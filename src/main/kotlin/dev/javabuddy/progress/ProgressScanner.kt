package dev.javabuddy.progress

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime

/** One commit in one repository, with the ideas its added Java lines use. */
data class CommitIdeas(val repo: String, val hash: String, val date: LocalDate, val message: String, val ideas: Set<String>)

/** The first time an idea showed up in the learner's commits, and how many commits used it. */
data class IdeaUse(val idea: Idea, val first: CommitIdeas, val commits: Int)

data class ProgressMap(
    val uses: Map<String, IdeaUse>,
    val commits: List<CommitIdeas>,
    val repos: List<String>,
    val problems: List<String>,
    /** Folders that look like they hold more Java projects, to offer with one click. */
    val suggestions: List<String> = emptyList(),
)

/**
 * Reads the learner's git history: every commit's added lines in .java files, matched against
 * [IDEAS]. Runs `git` from the PATH, one repository at a time.
 */
object ProgressScanner {
    private const val HEADER = "@@JBCOMMIT"
    private const val TIMEOUT_MS = 60_000
    private const val MAX_REPOS = 60

    /** Git repositories at [folder] or up to three folders below it (course folders hold one repo per exercise). */
    fun findRepos(folder: File, depth: Int = 3): List<File> {
        if (!folder.isDirectory) return emptyList()
        if (File(folder, ".git").exists()) return listOf(folder)
        if (depth == 0) return emptyList()
        val children = folder.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && file.name !in SKIPPED } ?: return emptyList()
        return children.sortedBy { it.name.lowercase() }.flatMap { findRepos(it, depth - 1) }.take(MAX_REPOS)
    }

    private val SKIPPED = setOf("node_modules", "target", "build", "out", "AppData")

    /**
     * Folders in the home folder that look like they hold course work: two or more git projects
     * with Java files. Offered with one click, so the learner doesn't have to know where to look.
     */
    fun suggestFolders(home: File, already: Collection<String>, projectRoots: Collection<File>): List<File> {
        val taken = (already.map { File(it).canonicalPath } + projectRoots.map { it.canonicalPath }).toSet()
        val children = home.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && file.name !in SKIPPED } ?: return emptyList()
        return children.filter { folder ->
            folder.canonicalPath !in taken &&
                findRepos(folder, depth = 2).count { repo -> taken.none { repo.canonicalPath == it } && hasJava(repo) } >= 2
        }.take(3)
    }

    private fun hasJava(repo: File): Boolean =
        repo.walkTopDown().maxDepth(6).onEnter { it.name !in SKIPPED && !it.name.startsWith(".") }.any { it.isFile && it.name.endsWith(".java") }

    fun scan(repos: List<File>): ProgressMap {
        val commits = mutableListOf<CommitIdeas>()
        val problems = mutableListOf<String>()
        for (repo in repos.distinctBy { it.canonicalPath }) {
            try {
                commits += scanRepo(repo)
            }
            catch (e: Exception) {
                problems += "${repo.name}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        commits.sortWith(compareBy({ it.date }, { it.repo }))
        val uses = linkedMapOf<String, IdeaUse>()
        for (idea in IDEAS) {
            val using = commits.filter { idea.id in it.ideas }
            if (using.isNotEmpty()) uses[idea.id] = IdeaUse(idea, using.first(), using.size)
        }
        return ProgressMap(uses, commits, repos.map { it.name }, problems)
    }

    private fun scanRepo(repo: File): List<CommitIdeas> {
        val command = GeneralCommandLine(
            "git", "-c", "core.quotepath=off", "log", "--reverse", "--no-merges", "--date=iso-strict",
            "--format=$HEADER%x1f%H%x1f%ad%x1f%s", "-p", "--unified=0", "--no-color", "--no-ext-diff", "--", "*.java",
        ).withWorkingDirectory(repo.toPath()).withCharset(StandardCharsets.UTF_8)
        val output = CapturingProcessHandler(command).runProcess(TIMEOUT_MS, true)
        if (output.isTimeout) error("git took too long")
        if (output.exitCode != 0) {
            // A brand-new repository with no commits yet is fine: nothing to show from it.
            if ("does not have any commits" in output.stderr) return emptyList()
            error(output.stderr.lineSequence().firstOrNull { it.isNotBlank() } ?: "git failed")
        }
        val result = mutableListOf<CommitIdeas>()
        var current: CommitIdeas? = null
        val ideas = mutableSetOf<String>()
        // Method names made in the current file of this commit: the same name twice is overloading.
        val methodsInFile = mutableSetOf<String>()
        fun finish() {
            current?.let { if (ideas.isNotEmpty()) result += it.copy(ideas = ideas.toSet()) }
            ideas.clear()
            methodsInFile.clear()
        }
        for (line in output.stdout.lineSequence()) {
            if (line.startsWith(HEADER)) {
                finish()
                val parts = line.removePrefix(HEADER).split('\u001F')
                val date = runCatching { OffsetDateTime.parse(parts[2]).toLocalDate() }.getOrElse { LocalDate.now() }
                current = CommitIdeas(repo.name, parts[1], date, parts.getOrElse(3) { "" }, emptySet())
            }
            else if (line.startsWith("+++")) {
                methodsInFile.clear()
            }
            else if (line.startsWith("+")) {
                val added = line.substring(1)
                ideasIn(added).forEach { ideas += it.id }
                methodMadeIn(added)?.let { name -> if (!methodsInFile.add(name)) ideas += "overloading" }
            }
        }
        finish()
        return result
    }
}
