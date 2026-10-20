package dev.javabuddy

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineScope

/** Coroutine scope tied to the project's lifetime; cancelled when the project closes or the plugin unloads. */
@Service(Service.Level.PROJECT)
class JavaBuddyScope(val cs: CoroutineScope)

/** Shows a balloon in the "Java Buddy" group. [content] is plain text; newlines become line breaks. */
fun notifyUser(
    project: Project,
    type: NotificationType,
    title: String,
    content: String = "",
    actions: List<AnAction> = emptyList(),
) {
    val html = StringUtil.escapeXmlEntities(content.trim()).replace("\n", "<br>")
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Java Buddy")
        .createNotification(title, html, type)
        .addActions(actions)
        .notify(project)
}

fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"

/** IDE-wide on/off switches; all default to on. Keys are prefixed `javaBuddy.`. */
object JavaBuddySettings {
    private const val ERROR_BUBBLES = "javaBuddy.errorBubbles"
    private const val CRASH_POPUPS = "javaBuddy.crashPopups"
    private const val CONCEPT_HOVER = "javaBuddy.conceptHover"
    private const val TODAY_NOTE = "javaBuddy.todayNote"

    /** A small note with today's quest, once a day when a project opens. */
    var todayNote: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(TODAY_NOTE, true)
        set(value) = PropertiesComponent.getInstance().setValue(TODAY_NOTE, value, true)

    /** Plain-English bubbles under lines with a red error. */
    var errorBubbles: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(ERROR_BUBBLES, true)
        set(value) = PropertiesComponent.getInstance().setValue(ERROR_BUBBLES, value, true)

    /** A popup explaining why the program crashed, when a run ends with an exception. */
    var crashPopups: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(CRASH_POPUPS, true)
        set(value) = PropertiesComponent.getInstance().setValue(CRASH_POPUPS, value, true)

    /** A Concept Card popup when the mouse rests on a Java word like static. */
    var conceptHover: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(CONCEPT_HOVER, true)
        set(value) = PropertiesComponent.getInstance().setValue(CONCEPT_HOVER, value, true)
}
