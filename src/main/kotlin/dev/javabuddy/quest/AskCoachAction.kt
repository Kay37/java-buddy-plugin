package dev.javabuddy.quest

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.daily.DailyLog
import dev.javacoach.engine.ai.CoachAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Right-click > "Ask the AI Coach" in any Java file. In a quest file it's the quest's own coach (it
 * knows the task and the last check). In any other file it reads the learner's code (and the lines
 * they selected) and asks or points; it never fixes their course work. The reply is a bubble under
 * the selection, or under the cursor's line.
 */
class AskCoachAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null &&
            file != null && (file.name.endsWith(".java") || QuestService.questFor(file) != null)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val quests = project.service<QuestService>()
        QuestService.questFor(file)?.let { quest ->
            quests.askCoach(quest, CoachAI.Mode.STUCK)
            return
        }

        val selection = editor.selectionModel
        val selected = selection.selectedText.orEmpty()
        val document = editor.document
        val firstLine = if (selected.isNotBlank()) document.getLineNumber(selection.selectionStart) else -1
        val prompt = if (selected.isNotBlank()) "What would you like help with in the lines you selected? (You can leave this empty: the coach reads them and picks something to ask about.)"
                     else "What would you like help with? (You can leave this empty: the coach reads this file and picks something to ask about. Tip: select some lines first to ask about just those.)"
        val words = Messages.showMultilineInputDialog(project, prompt, "Ask the AI Coach", "", null, null) ?: return

        // Under the last selected line, or the cursor's line.
        val line = if (selected.isNotBlank()) document.getLineNumber((selection.selectionEnd - 1).coerceAtLeast(selection.selectionStart)) else editor.caretModel.logicalPosition.line
        val code = document.text
        DailyLog.use("ai-coach")
        quests.showBubble(file, line, CoachView(CoachAI.Mode.STUCK, null, null, false))
        project.service<JavaBuddyScope>().cs.launch {
            val reply = withContext(Dispatchers.IO) {
                CoachAI.usual().helpWithCode(file.name, code, selected, firstLine + 1, words)
            }
            withContext(Dispatchers.EDT) { quests.showBubble(file, line, CoachView(CoachAI.Mode.STUCK, reply.text(), reply.problem(), reply.filtered())) }
        }
    }
}
