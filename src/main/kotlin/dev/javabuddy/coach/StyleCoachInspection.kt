package dev.javabuddy.coach

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import dev.javacoach.engine.style.Finding
import dev.javacoach.engine.style.RuleFeedback
import dev.javacoach.engine.style.Severity
import dev.javacoach.engine.style.StyleChecker
import java.nio.file.Files

/**
 * Shows Style Coach tips right in the editor, as soft dotted underlines on the line they're about
 * (bugs get a stronger warning). Alt+Enter offers "Why?" (opens the Coach tab) and "Not helpful".
 * Neither changes the code: the coach explains, the learner fixes.
 */
class StyleCoachInspection : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!file.name.endsWith(".java")) return null
        val text = file.text
        val findings = Feedback.current().visible(StyleChecker().check(text).findings())
        val document = file.viewProvider.document ?: return null
        return findings.filter { it.severity() != Severity.GOOD && it.line() in 1..document.lineCount }.map { finding ->
            val lineIndex = finding.line() - 1
            val start = document.getLineStartOffset(lineIndex)
            val end = document.getLineEndOffset(lineIndex)
            val lineText = text.substring(start, end)
            val from = start + (lineText.length - lineText.trimStart().length)
            val to = maxOf(from + 1, start + lineText.trimEnd().length).coerceAtMost(text.length)
            val type = if (finding.severity() == Severity.BUG) ProblemHighlightType.WARNING else ProblemHighlightType.WEAK_WARNING
            val fixes = buildList<LocalQuickFix> {
                add(WhyFix())
                if (finding.severity() != Severity.BUG) add(NotHelpfulFix(finding))
            }
            manager.createProblemDescriptor(file, TextRange(from, to), "Java Buddy: " + plain(finding.title()), type, isOnTheFly,
                *fixes.toTypedArray())
        }.toTypedArray()
    }

    /** Opens the Coach tab, which explains the tip with an example. */
    private class WhyFix : LocalQuickFix {
        override fun getFamilyName() = "Why? (Java Buddy explains)"
        override fun startInWriteAction() = false
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) = project.service<StyleCoach>().showPanel()
    }

    /** Hides this kind of tip from now on, like "Not helpful" in the Coach tab. */
    private class NotHelpfulFix(private val finding: Finding) : LocalQuickFix {
        override fun getFamilyName() = "Not helpful: stop showing tips like this"
        override fun startInWriteAction() = false
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) = project.service<StyleCoach>().vote(finding, false)
    }

    /** The saved thumbs up/down, read again only when the file changes on disk. */
    private object Feedback {
        @Volatile private var cached: Pair<Long, RuleFeedback>? = null

        fun current(): RuleFeedback {
            val file = RuleFeedback.defaultFile()
            val stamp = runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrDefault(0L)
            cached?.let { (time, feedback) -> if (time == stamp) return feedback }
            return RuleFeedback.load(file).also { cached = stamp to it }
        }
    }
}
