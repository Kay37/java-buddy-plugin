package dev.javabuddy.memory

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.CardList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel

/** The "Memory" tab: a short explanation on top, the boxes-and-arrows picture below. */
class MemoryPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val memory = project.service<MemoryService>()
    private val header = CardList()
    private val picture = MemoryPicture()

    init {
        add(header, BorderLayout.NORTH)
        add(JBScrollPane(picture).apply { border = null }, BorderLayout.CENTER)
        val job = project.service<JavaBuddyScope>().cs.launch {
            memory.state.collect { state -> withContext(Dispatchers.EDT) { render(state) } }
        }
        Disposer.register(this) { job.cancel() }
    }

    private fun render(state: MemoryState) {
        header.removeAll()
        header.addCard(when (state) {
            MemoryState.Idle -> howTo()
            MemoryState.Running -> Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip("Running")
                title("Your program is running")
                text("The picture appears when it stops at a red dot (breakpoint). Press F8 to go one line at a time once it's stopped.", muted = true)
            }
            is MemoryState.Unavailable -> Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip("No picture here")
                title("Can't draw this spot")
                text(state.reason)
            }
            is MemoryState.Paused -> Card(BuddyColors.CONCEPT_ACCENT, BuddyColors.CONCEPT_BACKGROUND).apply {
                chip("Paused at ${state.snapshot.location}")
                title("What's in memory right now")
                text("The left box is your variables. Numbers, true/false and text sit right inside. Arrays and objects live in their own boxes: an arrow shows which box a variable points to.")
                val notes = buildList {
                    if (state.changed.isNotEmpty()) add("Yellow = changed since the last step.")
                    add("F8 runs the next line.")
                    if (state.snapshot.truncated) add("Some boxes were left out to keep the picture small (shown as …).")
                }
                text(notes.joinToString(" "), muted = true)
            }
        })
        header.revalidate()
        header.repaint()
        picture.show((state as? MemoryState.Paused)?.snapshot, (state as? MemoryState.Paused)?.changed.orEmpty())
    }

    private fun howTo(): Card = Card(BuddyColors.CONCEPT_ACCENT, BuddyColors.CONCEPT_BACKGROUND).apply {
        chip("Memory Picture")
        title("See your variables as boxes and arrows")
        section("1. ADD A RED DOT", "Click in the grey margin just left of a line of code, next to its line number. A red dot (a breakpoint) appears. The program will stop there.")
        section("2. START DEBUGGING", "Click the green ▶ next to main and choose Debug (the bug icon), instead of Run.")
        section("3. STEP THROUGH", "When it stops, this tab draws your variables. Press F8 to run one more line and watch the boxes change.")
        example("✓ int[] scores = {90, 85, 77};   // <- red dot here\n  String name = \"Kim\";")
    }

    override fun dispose() = Unit
}
