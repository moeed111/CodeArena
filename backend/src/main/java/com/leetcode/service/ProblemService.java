package com.leetcode.service;

import com.leetcode.dto.*;
import com.leetcode.model.*;
import com.leetcode.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProblemService {

    private final ProblemRepository   problemRepository;
    private final TagRepository        tagRepository;
    private final TestCaseRepository   testCaseRepository;
    private final SubmissionRepository submissionRepository;

    public Page<ProblemSummaryDto> getProblems(
            Problem.Difficulty difficulty,
            String tagName,
            String search,
            int page,
            int size,
            Long currentUserId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<Problem> problems = problemRepository.findWithFilters(difficulty, tagName, search, pageable);

        return problems.map(p -> toSummaryDto(p, currentUserId));
    }

    public ProblemDetailDto getProblemBySlug(String slug, Long currentUserId) {
        Problem problem = problemRepository.findBySlug(slug)
            .orElseThrow(() -> new RuntimeException("Problem not found: " + slug));
        return toDetailDto(problem, currentUserId);
    }

    public ProblemDetailDto getProblemById(Long id, Long currentUserId) {
        Problem problem = problemRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Problem not found: " + id));
        return toDetailDto(problem, currentUserId);
    }

    @Transactional
    public ProblemDetailDto createProblem(CreateProblemRequest request, User creator) {
        String slug = generateSlug(request.title());

        Problem problem = Problem.builder()
            .title(request.title())
            .slug(slug)
            .description(request.description())
            .difficulty(request.difficulty())
            .constraints(request.constraints())
            .starterCode(request.starterCode())
            .solution(request.solution())
            .createdBy(creator)
            .active(true)
            .build();

        // Resolve/create tags
        if (request.tags() != null) {
            Set<Tag> tags = request.tags().stream()
                .map(name -> tagRepository.findByName(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())))
                .collect(Collectors.toSet());
            problem.setTags(tags);
        }

        // Add examples
        if (request.examples() != null) {
            List<ProblemExample> examples = new ArrayList<>();
            for (int i = 0; i < request.examples().size(); i++) {
                var e = request.examples().get(i);
                ProblemExample ex = new ProblemExample();
                ex.setProblem(problem);
                ex.setInput(e.input());
                ex.setOutput(e.output());
                ex.setExplanation(e.explanation());
                ex.setOrderIndex(e.orderIndex() > 0 ? e.orderIndex() : i);
                examples.add(ex);
            }
            problem.setExamples(examples);
        }

        // Add test cases
        if (request.testCases() != null) {
            List<TestCase> testCases = new ArrayList<>();
            for (int i = 0; i < request.testCases().size(); i++) {
                var tc = request.testCases().get(i);
                TestCase testCase = new TestCase();
                testCase.setProblem(problem);
                testCase.setInput(tc.input());
                testCase.setExpected(tc.expected());
                testCase.setHidden(tc.hidden());
                testCase.setOrderIndex(tc.orderIndex() > 0 ? tc.orderIndex() : i);
                testCases.add(testCase);
            }
            problem.setTestCases(testCases);
        }

        Problem saved = problemRepository.save(problem);
        return toDetailDto(saved, null);
    }

    @Transactional
    public void updateAcceptanceRate(Long problemId, boolean accepted) {
        Problem problem = problemRepository.findById(problemId).orElseThrow();
        problem.setSubmissions(problem.getSubmissions() + 1);

        if (problem.getSubmissions() > 0) {
            // Recalculate from submission data for accuracy
            long totalSubs = problem.getSubmissions();
            BigDecimal currentAccepted = problem.getAcceptance()
                .multiply(BigDecimal.valueOf(totalSubs - 1))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            if (accepted) currentAccepted = currentAccepted.add(BigDecimal.ONE);

            BigDecimal newRate = currentAccepted
                .divide(BigDecimal.valueOf(totalSubs), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

            problem.setAcceptance(newRate);
        }

        problemRepository.save(problem);
    }

    public List<Tag> getAllTags() {
        return tagRepository.findAllByOrderByNameAsc();
    }

    public Map<String, Long> getProblemStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("EASY",   problemRepository.countByDifficulty(Problem.Difficulty.EASY));
        stats.put("MEDIUM", problemRepository.countByDifficulty(Problem.Difficulty.MEDIUM));
        stats.put("HARD",   problemRepository.countByDifficulty(Problem.Difficulty.HARD));
        stats.put("TOTAL",  stats.values().stream().mapToLong(Long::longValue).sum());
        return stats;
    }

    // ------------------------------------------------------------------ //
    //  Mapping helpers                                                     //
    // ------------------------------------------------------------------ //

    private ProblemSummaryDto toSummaryDto(Problem p, Long userId) {
        Set<String> tagNames = p.getTags().stream().map(Tag::getName).collect(Collectors.toSet());
        Boolean solved = userId != null
            ? submissionRepository.existsByUserIdAndProblemIdAndStatus(userId, p.getId(), Submission.Status.ACCEPTED)
            : null;

        return new ProblemSummaryDto(
            p.getId(), p.getTitle(), p.getSlug(),
            p.getDifficulty().name(), p.getAcceptance(), p.getSubmissions(),
            tagNames, solved
        );
    }

    private ProblemDetailDto toDetailDto(Problem p, Long userId) {
        Set<String> tagNames = p.getTags().stream().map(Tag::getName).collect(Collectors.toSet());

        List<ExampleDto> examples = p.getExamples().stream()
            .map(e -> new ExampleDto(e.getId(), e.getInput(), e.getOutput(), e.getExplanation(), e.getOrderIndex()))
            .toList();

        List<TestCaseDto> visibleCases = p.getTestCases().stream()
            .filter(tc -> !tc.isHidden())
            .map(tc -> new TestCaseDto(tc.getId(), tc.getInput(), tc.getExpected(), tc.getOrderIndex()))
            .toList();

        Boolean solved = userId != null
            ? submissionRepository.existsByUserIdAndProblemIdAndStatus(userId, p.getId(), Submission.Status.ACCEPTED)
            : null;

        return new ProblemDetailDto(
            p.getId(), p.getTitle(), p.getSlug(),
            p.getDescription(), p.getDifficulty().name(),
            p.getConstraints(), p.getStarterCode(),
            p.getAcceptance(), p.getSubmissions(),
            tagNames, examples, visibleCases, solved
        );
    }

    private String generateSlug(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "");
        String slug = normalized.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-");

        // Ensure uniqueness
        String baseSlug = slug;
        int counter = 1;
        while (problemRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }
}
