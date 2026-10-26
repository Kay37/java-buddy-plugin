package dev.javabuddy.drills

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.charset.StandardCharsets

/** One hidden test's outcome. [location] is "Square.java:5" for a crash inside the learner's code. */
data class CheckResult(
    val check: Check,
    val passed: Boolean,
    val expected: String = "",
    val actual: String = "",
    val crash: String? = null,
    val crashMessage: String? = null,
    val location: String? = null,
)

/** A compile error. [inLearnerFile] is false when it's in the hidden test code, which means the method doesn't match the drill. */
data class CompileProblem(val line: Int, val message: String, val inLearnerFile: Boolean)

sealed class DrillOutcome {
    /** [total] is how many checks the drill has; fewer [results] means the run stopped early. */
    data class Checked(val results: List<CheckResult>, val total: Int, val timedOut: Boolean = false) : DrillOutcome() {
        val passedCount get() = results.count { it.passed }
        val allPassed get() = !timedOut && results.size == total && results.all { it.passed }
    }
    data class DidNotCompile(val problems: List<CompileProblem>) : DrillOutcome()
    data class NoJdk(val reason: String) : DrillOutcome()
    data class Failed(val reason: String) : DrillOutcome()
}

/**
 * Checks a drill outside the IDE: writes the learner's code and a generated test class to a temp
 * folder, compiles both with the project's JDK, and runs the tests with a time limit. The tests
 * print one marker line per check, which is parsed back into [CheckResult]s.
 */
object DrillChecker {
    private const val CHECK_CLASS = "JavaBuddyCheck"
    private const val MARKER = "@@JB"
    private const val SEPARATOR = '\u001F'
    private const val COMPILE_TIMEOUT_MS = 60_000
    private const val RUN_TIMEOUT_MS = 5_000

