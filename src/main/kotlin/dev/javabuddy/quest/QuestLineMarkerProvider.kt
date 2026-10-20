package dev.javabuddy.quest

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier

/**
 * A ▶ in the gutter next to a quest file's class line: "Check my answer", the same way a program
 * is run. (Not on every save: IntelliJ saves by itself often, and a check each time would be noise.)
 */
class QuestLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) return null
        val owner = element.parent as? PsiClass ?: return null
        if (owner.nameIdentifier != element) return null
        val file = element.containingFile?.virtualFile ?: return null
        val quest = QuestService.questFor(file) ?: return null
        if (QuestService.className(quest) != owner.name) return null
        return LineMarkerInfo(
            element, element.textRange, AllIcons.RunConfigurations.TestState.Run,
            { "Java Quest: check my answer" },
            { _, elt -> elt.project.service<QuestService>().check(quest) },
            GutterIconRenderer.Alignment.LEFT,
            { "Check my answer" },
        )
    }
}
