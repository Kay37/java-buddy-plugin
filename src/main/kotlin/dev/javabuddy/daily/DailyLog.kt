package dev.javabuddy.daily

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import dev.javacoach.engine.daily.MistakeNotebook
import dev.javacoach.engine.daily.UsageLog
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Writes to the Java Coach engine's daily files from anywhere in the plugin, off the UI thread:
 * the mistakes notebook (red errors that stayed, crashes, Style Coach counts) and the usage log.
 * Each thing is written at most once a day, so typing never turns into a stream of disk writes.
 */
object DailyLog {
    private val seenToday = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var day = LocalDate.now()
    private val lock = Any()
    private val LOG = logger<DailyLog>()

    /** A red error that stayed after typing paused ("error"), or a crash ("crash"). */
    fun mistake(kind: String, title: String) = once("m|$kind|$title") {
        synchronized(lock) {
            val book = MistakeNotebook.load(MistakeNotebook.defaultFile())
            book.record(LocalDate.now(), kind, title)
            book.save(MistakeNotebook.defaultFile())
        }
    }

    /** One file's Style Coach problems (bugs + worth fixing), counted for the first check of the file each day. */
    fun style(filePath: String, problems: Int) = once("s|$filePath") {
        synchronized(lock) {
            val book = MistakeNotebook.load(MistakeNotebook.defaultFile())
            book.recordStyle(LocalDate.now(), problems)
            book.save(MistakeNotebook.defaultFile())
        }
    }

    /** One use of a feature ("coach", "quest", "drill", "dashboard", "today"), counted once a day. */
    fun use(feature: String) = once("u|$feature") { UsageLog.note(feature) }

    private fun once(key: String, write: () -> Unit) {
        val today = LocalDate.now()
        if (today != day) {
            day = today
            seenToday.clear()
        }
        if (!seenToday.add(key)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                write()
            }
            catch (e: Exception) {
                LOG.info("Couldn't write Java Coach daily file: ${e.message}")
            }
        }
    }
}
