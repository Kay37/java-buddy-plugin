package dev.javabuddy.coach

import dev.javabuddy.ui.Card

/** The Java Coach engine writes `code`, **bold** and ```java blocks; Swing cards show plain text and code boxes. */
fun plain(text: String): String = text.replace("**", "").replace("`", "")

private val FENCE = Regex("```(?:java)?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

/** Adds [message] to the card: its text parts as wrapped text, its ```java parts as code boxes. */
fun Card.richText(message: String, muted: Boolean = false) {
    var last = 0
    for (match in FENCE.findAll(message)) {
        val before = message.substring(last, match.range.first).trim()
        if (before.isNotEmpty()) text(plain(before), muted)
        example(match.groupValues[1].trimEnd())
        last = match.range.last + 1
    }
    val after = message.substring(last).trim()
    if (after.isNotEmpty()) text(plain(after), muted)
}
