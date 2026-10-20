package dev.javabuddy.drills

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.errors.explainCompileError
import dev.javabuddy.errors.explainCrash
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.keepScrollAt
import dev.javabuddy.ui.CardList
import dev.javabuddy.ui.heading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/** The "Drills" tab: the latest result on top, then every drill grouped by topic. */
class DrillsPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val drills = project.service<DrillService>()
    private val list = CardList()
    private val scroll = JBScrollPane(list).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        border = null
    }

    init {
        add(scroll, BorderLayout.CENTER)
        val job = project.service<JavaBuddyScope>().cs.launch {
            drills.state.collect { state -> withContext(Dispatchers.EDT) { render(state) } }
        }
        Disposer.register(this) { job.cancel() }
    }

    private var lastShownResult: Pair<String?, DrillOutcome?>? = null

    private fun render(state: DrillState) {
        val scrollPosition = scroll.verticalScrollBar.value
        list.removeAll()

        val last = state.lastChecked?.let { name -> DRILLS.firstOrNull { it.className == name } }
        if (last != null) list.addCard(resultCard(last, state))

        list.addCard(introCard(state))
        for (topic in Topic.entries) {
            val inTopic = DRILLS.filter { it.topic == topic }
            val done = inTopic.count { it.className in state.passed }
            list.addCard(heading("${topic.title}${if (topic.soon) "  (coming up in the course)" else ""}  ·  $done of ${inTopic.size} done"))
            inTopic.forEach { list.addCard(drillCard(it, state)) }
        }
        list.revalidate()
        list.repaint()

        // A new result jumps to the top; anything else (a hint opening) keeps the scroll position.
        val shown = state.lastChecked to state.lastChecked?.let { state.outcomes[it] }
        val jumpToTop = shown != lastShownResult || state.running != null
        lastShownResult = shown
        scroll.keepScrollAt(if (jumpToTop) 0 else scrollPosition)
    }

    private fun introCard(state: DrillState): Card = Card(BuddyColors.HELP_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
        val done = state.passed.count { name -> DRILLS.any { it.className == name } }
        chip("$done of ${DRILLS.size} done")
        title("Practice drills")
        progressBar(done, DRILLS.size)
        text("Small exercises for what you're learning. Press Start to open one, write your code, then press Check my answer at the top of the file.")
        text("Your drill files are kept in IntelliJ's Scratches folder (Project panel → Scratches and Consoles), not in your course projects.", muted = true)
    }

    private fun drillCard(drill: Drill, state: DrillState): Card {
        val done = drill.className in state.passed
        val card = if (done) Card(BuddyColors.GOOD_ACCENT, BuddyColors.GOOD_BACKGROUND) else Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND)
        return card.apply {
            chip(if (done) "Done ✓" else drill.topic.short)
            title(drill.title)
            text(drill.task)
            if (drill.className in state.hintsShown) section("HINT", drill.hint)
            val started = drills.hasFile(drill)
            val links = buildList<Pair<String, () -> Unit>> {
                add((if (started) "Open" else "Start") to { drills.open(drill) })
                if (started) add("Check my answer" to { drills.check(drill) })
                add((if (drill.className in state.hintsShown) "Hide hint" else "Hint") to { drills.toggleHint(drill) })
            }
            links(*links.toTypedArray())
        }
    }

    private fun resultCard(drill: Drill, state: DrillState): Card {
        if (state.running == drill.className) {
            return Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip("Checking…")
                title("Checking ${drill.title}…")
                text("Java Buddy is building and testing your code. This takes a few seconds.", muted = true)
            }
        }
        val outcome = state.outcomes[drill.className] ?: return hintOnlyCard(drill)
        return when (outcome) {
            is DrillOutcome.Checked -> checkedCard(drill, outcome)
            is DrillOutcome.DidNotCompile -> compileCard(drill, outcome)
            is DrillOutcome.NoJdk -> Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip("Needs Java set up")
                title("Java Buddy needs Java's tools (a JDK) to check drills")
                text("${outcome.reason} Open File → Project Structure → Project, and under SDK pick a Java version, for example corretto-17. Then press Check my answer again.")
            }
            is DrillOutcome.Failed -> Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip("Couldn't check")
                title("Java Buddy couldn't check ${drill.title}")
                text(outcome.reason)
                links("Try again" to { drills.check(drill) })
            }
        }
    }

    /** Shown on top after "Hint" in the editor bar, before the drill has been checked. */
    private fun hintOnlyCard(drill: Drill): Card = Card(BuddyColors.HELP_ACCENT, BuddyColors.HELP_BACKGROUND).apply {
        chip(drill.topic.short)
        title(drill.title)
        text(drill.task)
        section("HINT", drill.hint)
        links("Check my answer" to { drills.check(drill) }, "Open" to { drills.open(drill) })
    }

    private fun checkedCard(drill: Drill, outcome: DrillOutcome.Checked): Card {
        val lines = outcome.results.joinToString("\n") { result ->
            when {
                result.passed -> "✓ ${result.check.label}"
                result.crash != null -> "✗ ${result.check.label}: crashed"
                else -> "✗ ${result.check.label}: expected ${result.expected}, got ${result.actual}"
            }
        }
        if (outcome.allPassed) {
            return Card(BuddyColors.GOOD_ACCENT, BuddyColors.GOOD_BACKGROUND).apply {
                chip("All ${outcome.total} checks passed")
                title("Nice work! ${drill.title} is done.")
                example(lines)
                val next = drills.nextDrill(drill)
                if (next != null) links("Next drill: ${next.title}" to { drills.open(next) }) else text("That was the last one. Every drill is done!")
            }
        }
        return Card(BuddyColors.HELP_ACCENT, BuddyColors.HELP_BACKGROUND).apply {
            chip("${outcome.passedCount} of ${outcome.total} checks passed")
            title(if (outcome.passedCount == 0) "Not yet. Look at the ✗ lines" else "Almost! Look at the ✗ lines")
            example(lines)
            if (outcome.timedOut) {
                section(
                    "YOUR CODE KEPT RUNNING",
                    "It was still going after 5 seconds, so Java Buddy stopped it. Usually that's a loop that never ends: check that the loop's condition becomes false at some point.",
                )
            }
            val firstCrash = outcome.results.firstOrNull { it.crash != null }
            if (firstCrash != null) {
                val explanation = explainCrash(firstCrash.crash!!, firstCrash.crashMessage)
                val where = firstCrash.location?.let { " (line ${it.substringAfter(':')})" }.orEmpty()
                section("YOUR CODE CRASHED$where", "${explanation.title}. ${explanation.meaning}")
                section("HOW TO FIX IT", explanation.fix)
            }
            val why = outcome.results.firstOrNull { !it.passed && it.crash == null }?.check?.why
            if (why != null) section("WHY IT MIGHT BE WRONG", why)
            val missing = outcome.total - outcome.results.size
            if (missing > 0 && !outcome.timedOut && firstCrash == null) text("$missing more checks didn't run.", muted = true)
            links("Check again" to { drills.check(drill) }, "Open" to { drills.open(drill) }, "Hint" to { drills.showHint(drill) })
        }
    }

    private fun compileCard(drill: Drill, outcome: DrillOutcome.DidNotCompile): Card = Card(BuddyColors.CRASH_ACCENT, BuddyColors.CRASH_BACKGROUND).apply {
        val own = outcome.problems.filter { it.inLearnerFile }
        val methodName = Regex("""(\w+)\s*\($""").find(drill.signature.substringBefore(')').substringBeforeLast('(') + "(")?.groupValues?.get(1)
        val onlyMissingMethod = own.isNotEmpty() && own.all { it.message == "Cannot resolve symbol '$methodName'" }
        if (onlyMissingMethod) {
            chip("Method not written yet")
            title("Write the $methodName method")
            text("main calls $methodName, but there's no method with that name yet. Write it inside the class, but outside main's { }.")
            section("ITS FIRST LINE SHOULD BE", drill.signature)
            example("✓ ${drill.signature} {\n      ...\n  }")
        }
        else if (own.isNotEmpty()) {
            chip("Didn't build")
            title("Fix the red errors in ${drill.fileName} first")
            own.take(3).forEach { problem ->
                val explanation = explainCompileError(problem.message)
                if (explanation != null) section("LINE ${problem.line}: ${explanation.title.uppercase()}", explanation.fix)
                else section("LINE ${problem.line}", "Java says: ${problem.message}")
            }
            if (own.size > 3) text("…and ${own.size - 3} more. The yellow bubbles in the file explain each one.", muted = true)
        }
        else {
            chip("Can't find your method")
            title("Java Buddy couldn't call your method")
            text("Your code builds, but the method doesn't match what the drill asks for. Check its name (capital letters count), that it says static, and what's in the brackets.")
            section("ITS FIRST LINE SHOULD BE", drill.signature)
            example("✓ ${drill.signature} {\n      ...\n  }")
        }
        links("Check again" to { drills.check(drill) }, "Open" to { drills.open(drill) })
    }

    override fun dispose() = Unit
}
