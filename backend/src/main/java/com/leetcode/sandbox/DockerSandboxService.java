package com.leetcode.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * DockerSandboxService
 *
 * Executes user-submitted Java code in an isolated Docker container with:
 *  - CPU quota limits  (50% of one core by default)
 *  - Memory limits     (256 MB by default)
 *  - No network access (--network none)
 *  - No new privileges (--security-opt no-new-privileges)
 *  - Read-only filesystem with a temp volume
 *  - Wall-clock timeout enforced by the JVM process watchdog
 */
@Service
@Slf4j
public class DockerSandboxService {

    @Value("${sandbox.docker.image:leetcode-sandbox:latest}")
    private String sandboxImage;

    @Value("${sandbox.timeout.seconds:5}")
    private int timeoutSeconds;

    @Value("${sandbox.memory.limit:256m}")
    private String memoryLimit;

    @Value("${sandbox.cpu.quota:50000}")
    private String cpuQuota;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Execute user code against a single test case.
     *
     * @param code          The user's Java solution (wraps inside a harness)
     * @param input         JSON-encoded test-case input
     * @param problemSlug   Used to select the correct harness template
     * @return              ExecutionResult with stdout, stderr, timing, and status
     */
    public ExecutionResult execute(String code, String input, String problemSlug) {
        Path tempDir = null;
        String containerId = null;
        long startTime = System.currentTimeMillis();

        try {
            // 1. Create a temp directory for source files
            tempDir = Files.createTempDirectory("sandbox_");

            // 2. Write the complete Java source file (harness + user code)
            String fullSource = buildHarnessSource(code, input, problemSlug);
            Path sourceFile = tempDir.resolve("Solution.java");
            Files.writeString(sourceFile, fullSource, StandardCharsets.UTF_8);

            // 3. Write the input file
            Path inputFile = tempDir.resolve("input.json");
            Files.writeString(inputFile, input, StandardCharsets.UTF_8);

            // 4. Build Docker run command
            List<String> dockerCmd = buildDockerCommand(tempDir);

            // 5. Execute in Docker with timeout
            ProcessResult processResult = runProcess(dockerCmd, timeoutSeconds + 2);

            long elapsed = System.currentTimeMillis() - startTime;

            if (processResult.timedOut()) {
                return ExecutionResult.timeLimitExceeded(elapsed);
            }

            if (processResult.exitCode() == 137) {
                // OOM killed by cgroup
                return ExecutionResult.memoryLimitExceeded(elapsed);
            }

            String stdout = processResult.stdout().trim();
            String stderr  = processResult.stderr().trim();

            // Detect compile errors (javac output goes to stderr)
            if (processResult.exitCode() != 0 && containsCompileError(stderr)) {
                return ExecutionResult.compileError(sanitizeError(stderr), elapsed);
            }

            // Detect runtime exceptions
            if (processResult.exitCode() != 0 && !stderr.isEmpty()) {
                return ExecutionResult.runtimeError(sanitizeError(stderr), elapsed);
            }

            return ExecutionResult.success(stdout, elapsed);

        } catch (Exception e) {
            log.error("Sandbox execution failed", e);
            return ExecutionResult.runtimeError("Execution failed: " + e.getMessage(), 0);
        } finally {
            // Always clean up temp files and container
            if (tempDir != null) {
                deleteDirectory(tempDir);
            }
        }
    }

