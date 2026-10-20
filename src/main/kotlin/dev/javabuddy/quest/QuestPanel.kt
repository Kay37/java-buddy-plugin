package dev.javabuddy.quest

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.coach.plain
import dev.javabuddy.coach.richText
import dev.javabuddy.pluralize
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.CardList
import dev.javabuddy.ui.heading
import dev.javacoach.engine.game.check.CheckResult
import dev.javacoach.engine.game.model.Quest
import dev.javacoach.engine.game.model.QuestKind
import dev.javacoach.engine.game.progress.Achievements
import dev.javacoach.engine.game.progress.XpRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/** The "Quest" tab: the latest result on top, then XP and rank, then every level and its quests. */
class QuestPanel(project: Project) : JPanel(BorderLayout()), Disposable {
    private val quests = project.service<QuestService>()
    private val list = CardList()
    private val scroll = JBScrollPane(list).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        border = null
    }

    init {
        add(scroll, BorderLayout.CENTER)
        quests.reload()
        val job = project.service<JavaBuddyScope>().cs.launch {
            quests.state.collect { state -> withContext(Dispatchers.EDT) { render(state) } }
        }
        Disposer.register(this) { job.cancel() }
    }

    private var lastShownTop: Any? = null

    private fun render(state: QuestState) {
        val scrollPosition = scroll.verticalScrollBar.value
        list.removeAll()

        val focus = state.focus?.let { id -> QuestService.COURSE.quest(id).orElse(null) }
        if (focus != null) list.addCard(topCard(focus, state))
        val another = focus?.let { state.anotherWay[it.id()] }
        if (focus != null && another != null) list.addCard(anotherWayCard(focus, another))
        list.addCard(playerCard(state))

        for (level in QuestService.COURSE.levels()) {
            val done = level.quests().count { state.progress.isDone(it.id()) }
            val open = QuestService.COURSE.isUnlocked(level, state.progress.completed())
            list.addCard(heading("Level ${level.number()}: ${level.name()}  ·  $done of ${level.quests().size} done${if (open) "" else "  (locked)"}"))
            level.quests().forEach { list.addCard(questCard(it, state, open)) }
        }
        list.revalidate()
        list.repaint()

        // A new result or a newly focused quest jumps to the top; anything else keeps the scroll position.
        val top = Triple(state.focus, state.running, state.focus?.let { state.results[it] })
        scroll.verticalScrollBar.value = if (top != lastShownTop) 0 else scrollPosition
        lastShownTop = top
    }

    private fun playerCard(state: QuestState): Card = Card(BuddyColors.PROGRESS_ACCENT, BuddyColors.PROGRESS_BACKGROUND).apply {
        val progress = state.progress
        val xp = progress.xp()
        val toNext = XpRules.xpToNextRank(xp)
        chip("Rank: ${progress.rank()}")
        title("Java Quest  ·  $xp XP")
        progressBar(xp, xp + toNext)
        text(if (toNext > 0) "$toNext XP to the next rank." else "Top rank! Every quest still counts as practice.")
        if (progress.streakDays() > 0) text("Streak: ${pluralize(progress.streakDays(), "day")} in a row. From ${XpRules.STREAK_BONUS_FROM} days on, your first quest each day gives +${XpRules.STREAK_BONUS} XP.")
        val allQuests = QuestService.COURSE.levels().flatMap { it.quests() }
        val stars = allQuests.filter { progress.isDone(it.id()) }.sumOf { maxOf(1, progress.stars(it.id())) }
        val badges = Achievements.all(QuestService.COURSE)
        text("★ $stars of ${3 * allQuests.size} stars  ·  ${progress.badges().size} of ${badges.size} badges  ·  ❄ ${progress.freezes()} of ${XpRules.MAX_FREEZES} streak freezes")
        val earned = badges.filter { it.id() in progress.badges() }
        if (earned.isNotEmpty()) text("Badges: " + earned.joinToString(", ") { it.name() }, muted = true)
        text(
            "Finish quests to earn XP. Each hint costs ${XpRules.HINT_COST} XP (you always keep at least half). Passing on the first try gives +${XpRules.FIRST_TRY_BONUS}, and +${XpRules.CLEAN_CODE_BONUS} more when the Coach finds no bugs. Stars: one for passing, one for no hints, one for clean code; replays can raise them. Clearing a level earns a streak freeze, which saves your streak on a day you miss. A level opens when the one before it is done.",
            muted = true,
        )
        state.saveProblem?.let { text(it) }
    }

    private fun kindColors(kind: QuestKind): Card = when (kind) {
        QuestKind.CODE -> Card(BuddyColors.CONCEPT_ACCENT, BuddyColors.CONCEPT_BACKGROUND)
        QuestKind.FIND_THE_BUG -> Card(BuddyColors.CRASH_ACCENT, BuddyColors.CRASH_BACKGROUND)
        QuestKind.PREDICT_OUTPUT -> Card(BuddyColors.PROGRESS_ACCENT, BuddyColors.PROGRESS_BACKGROUND)
        QuestKind.REFACTOR_RACE -> Card(BuddyColors.HELP_ACCENT, BuddyColors.HELP_BACKGROUND)
        QuestKind.BOSS -> Card(BuddyColors.CRASH_ACCENT, BuddyColors.HELP_BACKGROUND)
    }

    private fun questCard(quest: Quest, state: QuestState, levelOpen: Boolean): Card {
        val done = state.progress.isDone(quest.id())
        if (!levelOpen) {
            return Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip("Locked · ${quest.kind().label()}")
                title(quest.title())
                text("Opens when every quest in the level before it is done.", muted = true)
            }
        }
        if (done) {
            return Card(BuddyColors.GOOD_ACCENT, BuddyColors.GOOD_BACKGROUND).apply {
                chip("Done ${stars(maxOf(1, state.progress.stars(quest.id())))} · ${quest.kind().label()}")
                title(quest.title())
                if (quests.canShowAnotherWay(quest)) {
                    links("Open" to { quests.open(quest) }, "Show" to { quests.focus(quest) }, "See another way" to { quests.showAnotherWay(quest) })
                }
                else {
                    links("Open" to { quests.open(quest) }, "Show" to { quests.focus(quest) })
                }
            }
        }
        return kindColors(quest.kind()).apply { questDetails(quest, state, showProgram = false) }
    }

    /** The quest itself: story, goal, opened hints and its buttons. */
    private fun Card.questDetails(quest: Quest, state: QuestState, showProgram: Boolean) {
        val done = state.progress.isDone(quest.id())
        chip("${quest.kind().label()} · ${XpRules.baseXp(quest.kind())} XP")
        title(quest.title())
        text(plain(quest.story()), muted = true)
        section("YOUR GOAL", plain(quest.goal()))
        if (showProgram && !quest.kind().answersWithCode()) {
            section("THE PROGRAM", "Read it carefully. What does it print? Type your guess into ${QuestService.fileName(quest)}, one printed line per line.")
            example(quest.starter().trimEnd())
        }
        hintSections(quest, state)
        val started = quests.hasFile(quest)
        val links = buildList<Pair<String, () -> Unit>> {
            add((if (started) "Open" else "Start") to { quests.open(quest) })
            if (started) add(checkLabel(quest) to { quests.check(quest) })
            hintLink(quest, state, done)?.let(::add)
        }
        links(*links.toTypedArray())
    }

    private fun Card.hintSections(quest: Quest, state: QuestState) {
        val opened = state.progress.hintsOpened(quest.id()).coerceAtMost(quest.hints().size)
        for (k in 0 until opened) section("HINT ${k + 1} OF ${quest.hints().size}", plain(quest.hints()[k]))
    }

    private fun hintLink(quest: Quest, state: QuestState, done: Boolean): Pair<String, () -> Unit>? {
        if (state.progress.hintsOpened(quest.id()) >= quest.hints().size) return null
        val cost = if (done) "free" else "−${XpRules.HINT_COST} XP"
        return "Hint ($cost)" to { quests.hint(quest) }
    }

    private fun checkLabel(quest: Quest) = if (quest.kind().answersWithCode()) "Check my answer" else "Check my guess"

    /** The card on top: checking…, the result of the last check, or the quest itself (after Hint or Show). */
    private fun topCard(quest: Quest, state: QuestState): Card {
        if (state.running == quest.id()) {
            return Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip("Checking…")
                title("Checking ${quest.title()}…")
                text("Java Buddy is building your code and running the hidden tests. This takes a few seconds.", muted = true)
            }
        }
        val result = state.results[quest.id()] ?: return kindColors(quest.kind()).apply { questDetails(quest, state, showProgram = true) }
        return if (result.check.isPassed) passedCard(quest, result) else notYetCard(quest, result, state)
    }

    private fun passedCard(quest: Quest, result: QuestResult): Card = Card(BuddyColors.GOOD_ACCENT, BuddyColors.GOOD_BACKGROUND).apply {
        val check = result.check
        chip("Passed · ${check.passed()} of ${check.total()} tests")
        title("Quest complete: ${quest.title()}!")
        richText(check.message())
        val award = result.award
        if (award != null && award.stars() > 0) {
            text("${stars(award.stars())}  " + if (award.stars() == 3) "Three stars!" else "For more stars, solve it without hints and leave the Coach nothing to fix. Replays count.")
        }
        if (award != null && award.newBadges().isNotEmpty()) text("New badge: ${award.newBadges().joinToString(", ")}")
        if (award != null && award.usedFreezes() > 0) text("❄ A streak freeze saved your streak.", muted = true)
        if (award == null || award.alreadyDone()) {
            val best = if (award != null && award.bestStars() > 0) " Your best is ${stars(award.bestStars())}." else ""
            text("You'd already beaten this quest, so there's no new XP.$best Nice practice!", muted = true)
        }
        else {
            val lines = buildList {
                add("✓ +${award.questXp()} XP  quest" + if (award.hintsOpened() > 0) " (after ${pluralize(award.hintsOpened(), "hint")})" else "")
                if (award.firstTry() > 0) add("✓ +${award.firstTry()} XP  first try")
                if (award.cleanCode() > 0) add("✓ +${award.cleanCode()} XP  clean code (the Coach found no bugs)")
                if (award.streak() > 0) add("✓ +${award.streak()} XP  ${award.streakDays()}-day streak")
            }
            text("You earned ${award.total()} XP:")
            example(lines.joinToString("\n"))
        }
        val next = quests.nextQuest(quest)
        val passedLinks = buildList<Pair<String, () -> Unit>> {
            if (next != null) add("Next quest: ${next.title()}" to { quests.open(next) })
            if (quests.canShowAnotherWay(quest)) add("See another way" to { quests.showAnotherWay(quest) })
        }
        if (next == null) text("That's every open quest done. More levels are on the way!")
        if (passedLinks.isNotEmpty()) links(*passedLinks.toTypedArray())
    }

    /** Only reached through QuestService.showAnotherWay, which asks the engine's AnotherWay.reveal first. */
    private fun anotherWayCard(quest: Quest, view: AnotherWayView): Card = Card(BuddyColors.CONCEPT_ACCENT, BuddyColors.CONCEPT_BACKGROUND).apply {
        chip("Another way")
        title("Another way to solve ${quest.title()}")
        text("You've passed this quest, so here's a different solution. Both pass the same tests, and neither is \"the right one\": look for one idea worth borrowing.", muted = true)
        if (view.notes.isNotEmpty()) section("HOW IT DIFFERS FROM YOURS", view.notes.joinToString("\n") { "• " + plain(it) })
        example(view.code.trimEnd())
        links("Hide" to { quests.hideAnotherWay(quest) }, "Open my code" to { quests.open(quest) })
    }

    private fun notYetCard(quest: Quest, result: QuestResult, state: QuestState): Card {
        val check = result.check
        val card = if (check.status() == CheckResult.Status.COMPILE_ERROR || check.status() == CheckResult.Status.CRASHED) {
            Card(BuddyColors.CRASH_ACCENT, BuddyColors.CRASH_BACKGROUND)
        }
        else {
            Card(BuddyColors.HELP_ACCENT, BuddyColors.HELP_BACKGROUND)
        }
        return card.apply {
            chip(if (check.total() > 0) "${check.status().label()} · ${check.passed()} of ${check.total()} tests" else check.status().label())
            title("${quest.title()}: ${check.status().label().lowercase()}")
            richText(check.message())
            hintSections(quest, state)
            val links = buildList<Pair<String, () -> Unit>> {
                add("Check again" to { quests.check(quest) })
                if (check.line() > 0) add("Go to line ${check.line()}" to { quests.navigate(quest, check.line()) })
                else add("Open" to { quests.open(quest) })
                hintLink(quest, state, state.progress.isDone(quest.id()))?.let(::add)
            }
            links(*links.toTypedArray())
        }
    }

    /** ★★☆ for a quest's stars. */
    private fun stars(count: Int): String = "★".repeat(count.coerceIn(0, 3)) + "☆".repeat(3 - count.coerceIn(0, 3))

    override fun dispose() = Unit
}
