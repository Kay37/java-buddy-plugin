package dev.javabuddy.concepts

import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReferenceParameterList
import com.intellij.psi.PsiSwitchLabelStatementBase
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.util.PsiTreeUtil

/**
 * A Java word in the code that has a card. [hoverable] is false for names IntelliJ already shows its
 * own popup for (String, @Override), so two popups don't pile up; those cards still show in the panel.
 */
data class ConceptHit(val concept: Concept, val range: TextRange, val hoverable: Boolean)

/** The card for where a method or constructor is made: constructor, overloading, or getter/setter. */
private fun declarationCard(method: PsiMethod): Concept? {
    val sameName = method.containingClass?.findMethodsByName(method.name, false)?.size ?: 1
    return when {
        method.isConstructor -> Concepts.CONSTRUCTOR
        sameName > 1 -> Concepts.OVERLOADING
        Regex("""^(get|set|is)[A-Z]\w*$""").matches(method.name) -> Concepts.GETTERS_SETTERS
        else -> null
    }
}

/** The card for a called method's name, like substring in name.substring(1), or parseInt in Integer.parseInt(text). */
private fun callCard(reference: PsiReferenceExpression, name: String): Concept? {
    val card = Concepts.BY_CALL[name] ?: return null
    if (card.on != null && reference.qualifierExpression?.text !in card.on) return null
    return card.concept
}

/** The card for the Java word at [offset], or null. Needs read access and committed PSI. */
fun conceptAt(file: PsiFile, offset: Int): ConceptHit? {
    if (file !is PsiJavaFile || offset < 0 || offset >= file.textLength) return null
    val token = file.findElementAt(offset) as? PsiJavaToken ?: return null
    val range = token.textRange
    val text = token.text
    val parent = token.parent

    if (token is PsiIdentifier) {
        return when {
            text == "String" && parent is PsiJavaCodeReferenceElement -> ConceptHit(Concepts.STRING, range, hoverable = false)
            text == "Override" && parent?.parent is PsiAnnotation -> ConceptHit(Concepts.OVERRIDE, range, hoverable = false)
            // "var" and "record" are only special in some places, so IntelliJ may see them as plain names.
            text == "var" && PsiTreeUtil.getParentOfType(token, PsiTypeElement::class.java)?.isInferredType == true ->
                ConceptHit(Concepts.VAR, range, hoverable = true)
            text == "record" && parent is PsiClass && parent.isRecord -> ConceptHit(Concepts.RECORD, range, hoverable = true)
            // Names, not Java words: IntelliJ shows its own popup for them, so these cards show in the panel only.
            parent is PsiMethod && parent.nameIdentifier == token -> declarationCard(parent)?.let { ConceptHit(it, range, hoverable = false) }
            parent is PsiReferenceExpression && parent.parent is PsiMethodCallExpression -> callCard(parent, text)?.let { ConceptHit(it, range, hoverable = false) }
            parent is PsiJavaCodeReferenceElement -> Concepts.BY_CLASS_NAME[text]?.let { ConceptHit(it, range, hoverable = false) }
            else -> null
        }
    }

    return when (token.tokenType) {
        JavaTokenType.LT, JavaTokenType.GT ->
            if (parent is PsiReferenceParameterList || parent is PsiTypeParameterList) ConceptHit(Concepts.GENERICS, range, hoverable = true) else null
        // The ( ) of a method, where it's made or called. Not the ( ) of if, for, catch and so on.
        JavaTokenType.LPARENTH, JavaTokenType.RPARENTH ->
            if (parent is PsiParameterList || (parent is PsiExpressionList && parent.parent is PsiCallExpression)) ConceptHit(Concepts.PARAMETERS, range, hoverable = true) else null
        JavaTokenType.LBRACKET, JavaTokenType.RBRACKET -> ConceptHit(Concepts.ARRAY, range, hoverable = true)
        JavaTokenType.AT ->
            if ((parent as? PsiAnnotation)?.nameReferenceElement?.referenceName == "Override") ConceptHit(Concepts.OVERRIDE, range, hoverable = true) else null
        else -> {
            if (token !is PsiKeyword && text != "null") return null
            // "default" is also used for interface methods; only the switch one has a card.
            if (text == "default") {
                return if (parent is PsiSwitchLabelStatementBase) ConceptHit(Concepts.SWITCH, range, hoverable = true) else null
            }
            Concepts.BY_KEYWORD[text]?.let { ConceptHit(it, range, hoverable = true) }
        }
    }
}