    /**
     * Run code against multiple test cases and return aggregated results.
     */
    public List<TestCaseResult> runTestCases(String code, List<TestCaseInput> testCases, String problemSlug) {
        List<TestCaseResult> results = new ArrayList<>();

        for (TestCaseInput tc : testCases) {
            ExecutionResult result = execute(code, tc.input(), problemSlug);

            String actualOutput = result.stdout().trim();
            String expectedOutput = tc.expected().trim();
            boolean passed = normalizeOutput(actualOutput).equals(normalizeOutput(expectedOutput));

            results.add(new TestCaseResult(
                tc.index(),
                tc.input(),
                expectedOutput,
                actualOutput,
                passed,
                result.status(),
                result.runtimeMs(),
                result.errorMessage(),
                tc.hidden()
            ));
        }

        return results;
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private List<String> buildDockerCommand(Path workDir) {
        return Arrays.asList(
            "docker", "run",
            "--rm",                                          // auto-remove container
            "--network", "none",                             // no network access
            "--memory", memoryLimit,                         // memory limit
            "--memory-swap", memoryLimit,                    // disable swap
            "--cpu-quota", cpuQuota,                         // CPU throttle
            "--cpu-period", "100000",
            "--security-opt", "no-new-privileges",           // prevent privilege escalation
            "--cap-drop", "ALL",                             // drop all Linux capabilities
            "--read-only",                                   // read-only rootfs
            "--tmpfs", "/tmp:size=64m,noexec",               // writable tmp with noexec
            "-v", workDir.toAbsolutePath() + ":/code:ro",   // mount source read-only
            "-w", "/code",
            sandboxImage,
            "sh", "-c",
            "javac Solution.java 2>&1 && java -Xmx200m -cp . SolutionRunner < input.json 2>&1"
        );
    }

    private ProcessResult runProcess(List<String> command, int timeoutSecs) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // Read stdout and stderr concurrently to avoid blocking
        Future<String> stdoutFuture = Executors.newSingleThreadExecutor()
            .submit(() -> readStream(process.getInputStream()));
        Future<String> stderrFuture = Executors.newSingleThreadExecutor()
            .submit(() -> readStream(process.getErrorStream()));

        boolean finished = process.waitFor(timeoutSecs, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(-1, "", "", true);
        }

        try {
            String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            String stderr  = stderrFuture.get(1, TimeUnit.SECONDS);
            return new ProcessResult(process.exitValue(), stdout, stderr, false);
        } catch (ExecutionException | TimeoutException e) {
            return new ProcessResult(process.exitValue(), "", "", false);
        }
    }

    private String readStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Builds the full harness source that wraps the user solution.
     * The harness reads JSON input, calls the solution method, and prints the result.
     * For a real system this would be per-problem; here we use a general-purpose harness.
     */
    private String buildHarnessSource(String userCode, String input, String problemSlug) {
        return """
import java.util.*;
import java.io.*;

// ---- USER SOLUTION ----
%s
// ---- END SOLUTION ----

class SolutionRunner {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\\n");
        String jsonInput = sb.toString().trim();

        // Parse and dispatch based on problem slug
        Solution solution = new Solution();
        runSolution(solution, jsonInput);
    }

    @SuppressWarnings("unchecked")
    static void runSolution(Solution solution, String jsonInput) throws Exception {
        // Generic dispatcher: parse the JSON manually for each problem type.
        // The harness is simplified here; a production system would generate
        // per-problem harnesses based on method signatures stored in the DB.
        try {
            // Try to detect what kind of problem this is from the JSON keys
            if (jsonInput.contains("\\"nums\\"") && jsonInput.contains("\\"target\\"")) {
                // Two Sum / similar
                int[] nums = parseIntArray(jsonInput, "nums");
                int target = parseInt(jsonInput, "target");
                try {
                    int[] result = solution.twoSum(nums, target);
                    System.out.println(Arrays.toString(result).replace(", ", ", "));
                } catch (NoSuchMethodError | AbstractMethodError e) {
                    // Method doesn't exist, try other signatures
                    System.out.println(solution.search(nums, target));
                }
            } else if (jsonInput.contains("\\"nums\\"")) {
                int[] nums = parseIntArray(jsonInput, "nums");
                try {
                    System.out.println(solution.maxSubArray(nums));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            } else if (jsonInput.contains("\\"s\\"") && !jsonInput.contains("\\"word\\"")) {
                String s = parseString(jsonInput, "s");
                try {
                    System.out.println(solution.isValid(s));
                } catch (Exception e) {
                    try {
                        System.out.println(solution.lengthOfLongestSubstring(s));
                    } catch (Exception e2) {
                        System.out.println(e2.getMessage());
                    }
                }
            } else if (jsonInput.contains("\\"n\\"")) {
                int n = parseInt(jsonInput, "n");
                System.out.println(solution.climbStairs(n));
            } else {
                System.out.println("UNSUPPORTED_PROBLEM_TYPE");
            }
        } catch (Exception e) {
            System.err.println("Runtime error: " + e.getClass().getName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    static int[] parseIntArray(String json, String key) {
        int start = json.indexOf("\\"" + key + "\\"");
        start = json.indexOf("[", start);
        int end = json.indexOf("]", start);
        String arr = json.substring(start + 1, end).trim();
        if (arr.isEmpty()) return new int[0];
        String[] parts = arr.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Integer.parseInt(parts[i].trim());
        return result;
    }

    static int parseInt(String json, String key) {
        int idx = json.indexOf("\\"" + key + "\\"");
        int colon = json.indexOf(":", idx);
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\\t')) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return Integer.parseInt(json.substring(start, end));
    }

    static String parseString(String json, String key) {
        int idx = json.indexOf("\\"" + key + "\\"");
        int colon = json.indexOf(":", idx);
        int start = json.indexOf("\\"", colon) + 1;
        int end = json.indexOf("\\"", start);
        return json.substring(start, end);
    }
}
""".formatted(userCode);
    }

