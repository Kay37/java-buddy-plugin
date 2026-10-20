package dev.javabuddy.quest

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/** The bar across the top of a quest file: what to do, Check my answer, Hint. */
class QuestNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val quest = QuestService.questFor(file) ?: return null
        val quests = project.service<QuestService>()
        return Function { editor ->
            val done = quests.isDone(quest)
            val predict = !quest.kind().answersWithCode()
            EditorNotificationPanel(editor, if (done) EditorNotificationPanel.Status.Success else EditorNotificationPanel.Status.Info).apply {
                text = when {
                    done -> "Quest done ✓  ${quest.title()}. You can keep changing it and check again."
                    predict -> "Java Quest: ${quest.title()}. The program is in the Quest tab. Type what it prints here, then check."
                    else -> "Java Quest: ${quest.title()} (${quest.kind().label()}). Write your code, then check it."
                }
                createActionLabel(if (predict) "Check my guess" else "Check my answer") { quests.check(quest) }
                createActionLabel("Hint") { quests.hint(quest) }
                createActionLabel("Show the quest") { quests.focus(quest) }
                if (done) quests.nextQuest(quest)?.let { next -> createActionLabel("Next quest") { quests.open(next) } }
                if (!predict) createActionLabel("Start again") { quests.reset(quest) }
            }
        }
    }
}
