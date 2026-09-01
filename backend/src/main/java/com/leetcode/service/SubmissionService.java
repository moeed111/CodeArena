package com.leetcode.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.leetcode.dto.SubmissionDtos.*;
import com.leetcode.model.*;
import com.leetcode.repository.*;
import com.leetcode.sandbox.DockerSandboxService;
import com.leetcode.sandbox.DockerSandboxService.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository    problemRepository;
    private final UserRepository       userRepository;
    private final TestCaseRepository   testCaseRepository;
    private final DockerSandboxService dockerSandbox;
    private final ProblemService       problemService;
    private final UserService          userService;
    private final ObjectMapper         objectMapper;

    // ------------------------------------------------------------------ //
    //  Submit code (runs all test cases including hidden)                  //
    // ------------------------------------------------------------------ //

    @Transactional
    public SubmissionResultDto submitCode(SubmitCodeRequest request, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(request.problemId())
            .orElseThrow(() -> new RuntimeException("Problem not found"));

        // Create a pending submission record
        Submission submission = submissionRepository.save(
            Submission.builder()
                .user(user)
                .problem(problem)
                .code(request.code())
                .language(request.language())
                .status(Submission.Status.PENDING)
                .build()
        );

        // Fetch ALL test cases (visible + hidden)
        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId());

        if (testCases.isEmpty()) {
            return failSubmission(submission, "No test cases configured for this problem.");
        }

        // Build sandbox input list
        List<TestCaseInput> inputs = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            inputs.add(new TestCaseInput(i, tc.getInput(), tc.getExpected(), tc.isHidden()));
        }

        // Execute in sandbox
        submission.setStatus(Submission.Status.RUNNING);
        submission = submissionRepository.save(submission);

        List<TestCaseResult> results;
        try {
            results = dockerSandbox.runTestCases(request.code(), inputs, problem.getSlug());
        } catch (Exception e) {
            log.error("Sandbox execution exception", e);
            return failSubmission(submission, "Internal execution error: " + e.getMessage());
        }

        // Analyze results
        int passed = (int) results.stream().filter(TestCaseResult::passed).count();
        int total  = results.size();
        long totalRuntimeMs = results.stream().mapToLong(TestCaseResult::runtimeMs).sum();

        // Determine overall status
        Submission.Status status = determineStatus(results, passed, total);

        // Serialize test results to JSON
        String testResultsJson = serializeTestResults(results);

        // Update submission record
        submission.setStatus(status);
        submission.setPassedTests(passed);
        submission.setTotalTests(total);
        submission.setRuntimeMs((int) totalRuntimeMs);
        submission.setMemoryKb(estimateMemory(request.code()));
        submission.setTestResults(testResultsJson);

        if (status != Submission.Status.ACCEPTED) {
            // Find first failing test case error
            for (TestCaseResult r : results) {
                if (!r.passed()) {
                    submission.setErrorMessage(
                        r.errorMessage() != null ? r.errorMessage()
                            : "Expected: " + r.expected() + "\nGot: " + r.actual()
                    );
                    break;
                }
            }
        }

        submission = submissionRepository.save(submission);

        // Side effects on acceptance
        if (status == Submission.Status.ACCEPTED) {
            problemService.updateAcceptanceRate(problem.getId(), true);
            userService.updateStreak(userId);
        } else {
            problemService.updateAcceptanceRate(problem.getId(), false);
        }

        // Map results (hide hidden test case I/O for non-accepted)
        List<TestResultDto> resultDtos = mapTestResults(results, status == Submission.Status.ACCEPTED);

        return new SubmissionResultDto(
            submission.getId(),
            status.name(),
            submission.getRuntimeMs(),
            submission.getMemoryKb(),
            submission.getErrorMessage(),
            passed,
            total,
            resultDtos,
            submission.getSubmittedAt()
        );
    }

    // ------------------------------------------------------------------ //
    //  Run code (only visible test cases, does NOT save to history)        //
    // ------------------------------------------------------------------ //

    public RunCodeResultDto runCode(RunCodeRequest request, Long userId) {
        Problem problem = problemRepository.findById(request.problemId())
            .orElseThrow(() -> new RuntimeException("Problem not found"));

        List<TestCase> testCases;
        if (request.customInput() != null && !request.customInput().isBlank()) {
            // Run against custom input only
            TestCase custom = new TestCase();
            custom.setInput(request.customInput());
            custom.setExpected(""); // no expected for custom
            custom.setHidden(false);
            testCases = List.of(custom);
        } else {
            // Run against visible test cases only
            testCases = testCaseRepository.findByProblemIdAndHiddenFalseOrderByOrderIndexAsc(problem.getId());
        }

        List<TestCaseInput> inputs = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            inputs.add(new TestCaseInput(i, tc.getInput(), tc.getExpected(), false));
        }

        List<TestCaseResult> results;
        try {
            results = dockerSandbox.runTestCases(request.code(), inputs, problem.getSlug());
        } catch (Exception e) {
            return new RunCodeResultDto("ERROR", List.of(), "", "", e.getMessage(), 0);
        }

        int passed = (int) results.stream().filter(TestCaseResult::passed).count();
        boolean allPassed = passed == results.size();
        long totalRuntimeMs = results.stream().mapToLong(TestCaseResult::runtimeMs).sum();

        // Detect overall status from first failure
        String status = results.stream()
            .filter(r -> !r.passed())
            .findFirst()
            .map(TestCaseResult::status)
            .orElse("SUCCESS");

        List<TestResultDto> resultDtos = results.stream()
            .map(r -> new TestResultDto(
                r.index(), r.input(), r.expected(), r.actual(),
                r.passed(), r.status(), r.runtimeMs(), r.errorMessage(), false
            ))
            .toList();

        return new RunCodeResultDto(status, resultDtos, "", "", null, totalRuntimeMs);
    }

    // ------------------------------------------------------------------ //
    //  Submission history                                                  //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public Page<SubmissionSummaryDto> getUserSubmissions(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return submissionRepository.findByUserIdOrderBySubmittedAtDesc(userId, pageable)
            .map(this::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionSummaryDto> getUserSubmissionsForProblem(Long userId, Long problemId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return submissionRepository.findByUserIdAndProblemIdOrderBySubmittedAtDesc(userId, problemId, pageable)
            .map(this::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public SubmissionResultDto getSubmissionDetail(Long submissionId, Long userId) {
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new RuntimeException("Submission not found"));

        if (!submission.getUser().getId().equals(userId)) {
            throw new SecurityException("Access denied");
        }

        List<TestResultDto> testResults = deserializeTestResults(submission.getTestResults());

        return new SubmissionResultDto(
            submission.getId(),
            submission.getStatus().name(),
            submission.getRuntimeMs(),
            submission.getMemoryKb(),
            submission.getErrorMessage(),
            submission.getPassedTests(),
            submission.getTotalTests(),
            testResults,
            submission.getSubmittedAt()
        );
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private Submission.Status determineStatus(List<TestCaseResult> results, int passed, int total) {
        if (passed == total) return Submission.Status.ACCEPTED;

        // Check for specific error types
        for (TestCaseResult r : results) {
            if (!r.passed()) {
                return switch (r.status()) {
                    case "TIME_LIMIT_EXCEEDED"   -> Submission.Status.TIME_LIMIT_EXCEEDED;
                    case "MEMORY_LIMIT_EXCEEDED" -> Submission.Status.MEMORY_LIMIT_EXCEEDED;
                    case "COMPILE_ERROR"          -> Submission.Status.COMPILE_ERROR;
                    case "RUNTIME_ERROR"          -> Submission.Status.RUNTIME_ERROR;
                    default                       -> Submission.Status.WRONG_ANSWER;
                };
            }
        }
        return Submission.Status.WRONG_ANSWER;
    }

    private List<TestResultDto> mapTestResults(List<TestCaseResult> results, boolean showHidden) {
        return results.stream()
            .map(r -> {
                boolean reveal = !r.hidden() || showHidden;
                return new TestResultDto(
                    r.index(),
                    reveal ? r.input()    : null,
                    reveal ? r.expected() : null,
                    reveal ? r.actual()   : null,
                    r.passed(),
                    r.status(),
                    r.runtimeMs(),
                    reveal ? r.errorMessage() : null,
                    r.hidden()
                );
            })
            .toList();
    }

    private SubmissionResultDto failSubmission(Submission submission, String message) {
        submission.setStatus(Submission.Status.RUNTIME_ERROR);
        submission.setErrorMessage(message);
        submissionRepository.save(submission);
        return new SubmissionResultDto(
            submission.getId(), "RUNTIME_ERROR", null, null, message, 0, 0, List.of(), null
        );
    }

    private String serializeTestResults(List<TestCaseResult> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<TestResultDto> deserializeTestResults(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, List.class);
            return raw.stream().map(m -> new TestResultDto(
                (int) m.getOrDefault("index", 0),
                (String) m.get("input"),
                (String) m.get("expected"),
                (String) m.get("actual"),
                (boolean) m.getOrDefault("passed", false),
                (String) m.getOrDefault("status", "UNKNOWN"),
                ((Number) m.getOrDefault("runtimeMs", 0)).longValue(),
                (String) m.get("errorMessage"),
                (boolean) m.getOrDefault("hidden", false)
            )).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private int estimateMemory(String code) {
        // Rough estimate based on code size; real impl would use docker stats
        return 32768 + (code.length() / 10);
    }

    private SubmissionSummaryDto toSummaryDto(Submission s) {
        return new SubmissionSummaryDto(
            s.getId(),
            s.getProblem().getId(),
            s.getProblem().getTitle(),
            s.getProblem().getSlug(),
            s.getStatus().name(),
            s.getLanguage(),
            s.getRuntimeMs(),
            s.getPassedTests(),
            s.getTotalTests(),
            s.getSubmittedAt()
        );
    }
}
