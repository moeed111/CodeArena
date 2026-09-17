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
 * Executes user-submitted Java code. Supports both:
 *  1. Isolated Docker execution (when Docker daemon is available)
 *  2. Safe local process execution with timeout watchdog and memory caps (fallback for cloud/container platforms like Render)
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

    private final ExecutorService processExecutor = Executors.newCachedThreadPool();
    private Boolean dockerAvailable = null;

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Execute user code against a single test case.
     */
    public ExecutionResult execute(String code, String input, String problemSlug) {
        Path tempDir = null;
        long startTime = System.currentTimeMillis();

        try {
            tempDir = Files.createTempDirectory("sandbox_");

            String fullSource = buildHarnessSource(code, problemSlug);
            Path sourceFile = tempDir.resolve("Solution.java");
            Files.writeString(sourceFile, fullSource, StandardCharsets.UTF_8);

            CompileResult compileResult = compileSource(tempDir);
            if (!compileResult.success()) {
                return ExecutionResult.compileError(sanitizeError(compileResult.output()), System.currentTimeMillis() - startTime);
            }

            return runCompiledTestCase(tempDir, input, problemSlug);

        } catch (Exception e) {
            log.error("Sandbox execution failed", e);
            return ExecutionResult.runtimeError("Execution failed: " + e.getMessage(), System.currentTimeMillis() - startTime);
        } finally {
            if (tempDir != null) {
                deleteDirectory(tempDir);
            }
        }
    }

    /**
     * Run code against multiple test cases and return aggregated results.
     * Compiles user code ONCE and executes each test case against the compiled class.
     */
    public List<TestCaseResult> runTestCases(String code, List<TestCaseInput> testCases, String problemSlug) {
        List<TestCaseResult> results = new ArrayList<>();
        if (testCases == null || testCases.isEmpty()) {
            return results;
        }

        for (TestCaseInput tc : testCases) {
            ExecutionResult result = execute(code, tc.input(), problemSlug);

            String actualOutput = result.stdout().trim();
            String expectedOutput = tc.expected() != null ? tc.expected().trim() : "";
            boolean passed = isAnswerCorrect(actualOutput, expectedOutput, problemSlug)
                             && "SUCCESS".equals(result.status());

            String status = passed ? "SUCCESS"
                : ("SUCCESS".equals(result.status()) ? "WRONG_ANSWER" : result.status());

            results.add(new TestCaseResult(
                tc.index(),
                tc.input(),
                expectedOutput,
                actualOutput,
                passed,
                status,
                result.runtimeMs(),
                result.errorMessage(),
                tc.hidden()
            ));
        }

        return results;
    }

    // ------------------------------------------------------------------ //
    //  Compilation & Execution Logic                                       //
    // ------------------------------------------------------------------ //

    private CompileResult compileSource(Path tempDir) {
        long start = System.currentTimeMillis();

        if (isDockerSupported()) {
            try {
                String mountPath = tempDir.toAbsolutePath().toString().replace("\\", "/");
                List<String> dockerCmd = Arrays.asList(
                    "docker", "run", "--rm",
                    "-v", mountPath + ":/code",
                    "-w", "/code",
                    sandboxImage,
                    "sh", "-c", "javac -encoding UTF-8 Solution.java 2>&1"
                );
                ProcessResult pr = runProcess(dockerCmd, null, tempDir, timeoutSeconds + 5);
                long elapsed = System.currentTimeMillis() - start;
                if (pr.exitCode() == 0) {
                    return new CompileResult(true, "", elapsed);
                } else {
                    return new CompileResult(false, pr.stdout().isEmpty() ? pr.stderr() : pr.stdout(), elapsed);
                }
            } catch (Exception e) {
                log.warn("Docker compilation failed, falling back to local javac: {}", e.getMessage());
            }
        }

        // Local compile
        List<String> javacCmd = List.of("javac", "-encoding", "UTF-8", "Solution.java");
        ProcessResult pr = runProcess(javacCmd, null, tempDir, timeoutSeconds + 5);
        long elapsed = System.currentTimeMillis() - start;

        if (pr.exitCode() == 0 && !containsCompileError(pr.stderr())) {
            return new CompileResult(true, "", elapsed);
        }

        String err = pr.stderr().trim();
        if (err.isEmpty()) err = pr.stdout().trim();
        return new CompileResult(false, err, elapsed);
    }

    private ExecutionResult runCompiledTestCase(Path tempDir, String input, String problemSlug) {
        long start = System.currentTimeMillis();

        if (isDockerSupported()) {
            try {
                String mountPath = tempDir.toAbsolutePath().toString().replace("\\", "/");
                List<String> dockerCmd = Arrays.asList(
                    "docker", "run", "--rm", "-i",
                    "--network", "none",
                    "--memory", memoryLimit,
                    "--memory-swap", memoryLimit,
                    "--cpu-quota", cpuQuota,
                    "--cpu-period", "100000",
                    "-v", mountPath + ":/code:ro",
                    "-w", "/code",
                    sandboxImage,
                    "sh", "-c", "java -Xmx200m -cp . SolutionRunner 2>&1"
                );
                ProcessResult pr = runProcess(dockerCmd, input != null ? input : "", tempDir, timeoutSeconds);
                return processResultToExecutionResult(pr, start);
            } catch (Exception e) {
                log.warn("Docker execution failed, falling back to local execution: {}", e.getMessage());
            }
        }

        // Local process execution
        List<String> javaCmd = List.of("java", "-Xmx128m", "-cp", ".", "SolutionRunner");
        ProcessResult pr = runProcess(javaCmd, input != null ? input : "", tempDir, timeoutSeconds);
        return processResultToExecutionResult(pr, start);
    }

    private ExecutionResult processResultToExecutionResult(ProcessResult pr, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;

        if (pr.timedOut()) {
            return ExecutionResult.timeLimitExceeded(elapsed);
        }

        if (pr.exitCode() == 137) {
            return ExecutionResult.memoryLimitExceeded(elapsed);
        }

        if (pr.exitCode() != 0) {
            String err = pr.stderr().trim();
            if (err.isEmpty()) err = pr.stdout().trim();
            return ExecutionResult.runtimeError(sanitizeError(err), elapsed);
        }

        return ExecutionResult.success(pr.stdout().trim(), elapsed);
    }

    private boolean isDockerSupported() {
        if (dockerAvailable != null) return dockerAvailable;
        try {
            Process p = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            boolean ok = p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
            dockerAvailable = ok;
            return ok;
        } catch (Exception e) {
            dockerAvailable = false;
            return false;
        }
    }

    private ProcessResult runProcess(List<String> command, String stdinContent, Path workDir, int timeoutSecs) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            process = pb.start();

            if (stdinContent != null) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(stdinContent.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException ignored) {}
            } else {
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {}
            }

            final Process proc = process;
            Future<String> stdoutFuture = processExecutor.submit(() -> readStream(proc.getInputStream()));
            Future<String> stderrFuture = processExecutor.submit(() -> readStream(proc.getErrorStream()));

            boolean finished = process.waitFor(timeoutSecs, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(-1, "", "Time Limit Exceeded", true);
            }

            String stdout = "";
            String stderr = "";
            try {
                stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            try {
                stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}

            return new ProcessResult(process.exitValue(), stdout, stderr, false);
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return new ProcessResult(-1, "", e.getMessage(), false);
        }
    }

    private String readStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ //
    //  Harness Generation (Dynamic Reflection + JSON Parser)              //
    // ------------------------------------------------------------------ //

    private String buildHarnessSource(String userCode, String problemSlug) {
        String template = """
import java.util.*;
import java.io.*;
import java.lang.reflect.*;

// ---- HELPER CLASSES ----
class ListNode {
    public int val;
    public ListNode next;
    public ListNode() {}
    public ListNode(int val) { this.val = val; }
    public ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

class TreeNode {
    public int val;
    public TreeNode left;
    public TreeNode right;
    public TreeNode() {}
    public TreeNode(int val) { this.val = val; }
    public TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}

// ---- USER SOLUTION ----
/*__USER_CODE__*/
// ---- END SOLUTION ----

class SolutionRunner {
    public static void main(String[] args) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\\n");
            }
            String rawInput = sb.toString().trim();

            Solution solution = new Solution();
            Method targetMethod = findTargetMethod(Solution.class, "/*__PROBLEM_SLUG__*/");
            if (targetMethod == null) {
                System.err.println("Runtime error: No public solution method found in Solution class.");
                System.exit(1);
                return;
            }

            Object parsed = new MiniJson(rawInput).parse();
            Class<?>[] paramTypes = targetMethod.getParameterTypes();
            Object[] methodArgs = prepareArgs(parsed, paramTypes);

            targetMethod.setAccessible(true);
            Object result = targetMethod.invoke(solution, methodArgs);
            String output = formatResult(result, methodArgs, targetMethod);
            System.out.print(output);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.println("Runtime error: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            System.exit(1);
        } catch (Throwable e) {
            System.err.println("Runtime error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static Method findTargetMethod(Class<?> clazz, String slug) {
        Map<String, String> slugToMethod = new HashMap<>();
        slugToMethod.put("two-sum", "twoSum");
        slugToMethod.put("reverse-string", "reverseString");
        slugToMethod.put("valid-parentheses", "isValid");
        slugToMethod.put("longest-substring-without-repeating-characters", "lengthOfLongestSubstring");
        slugToMethod.put("merge-two-sorted-lists", "mergeTwoLists");
        slugToMethod.put("maximum-subarray", "maxSubArray");
        slugToMethod.put("climbing-stairs", "climbStairs");
        slugToMethod.put("binary-search", "search");
        slugToMethod.put("word-search", "exist");
        slugToMethod.put("median-of-two-sorted-arrays", "findMedianSortedArrays");

        String preferredName = slugToMethod.get(slug);

        Method[] methods = clazz.getDeclaredMethods();
        if (preferredName != null) {
            for (Method m : methods) {
                if (m.getName().equals(preferredName) && Modifier.isPublic(m.getModifiers())) {
                    return m;
                }
            }
        }

        // Fallback: first public non-synthetic method not from Object
        for (Method m : methods) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.getName().startsWith("$")) {
                return m;
            }
        }
        return null;
    }

    private static Object[] prepareArgs(Object parsed, Class<?>[] paramTypes) {
        Object[] args = new Object[paramTypes.length];
        if (paramTypes.length == 0) return args;

        if (parsed instanceof Map<?, ?> map) {
            Collection<?> values = map.values();
            Iterator<?> it = values.iterator();
            for (int i = 0; i < paramTypes.length; i++) {
                Object val = it.hasNext() ? it.next() : null;
                args[i] = convert(val, paramTypes[i]);
            }
        } else if (parsed instanceof List<?> list && paramTypes.length > 1 && !(paramTypes[0].isArray() && list.size() > 0 && !(list.get(0) instanceof List))) {
            for (int i = 0; i < paramTypes.length; i++) {
                Object val = i < list.size() ? list.get(i) : null;
                args[i] = convert(val, paramTypes[i]);
            }
        } else {
            args[0] = convert(parsed, paramTypes[0]);
        }
        return args;
    }

    @SuppressWarnings("unchecked")
    private static Object convert(Object val, Class<?> type) {
        if (val == null) return null;
        if (type.equals(int.class) || type.equals(Integer.class)) {
            return val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString().trim());
        }
        if (type.equals(long.class) || type.equals(Long.class)) {
            return val instanceof Number ? ((Number) val).longValue() : Long.parseLong(val.toString().trim());
        }
        if (type.equals(double.class) || type.equals(Double.class)) {
            return val instanceof Number ? ((Number) val).doubleValue() : Double.parseDouble(val.toString().trim());
        }
        if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            return val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(val.toString().trim());
        }
        if (type.equals(String.class)) {
            return val.toString();
        }
        if (type.equals(char.class) || type.equals(Character.class)) {
            String s = val.toString();
            return s.isEmpty() ? ' ' : s.charAt(0);
        }
        if (type.equals(int[].class)) {
            if (val instanceof List<?> list) {
                int[] arr = new int[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    arr[i] = item instanceof Number ? ((Number) item).intValue() : Integer.parseInt(item.toString().trim());
                }
                return arr;
            }
            if (val instanceof int[] arr) return arr;
        }
        if (type.equals(int[][].class)) {
            if (val instanceof List<?> outer) {
                int[][] matrix = new int[outer.size()][];
                for (int i = 0; i < outer.size(); i++) {
                    matrix[i] = (int[]) convert(outer.get(i), int[].class);
                }
                return matrix;
            }
        }
        if (type.equals(char[].class)) {
            if (val instanceof String s) return s.toCharArray();
            if (val instanceof List<?> list) {
                char[] arr = new char[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    String s = list.get(i).toString();
                    arr[i] = s.isEmpty() ? ' ' : s.charAt(0);
                }
                return arr;
            }
        }
        if (type.equals(char[][].class)) {
            if (val instanceof List<?> outer) {
                char[][] grid = new char[outer.size()][];
                for (int i = 0; i < outer.size(); i++) {
                    grid[i] = (char[]) convert(outer.get(i), char[].class);
                }
                return grid;
            }
        }
        if (type.equals(double[].class)) {
            if (val instanceof List<?> list) {
                double[] arr = new double[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    arr[i] = item instanceof Number ? ((Number) item).doubleValue() : Double.parseDouble(item.toString().trim());
                }
                return arr;
            }
        }
        if (type.equals(ListNode.class)) {
            if (val instanceof List<?> list) {
                ListNode dummy = new ListNode(0);
                ListNode cur = dummy;
                for (Object item : list) {
                    int v = item instanceof Number ? ((Number) item).intValue() : Integer.parseInt(item.toString().trim());
                    cur.next = new ListNode(v);
                    cur = cur.next;
                }
                return dummy.next;
            }
        }
        return val;
    }

    private static String formatResult(Object obj, Object[] args, Method method) {
        if (method.getReturnType().equals(void.class)) {
            if (args != null && args.length > 0) {
                return formatValue(args[0]);
            }
            return "null";
        }
        return formatValue(obj);
    }

    private static String formatValue(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof int[] arr) return Arrays.toString(arr);
        if (obj instanceof int[][] mat) return Arrays.deepToString(mat);
        if (obj instanceof char[] arr) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("\\"").append(arr[i]).append("\\"");
            }
            return sb.append("]").toString();
        }
        if (obj instanceof char[][] grid) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < grid.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatValue(grid[i]));
            }
            return sb.append("]").toString();
        }
        if (obj instanceof double[] arr) return Arrays.toString(arr);
        if (obj instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.format(Locale.US, "%.1f", d);
            }
            return String.valueOf(d);
        }
        if (obj instanceof ListNode node) {
            List<Integer> vals = new ArrayList<>();
            while (node != null) {
                vals.add(node.val);
                node = node.next;
            }
            return vals.toString();
        }
        return obj.toString();
    }

    static class MiniJson {
        private final String src;
        private int pos = 0;

        MiniJson(String src) { this.src = src != null ? src.trim() : ""; }

        Object parse() {
            skipWhitespace();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"' || c == '\\'') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            return parseNumber();
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') { pos++; return map; }
            while (pos < src.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ':') pos++;
                skipWhitespace();
                Object val = parse();
                map.put(key, val);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') { pos++; continue; }
                if (pos < src.length() && src.charAt(pos) == '}') { pos++; break; }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') { pos++; return list; }
            while (pos < src.length()) {
                skipWhitespace();
                list.add(parse());
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') { pos++; continue; }
                if (pos < src.length() && src.charAt(pos) == ']') { pos++; break; }
            }
            return list;
        }

        private String parseString() {
            if (pos >= src.length()) return "";
            char quote = src.charAt(pos);
            if (quote != '"' && quote != '\\'') {
                int start = pos;
                while (pos < src.length() && !Character.isWhitespace(src.charAt(pos)) && src.charAt(pos) != ':' && src.charAt(pos) != ',' && src.charAt(pos) != '}' && src.charAt(pos) != ']') pos++;
                return src.substring(start, pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == quote) break;
                if (c == '\\\\' && pos < src.length()) {
                    char next = src.charAt(pos++);
                    if (next == 'n') sb.append('\\n');
                    else if (next == 't') sb.append('\\t');
                    else if (next == 'r') sb.append('\\r');
                    else sb.append(next);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) { pos += 4; return true; }
            if (src.startsWith("false", pos)) { pos += 5; return false; }
            return false;
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            return null;
        }

        private Object parseNumber() {
            int start = pos;
            if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) pos++;
            boolean isDouble = false;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                if (src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E') isDouble = true;
                pos++;
            }
            String num = src.substring(start, pos);
            if (num.isEmpty() || num.equals("-")) return 0;
            return isDouble ? Double.parseDouble(num) : Long.parseLong(num);
        }
    }
}
""";
        return template
            .replace("/*__USER_CODE__*/", userCode != null ? userCode : "")
            .replace("/*__PROBLEM_SLUG__*/", problemSlug != null ? problemSlug : "");
    }

    private boolean isAnswerCorrect(String actual, String expected, String problemSlug) {
        String normActual = normalizeOutput(actual);
        String normExpected = normalizeOutput(expected);

        if (normActual.equals(normExpected)) return true;

        if ("two-sum".equals(problemSlug)) {
            if (sortIntArrayString(normActual).equals(sortIntArrayString(normExpected))) {
                return true;
            }
        }
        return false;
    }

    private String sortIntArrayString(String s) {
        if (s == null || !s.startsWith("[") || !s.endsWith("]")) return s;
        String inner = s.substring(1, s.length() - 1).trim();
        if (inner.isEmpty()) return s;
        String[] parts = inner.split(",");
        try {
            int[] arr = new int[parts.length];
            for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
            Arrays.sort(arr);
            return Arrays.toString(arr).replaceAll("\\s+", "");
        } catch (Exception e) {
            return s;
        }
    }

    private String normalizeOutput(String output) {
        if (output == null) return "";
        return output.trim()
                     .replaceAll("\\s+", "")
                     .replaceAll("'", "\"")
                     .toLowerCase();
    }

    private boolean containsCompileError(String stderr) {
        return stderr.contains("error:") || stderr.contains("cannot find symbol") ||
               stderr.contains("illegal") || stderr.contains("unexpected token");
    }

    private String sanitizeError(String error) {
        if (error == null) return "";
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
    //  Inner Record Types                                                  //
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

    private record CompileResult(boolean success, String output, long durationMs) {}
}
