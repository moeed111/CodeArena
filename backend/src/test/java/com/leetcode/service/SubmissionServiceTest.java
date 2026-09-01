package com.leetcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcode.dto.SubmissionDtos;
import com.leetcode.dto.SubmissionDtos.*;
import com.leetcode.model.*;
import com.leetcode.repository.*;
import com.leetcode.sandbox.DockerSandboxService;
import com.leetcode.sandbox.DockerSandboxService.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private ProblemRepository    problemRepository;
    @Mock private UserRepository       userRepository;
    @Mock private TestCaseRepository   testCaseRepository;
    @Mock private DockerSandboxService dockerSandbox;
    @Mock private ProblemService       problemService;
    @Mock private UserService          userService;

    @InjectMocks private SubmissionService submissionService;

    private User    testUser;
    private Problem testProblem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(submissionService, "objectMapper", new ObjectMapper());

        testUser = User.builder()
            .id(1L).username("alice").email("alice@test.com")
            .password("hashed").role(User.Role.USER).build();

        testProblem = Problem.builder()
            .id(10L).title("Two Sum").slug("two-sum")
            .description("desc").difficulty(Problem.Difficulty.EASY)
            .acceptance(BigDecimal.ZERO).submissions(0).active(true).build();
    }

    @Test
    @DisplayName("submitCode returns ACCEPTED when all test cases pass")
    void submitCode_allPass_returnsAccepted() {
        SubmitCodeRequest request = new SubmitCodeRequest("class Solution{}", "java", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(problemRepository.findById(10L)).thenReturn(Optional.of(testProblem));
        when(submissionRepository.save(any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            if (s.getId() == null) s.setId(100L);
            return s;
        });

        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L)).thenReturn(List.of(
            buildTestCase(1L, "{\"nums\":[2,7],\"target\":9}", "[0, 1]", false),
            buildTestCase(2L, "{\"nums\":[3,2,4],\"target\":6}", "[1, 2]", true)
        ));

        when(dockerSandbox.runTestCases(anyString(), anyList(), anyString())).thenReturn(List.of(
            new TestCaseResult(0, "{}", "[0, 1]", "[0, 1]", true, "SUCCESS", 42L, null, false),
            new TestCaseResult(1, "{}", "[1, 2]", "[1, 2]", true, "SUCCESS", 38L, null, true)
        ));

        SubmissionResultDto result = submissionService.submitCode(request, 1L);

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.passedTests()).isEqualTo(2);
        assertThat(result.totalTests()).isEqualTo(2);
        verify(problemService).updateAcceptanceRate(10L, true);
        verify(userService).updateStreak(1L);
    }

    @Test
    @DisplayName("submitCode returns WRONG_ANSWER when output mismatches")
    void submitCode_wrongAnswer_returnsWrongAnswer() {
        SubmitCodeRequest request = new SubmitCodeRequest("class Solution{}", "java", 10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(problemRepository.findById(10L)).thenReturn(Optional.of(testProblem));
        when(submissionRepository.save(any())).thenAnswer(inv -> { Submission s = inv.getArgument(0); if(s.getId()==null)s.setId(100L); return s; });
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L)).thenReturn(List.of(
            buildTestCase(1L, "{}", "[0, 1]", false)
        ));
        when(dockerSandbox.runTestCases(anyString(), anyList(), anyString())).thenReturn(List.of(
            new TestCaseResult(0, "{}", "[0, 1]", "[1, 0]", false, "WRONG_ANSWER", 30L, null, false)
        ));

        SubmissionResultDto result = submissionService.submitCode(request, 1L);

        assertThat(result.status()).isEqualTo("WRONG_ANSWER");
        assertThat(result.passedTests()).isEqualTo(0);
        verify(userService, never()).updateStreak(anyLong());
    }

    @Test
    @DisplayName("submitCode returns COMPILE_ERROR on bad code")
    void submitCode_compileError() {
        SubmitCodeRequest request = new SubmitCodeRequest("not java", "java", 10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(problemRepository.findById(10L)).thenReturn(Optional.of(testProblem));
        when(submissionRepository.save(any())).thenAnswer(inv -> { Submission s = inv.getArgument(0); if(s.getId()==null)s.setId(100L); return s; });
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L)).thenReturn(List.of(
            buildTestCase(1L, "{}", "0", false)
        ));
        when(dockerSandbox.runTestCases(anyString(), anyList(), anyString())).thenReturn(List.of(
            new TestCaseResult(0, "{}", "0", "", false, "COMPILE_ERROR", 0L, "error: class expected", false)
        ));

        SubmissionResultDto result = submissionService.submitCode(request, 1L);
        assertThat(result.status()).isEqualTo("COMPILE_ERROR");
        assertThat(result.errorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("submitCode returns TLE when sandbox times out")
    void submitCode_timeLimitExceeded() {
        SubmitCodeRequest request = new SubmitCodeRequest("class Solution{}", "java", 10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(problemRepository.findById(10L)).thenReturn(Optional.of(testProblem));
        when(submissionRepository.save(any())).thenAnswer(inv -> { Submission s = inv.getArgument(0); if(s.getId()==null)s.setId(100L); return s; });
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L)).thenReturn(List.of(
            buildTestCase(1L, "{}", "[0,1]", false)
        ));
        when(dockerSandbox.runTestCases(anyString(), anyList(), anyString())).thenReturn(List.of(
            new TestCaseResult(0, "{}", "[0,1]", "", false, "TIME_LIMIT_EXCEEDED", 5001L, "TLE", false)
        ));

        SubmissionResultDto result = submissionService.submitCode(request, 1L);
        assertThat(result.status()).isEqualTo("TIME_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("submitCode returns error when no test cases configured")
    void submitCode_noTestCases_returnsError() {
        SubmitCodeRequest request = new SubmitCodeRequest("class Solution{}", "java", 10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(problemRepository.findById(10L)).thenReturn(Optional.of(testProblem));
        when(submissionRepository.save(any())).thenAnswer(inv -> { Submission s = inv.getArgument(0); if(s.getId()==null)s.setId(100L); return s; });
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L)).thenReturn(List.of());

        SubmissionResultDto result = submissionService.submitCode(request, 1L);
        assertThat(result.status()).isEqualTo("RUNTIME_ERROR");
        verify(dockerSandbox, never()).runTestCases(any(), any(), any());
    }

    @Test
    @DisplayName("runCode does not persist and uses only visible test cases")
    void runCode_doesNotPersist() {
        RunCodeRequest request = new RunCodeRequest("class Solution{}", "java", 10L, null);
        when(problemRepository.findById(10L)).thenReturn(Optional.of(testProblem));
        when(testCaseRepository.findByProblemIdAndHiddenFalseOrderByOrderIndexAsc(10L)).thenReturn(List.of(
            buildTestCase(1L, "{}", "[0,1]", false)
        ));
        when(dockerSandbox.runTestCases(anyString(), anyList(), anyString())).thenReturn(List.of(
            new TestCaseResult(0, "{}", "[0,1]", "[0,1]", true, "SUCCESS", 25L, null, false)
        ));

        RunCodeResultDto result = submissionService.runCode(request, 1L);
        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("getUserSubmissions returns paged results")
    void getUserSubmissions_returnsPaged() {
        Submission s = new Submission();
        s.setId(1L); s.setUser(testUser); s.setProblem(testProblem);
        s.setStatus(Submission.Status.ACCEPTED); s.setLanguage("java");
        s.setPassedTests(2); s.setTotalTests(2);

        when(submissionRepository.findByUserIdOrderBySubmittedAtDesc(eq(1L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(s)));

        Page<SubmissionSummaryDto> result = submissionService.getUserSubmissions(1L, 0, 20);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("ACCEPTED");
    }

    private TestCase buildTestCase(Long id, String input, String expected, boolean hidden) {
        TestCase tc = new TestCase();
        tc.setId(id); tc.setProblem(testProblem);
        tc.setInput(input); tc.setExpected(expected); tc.setHidden(hidden);
        return tc;
    }
}