    private String normalizeOutput(String output) {
        return output.trim()
                     .replaceAll("\\s+", " ")
                     .replaceAll(", ", ",")
                     .replaceAll("\\[\\s+", "[")
                     .replaceAll("\\s+]", "]");
    }

    private boolean containsCompileError(String stderr) {
        return stderr.contains("error:") || stderr.contains("cannot find symbol") ||
               stderr.contains("illegal") || stderr.contains("unexpected token");
    }

    private String sanitizeError(String error) {
        // Remove absolute paths for security
        return error.replaceAll("/[^\\s:]+/", "")
                    .replaceAll("\\bat /code\\b", "")
                    .trim();
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                 .sorted(Comparator.reverseOrder())
                 .forEach(path -> {
                     try { Files.delete(path); }
                     catch (IOException e) { log.warn("Could not delete temp file: {}", path); }
                 });
        } catch (IOException e) {
            log.warn("Could not clean up temp directory: {}", dir);
        }
    }

    // ------------------------------------------------------------------ //
    //  Inner record types                                                  //
    // ------------------------------------------------------------------ //

    public record ExecutionResult(
        String status,
        String stdout,
        String stderr,
        String errorMessage,
        long runtimeMs
    ) {
        public static ExecutionResult success(String stdout, long runtimeMs) {
            return new ExecutionResult("SUCCESS", stdout, "", null, runtimeMs);
        }
        public static ExecutionResult timeLimitExceeded(long runtimeMs) {
            return new ExecutionResult("TIME_LIMIT_EXCEEDED", "", "", "Time Limit Exceeded", runtimeMs);
        }
        public static ExecutionResult memoryLimitExceeded(long runtimeMs) {
            return new ExecutionResult("MEMORY_LIMIT_EXCEEDED", "", "", "Memory Limit Exceeded", runtimeMs);
        }
        public static ExecutionResult compileError(String error, long runtimeMs) {
            return new ExecutionResult("COMPILE_ERROR", "", error, error, runtimeMs);
        }
        public static ExecutionResult runtimeError(String error, long runtimeMs) {
            return new ExecutionResult("RUNTIME_ERROR", "", error, error, runtimeMs);
        }
    }

    public record TestCaseResult(
        int index,
        String input,
        String expected,
        String actual,
        boolean passed,
        String status,
        long runtimeMs,
        String errorMessage,
        boolean hidden
    ) {}

    public record TestCaseInput(int index, String input, String expected, boolean hidden) {}

    private record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {}
}
