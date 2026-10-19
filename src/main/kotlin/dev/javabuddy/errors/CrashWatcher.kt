package dev.javabuddy.errors

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.JavaBuddySettings
import dev.javabuddy.notifyUser
import kotlinx.coroutines.launch

/**
 * Listens to every run of the learner's programs. When one ends with an exception (or can't
 * start), works out what happened and where in their own code, and explains it.
 */
class CrashWatcher(private val project: Project) : ExecutionListener {

    // processStarting, not processStarted: a short program can print its crash and exit before
    // processStarted fires, and a listener added then never hears the output or the end.
    override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val explainer = project.service<ErrorExplainer>()
        explainer.reportCrash(null)
        val output = OutputLines()
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) = output.add(event.text)
            override fun processTerminated(event: ProcessEvent) {
                val lines = output.finish()
                project.service<JavaBuddyScope>().cs.launch {
                    val crash = analyse(lines)
                    LOG.info("Run ended (exit ${event.exitCode}, ${lines.size} lines): ${crash?.exception ?: "no crash"}")
                    crash?.let(::report)
                }
            }
        })
    }

    private suspend fun analyse(lines: List<String>): Crash? {
        for (line in lines) {
            for (rule in LAUNCH_RULES) {
                rule.regex.find(line)?.let { return Crash(line.trim(), rule.explain(it), null, -1, null) }
            }
        }
        val trace = StackTrace.parse(lines) ?: return null
        val explanation = explainCrash(trace.exception, trace.message)
        // The first frame that's in the learner's own code is where to look; library frames above it rarely help.
        for (frame in trace.frames) {
            val file = readAction { sourceFile(frame.className) } ?: continue
            return Crash(trace.exception, explanation, file, frame.line - 1, "${frame.fileName}:${frame.line}")
        }
        return Crash(trace.exception, explanation, null, -1, trace.frames.firstOrNull()?.let { "${it.fileName}:${it.line}" })
    }

    private fun sourceFile(className: String): VirtualFile? {
        val outer = className.substringBefore('$')
        return JavaPsiFacade.getInstance(project).findClass(outer, GlobalSearchScope.projectScope(project))
            ?.containingFile?.virtualFile
    }

    private fun report(crash: Crash) {
        val explainer = project.service<ErrorExplainer>()
        explainer.reportCrash(crash)
        if (!JavaBuddySettings.crashPopups) return
        val where = crash.location?.let { " (line ${it.substringAfter(':')} of ${it.substringBefore(':')})" }.orEmpty()
        val actions = buildList {
            add(NotificationAction.createSimpleExpiring("Explain more") { explainer.showPanel() })
            val file = crash.file
            if (file != null && crash.line >= 0) {
                add(NotificationAction.createSimple("Go to ${crash.location}") { explainer.navigate(file, crash.line) })
            }
        }
        notifyUser(project, NotificationType.ERROR, "Your program crashed$where", "${crash.explanation.title}\n${crash.explanation.meaning}", actions)
    }

    /** Collects output as whole lines, keeping only the end: that's where a crash is printed. */
    private class OutputLines {
        private val lines = ArrayDeque<String>()
        private val partial = StringBuilder()

        @Synchronized
        fun add(text: String) {
            partial.append(text)
            var newline = partial.indexOf("\n")
            while (newline >= 0) {
                lines.addLast(partial.substring(0, newline).trimEnd('\r'))
                if (lines.size > MAX_LINES) lines.removeFirst()
                partial.delete(0, newline + 1)
                newline = partial.indexOf("\n")
            }
        }

        @Synchronized
        fun finish(): List<String> {
            if (partial.isNotEmpty()) lines.addLast(partial.toString())
            return lines.toList()
        }

        private companion object {
            const val MAX_LINES = 400
        }
    }

    private companion object {
        val LOG = logger<CrashWatcher>()
    }
}

/** The last uncaught exception in some output: its root cause and the stack frames under it. */
data class StackTrace(val exception: String, val message: String?, val frames: List<Frame>) {
    data class Frame(val className: String, val method: String, val fileName: String, val line: Int)

    companion object {
        private val HEADER = Regex("""^Exception in thread "[^"]*" ([\w.$]+?)(?:: (.*))?$""")
        private val CAUSED_BY = Regex("""^Caused by: ([\w.$]+?)(?:: (.*))?$""")
        private val FRAME = Regex("""^\s*at (?:[\w.-]+/)*([\w.$]+)\.([\w$<>]+)\(([^:()]+):(\d+)\)""")

        /** Finds the last "Exception in thread" in [lines] and, if it has a "Caused by", that root cause. */
        fun parse(lines: List<String>): StackTrace? {
            val start = lines.indexOfLast { HEADER.containsMatchIn(it) }
            if (start < 0) return null
            var (exception, message) = HEADER.find(lines[start])!!.destructured
            var frames = mutableListOf<Frame>()
            for (line in lines.drop(start + 1)) {
                val cause = CAUSED_BY.find(line)
                if (cause != null) {
                    exception = cause.groupValues[1]
                    message = cause.groupValues[2]
                    frames = mutableListOf()
                    continue
                }
                FRAME.find(line)?.let { frames += Frame(it.groupValues[1], it.groupValues[2], it.groupValues[3], it.groupValues[4].toInt()) }
            }
            return StackTrace(exception, message.ifEmpty { null }, frames)
        }
    }
}