    fun check(drill: Drill, code: String, jdkHome: String?): DrillOutcome {
        val javac = jdkHome?.let { tool(it, "javac") }
        val java = jdkHome?.let { tool(it, "java") }
        if (javac == null || java == null) {
            return DrillOutcome.NoJdk(if (jdkHome == null) "This project isn't set up with a Java version yet." else "The Java set up for this project ($jdkHome) can't build code: it has no javac.")
        }
        val dir = FileUtil.createTempDirectory("javabuddy-drill", null, true)
        try {
            val learnerFile = File(dir, drill.fileName).apply { writeText(code, StandardCharsets.UTF_8) }
            val checkFile = File(dir, "$CHECK_CLASS.java").apply { writeText(testSource(drill), StandardCharsets.UTF_8) }
            val out = File(dir, "out").apply { mkdirs() }

            val compile = run(GeneralCommandLine(javac.path, "-J-Duser.language=en", "-J-Duser.country=US", "-encoding", "UTF-8", "-Xlint:none", "-d", out.path, learnerFile.path, checkFile.path).withWorkingDirectory(dir.toPath()), COMPILE_TIMEOUT_MS)
            if (compile.isTimeout) return DrillOutcome.Failed("Compiling took more than a minute. Try again in a moment.")
            if (compile.exitCode != 0) return DrillOutcome.DidNotCompile(compileProblems(compile.stdout + "\n" + compile.stderr, drill.fileName))

            val result = run(
                GeneralCommandLine(java.path, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Xss2m", "-cp", out.path, CHECK_CLASS).withWorkingDirectory(dir.toPath()),
                RUN_TIMEOUT_MS,
            )
            val results = parse(drill, result.stdout)
            return DrillOutcome.Checked(results, drill.checks.size, timedOut = result.isTimeout)
        }
        finally {
            FileUtil.delete(dir)
        }
    }

    private fun tool(jdkHome: String, name: String): File? =
        File(jdkHome, "bin/" + if (SystemInfo.isWindows) "$name.exe" else name).takeIf { it.isFile }

    private fun run(command: GeneralCommandLine, timeoutMs: Int) =
        CapturingProcessHandler(command.withCharset(StandardCharsets.UTF_8)).runProcess(timeoutMs, true)

    /** javac prints "C:\...\Square.java:5: error: cannot find symbol". */
    private val JAVAC_ERROR = Regex("""^(?:.*[\\/])?([\w$]+\.java):(\d+): error: (.*)$""")

    /** The line javac prints under "cannot find symbol": "  symbol:   method sayHello()". */
    private val JAVAC_SYMBOL = Regex("""^\s*symbol:\s+(?:\w+\s+)?([\w$]+)""")

    internal fun compileProblems(output: String, learnerFileName: String): List<CompileProblem> {
        val lines = output.lines()
        return lines.mapIndexedNotNull { index, line ->
            JAVAC_ERROR.find(line.trimEnd())?.let { m ->
                var message = m.groupValues[3].trim()
                if (message == "cannot find symbol") {
                    // javac names the missing thing a few lines down; say it the way IntelliJ does, so the rules can name it too.
                    val symbol = lines.drop(index + 1).take(4).firstNotNullOfOrNull { JAVAC_SYMBOL.find(it)?.groupValues?.get(1) }
                    if (symbol != null) message = "Cannot resolve symbol '$symbol'"
                }
                CompileProblem(m.groupValues[2].toInt(), message, inLearnerFile = m.groupValues[1] == learnerFileName)
            }
        }.distinct()
    }

    internal fun parse(drill: Drill, stdout: String): List<CheckResult> =
        stdout.lines().filter { it.startsWith(MARKER) }.mapNotNull { line ->
            val parts = line.removePrefix(MARKER).split(SEPARATOR).drop(1).map { it.replace("\\n", "\n") }
            val kind = parts.getOrNull(0) ?: return@mapNotNull null
            val check = parts.getOrNull(1)?.toIntOrNull()?.let { drill.checks.getOrNull(it) } ?: return@mapNotNull null
            when (kind) {
                "PASS" -> CheckResult(check, passed = true, expected = parts[2], actual = parts[3])
                "FAIL" -> CheckResult(check, passed = false, expected = parts[2], actual = parts[3])
                "CRASH" -> CheckResult(check, passed = false, crash = parts[2], crashMessage = parts[3].ifEmpty { null }, location = parts.getOrNull(4)?.ifEmpty { null })
                else -> null
            }
        }

    /** The hidden test class. Plain Java 8 so it compiles on any JDK the learner might use. */
    internal fun testSource(drill: Drill): String {
        val tests = drill.checks.mapIndexed { index, check ->
            val call = if (check.onClass) "${drill.className}.${check.call}" else check.call
            when (check) {
                is Check.Returns ->
                    "        returns($index, ${check.expected}, new Call() { public Object call() throws Throwable { return $call; } });"
                is Check.Prints ->
                    "        prints($index, ${javaString(check.expected)}, ${javaString(check.input)}, ${check.contains}, new Body() { public void run(Scanner in) throws Throwable { $call; } });"
            }
        }.joinToString("\n")
        return CHECK_TEMPLATE
            .replace("LEARNER_FILE", drill.fileName)
            .replace("    // TESTS", tests)
    }

    private fun javaString(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private val CHECK_TEMPLATE = """
        |import java.io.*;
        |import java.util.*;
        |
        |public class JavaBuddyCheck {
        |    interface Call { Object call() throws Throwable; }
        |    interface Body { void run(Scanner in) throws Throwable; }
        |
        |    static final PrintStream REAL_OUT = System.out;
        |    static final InputStream REAL_IN = System.in;
        |
        |    static void report(String kind, int index, String a, String b, String c) {
        |        StringBuilder line = new StringBuilder("$MARKER");
        |        for (String part : new String[] {kind, String.valueOf(index), a, b, c}) {
        |            line.append('\u001F').append(part == null ? "" : part.replace("\r", "").replace("\n", "\\n").replace("\u001F", " "));
        |        }
        |        REAL_OUT.println(line);
        |        REAL_OUT.flush();
        |    }
        |
        |    static String show(Object value) {
        |        if (value == null) return "null";
        |        if (value instanceof String) return "\"" + value + "\"";
        |        if (value instanceof Character) return "'" + value + "'";
        |        if (value instanceof Double || value instanceof Float) {
        |            double d = ((Number) value).doubleValue();
        |            String plain = String.valueOf(d);
        |            int dot = plain.indexOf('.');
        |            if (dot >= 0 && plain.indexOf('E') < 0 && plain.length() - dot - 1 > 4) {
        |                plain = String.format(Locale.ROOT, "%.4f", d).replaceAll("0+${'$'}", "");
        |                if (plain.endsWith(".")) plain = plain + "0";
        |            }
        |            return plain;
        |        }
        |        return String.valueOf(value);
        |    }
        |
        |    static boolean same(Object expected, Object actual) {
        |        if (expected instanceof Double && actual instanceof Number && !(actual instanceof Integer || actual instanceof Long)) {
        |            return Math.abs(((Double) expected) - ((Number) actual).doubleValue()) < 0.0001;
        |        }
        |        return Objects.equals(expected, actual);
        |    }
        |
        |    static void returns(int index, Object expected, Call call) {
        |        try {
        |            Object actual = call.call();
        |            report(same(expected, actual) ? "PASS" : "FAIL", index, show(expected), show(actual), "");
        |        } catch (Throwable t) {
        |            crashed(index, t);
        |        } finally {
        |            System.setOut(REAL_OUT);
        |            System.setIn(REAL_IN);
        |        }
        |    }
        |
        |    static void prints(int index, String expected, String input, boolean contains, Body body) {
        |        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        |        try {
        |            System.setOut(new PrintStream(buffer, true, "UTF-8"));
        |            System.setIn(new ByteArrayInputStream(input.getBytes("UTF-8")));
        |            body.run(new Scanner(System.in));
        |            System.out.flush();
        |            String actual = tidy(buffer.toString("UTF-8"));
        |            boolean ok = contains ? actual.contains(tidy(expected)) : actual.equals(tidy(expected));
        |            report(ok ? "PASS" : "FAIL", index, "\"" + expected + "\"", "\"" + actual + "\"", "");
        |        } catch (Throwable t) {
        |            System.setOut(REAL_OUT);
        |            crashed(index, t);
        |        } finally {
        |            System.setOut(REAL_OUT);
        |            System.setIn(REAL_IN);
        |        }
        |    }
        |
        |    /** Same line endings everywhere, no spaces at line ends, no blank lines at the start or end. */
        |    static String tidy(String text) {
        |        String[] lines = text.replace("\r", "").split("\n", -1);
        |        StringBuilder out = new StringBuilder();
        |        for (String line : lines) {
        |            if (out.length() > 0) out.append('\n');
        |            out.append(line.replaceAll("\\s+${'$'}", ""));
        |        }
        |        return out.toString().trim();
        |    }
        |
        |    static void crashed(int index, Throwable t) {
        |        String where = "";
        |        for (StackTraceElement frame : t.getStackTrace()) {
        |            if ("LEARNER_FILE".equals(frame.getFileName())) {
        |                where = frame.getFileName() + ":" + frame.getLineNumber();
        |                break;
        |            }
        |        }
        |        report("CRASH", index, t.getClass().getName(), t.getMessage(), where);
        |    }
        |
        |    public static void main(String[] args) {
        |    // TESTS
        |        System.exit(0);
        |    }
        |}
        |""".trimMargin()
}
