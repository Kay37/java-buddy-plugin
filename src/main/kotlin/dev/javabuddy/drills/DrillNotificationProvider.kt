package dev.javabuddy.drills

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/** The bar across the top of a drill file: what to do, and the Check my answer button. */
class DrillNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val drill = drillFor(file.name) ?: return null
        if (ScratchFileService.findRootType(file) !is ScratchRootType) return null
        val drills = project.service<DrillService>()
        return Function { editor ->
            val done = drills.isPassed(drill)
            EditorNotificationPanel(editor, if (done) EditorNotificationPanel.Status.Success else EditorNotificationPanel.Status.Info).apply {
                text = if (done) "Drill done ✓  ${drill.title}. You can keep changing it and check again."
                       else "Java Buddy drill: ${drill.title}. Write your code, then check it."
                createActionLabel("Check my answer") { drills.check(drill) }
                createActionLabel("Hint") { drills.showHint(drill) }
                if (done) drills.nextDrill(drill)?.let { next -> createActionLabel("Next drill") { drills.open(next) } }
                createActionLabel("Start again") { drills.reset(drill) }
            }
        }
    }
}
