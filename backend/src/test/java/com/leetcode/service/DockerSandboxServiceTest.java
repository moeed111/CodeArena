package com.leetcode.service;

import com.leetcode.sandbox.DockerSandboxService;
import com.leetcode.sandbox.DockerSandboxService.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DockerSandboxServiceTest {

    // We test the sandbox without actually calling Docker.
    // The execute() method is tested via a spy with the subprocess mocked.
    private DockerSandboxService sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new DockerSandboxService();
        ReflectionTestUtils.setField(sandbox, "sandboxImage",  "leetcode-sandbox:latest");
        ReflectionTestUtils.setField(sandbox, "timeoutSeconds", 5);
        ReflectionTestUtils.setField(sandbox, "memoryLimit",   "256m");
        ReflectionTestUtils.setField(sandbox, "cpuQuota",      "50000");
    }

    @Test
    @DisplayName("ExecutionResult.success() sets correct fields")
    void executionResult_success() {
        ExecutionResult result = ExecutionResult.success("[0, 1]", 120L);
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.stdout()).isEqualTo("[0, 1]");
        assertThat(result.runtimeMs()).isEqualTo(120L);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("ExecutionResult.timeLimitExceeded() sets TLE status")
    void executionResult_tle() {
        ExecutionResult result = ExecutionResult.timeLimitExceeded(5001L);
        assertThat(result.status()).isEqualTo("TIME_LIMIT_EXCEEDED");
        assertThat(result.errorMessage()).isEqualTo("Time Limit Exceeded");
    }

    @Test
    @DisplayName("ExecutionResult.compileError() preserves error message")
    void executionResult_compileError() {
        ExecutionResult result = ExecutionResult.compileError("Solution.java:3: error: ';' expected", 0L);
        assertThat(result.status()).isEqualTo("COMPILE_ERROR");
        assertThat(result.errorMessage()).contains("error:");
    }

    @Test
    @DisplayName("ExecutionResult.memoryLimitExceeded() sets MLE status")
    void executionResult_mle() {
        ExecutionResult result = ExecutionResult.memoryLimitExceeded(3000L);
        assertThat(result.status()).isEqualTo("MEMORY_LIMIT_EXCEEDED");
        assertThat(result.errorMessage()).isEqualTo("Memory Limit Exceeded");
    }

    @Test
    @DisplayName("TestCaseResult reflects pass/fail correctly")
    void testCaseResult_passFail() {
        TestCaseResult passing = new TestCaseResult(0, "{}", "[0,1]", "[0,1]", true,  "SUCCESS",      40L, null,         false);
        TestCaseResult failing = new TestCaseResult(1, "{}", "[0,1]", "[1,0]", false, "WRONG_ANSWER", 35L, null,         false);
        TestCaseResult error   = new TestCaseResult(2, "{}", "x",     "",      false, "RUNTIME_ERROR",0L,  "NPE at L1",  true);

        assertThat(passing.passed()).isTrue();
        assertThat(failing.passed()).isFalse();
        assertThat(error.hidden()).isTrue();
        assertThat(error.errorMessage()).contains("NPE");
    }

    @Test
    @DisplayName("runTestCases returns one result per input")
    void runTestCases_returnsCorrectCount() {
        // Use a spy to avoid actually running Docker
        DockerSandboxService spySandbox = Mockito.spy(sandbox);

        List<TestCaseInput> inputs = List.of(
            new TestCaseInput(0, "{\"n\":2}", "2",  false),
            new TestCaseInput(1, "{\"n\":3}", "3",  false),
            new TestCaseInput(2, "{\"n\":10}", "89", true)
        );

        // Stub the execute() call so no Docker process is started
        Mockito.doReturn(ExecutionResult.success("2",  50L)).when(spySandbox).execute(anyString(), eq("{\"n\":2}"),  anyString());
        Mockito.doReturn(ExecutionResult.success("3",  48L)).when(spySandbox).execute(anyString(), eq("{\"n\":3}"),  anyString());
        Mockito.doReturn(ExecutionResult.success("89", 52L)).when(spySandbox).execute(anyString(), eq("{\"n\":10}"), anyString());

        List<TestCaseResult> results = spySandbox.runTestCases("class Solution{}", inputs, "climbing-stairs");

        assertThat(results).hasSize(3);
        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(1).passed()).isTrue();
        assertThat(results.get(2).passed()).isTrue();
    }

    // Mockito helper – needed inside the test class due to package visibility
    private String anyString() { return Mockito.anyString(); }
    private String eq(String s) { return Mockito.eq(s); }
}
