package dev.javabuddy.progress

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.application.readAction
import dev.javabuddy.JavaBuddyScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ProgressState(
    val map: ProgressMap? = null,
    val scanning: Boolean = false,
    val selected: String? = null,
    val folders: List<String> = emptyList(),
)

/**
 * Builds the Progress Map from the open project's git history plus any folders the learner added
 * (for example the folder that holds all their course exercises).
 */
@Service(Service.Level.PROJECT)
class ProgressService(private val project: Project) {
    private val _state = MutableStateFlow(ProgressState(folders = ProgressFolders.get()))
    val state: StateFlow<ProgressState> = _state.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        _state.update { it.copy(scanning = true) }
        job = project.service<JavaBuddyScope>().cs.launch {
            val projectRoots = readAction {
                val vcsRoots = ProjectLevelVcsManager.getInstance(project).allVcsRoots.map { File(it.path.path) }
                vcsRoots.ifEmpty { listOfNotNull(project.basePath?.let(::File)) }
            }
            val map = withContext(Dispatchers.IO) {
                val repos = projectRoots.flatMap { ProgressScanner.findRepos(it, depth = 1) } +
                    ProgressFolders.get().flatMap { ProgressScanner.findRepos(File(it)) }
                ProgressScanner.scan(repos)
            }
            _state.update { it.copy(map = map, scanning = false, folders = ProgressFolders.get()) }
        }
    }

    fun select(ideaId: String?) = _state.update { it.copy(selected = if (it.selected == ideaId) null else ideaId) }

    fun addFolder(path: String) {
        ProgressFolders.set((ProgressFolders.get() + path).distinct())
        _state.update { it.copy(folders = ProgressFolders.get()) }
        job?.cancel()
        refresh()
    }

    fun removeFolder(path: String) {
        ProgressFolders.set(ProgressFolders.get() - path)
        _state.update { it.copy(folders = ProgressFolders.get()) }
        job?.cancel()
        refresh()
    }

    companion object {
        const val TAB_NAME = "Progress"
    }
}

/** Extra folders to read, remembered IDE-wide (a course folder is the same for every project). */
object ProgressFolders {
    private const val KEY = "javaBuddy.progress.folders"

    fun get(): List<String> =
        PropertiesComponent.getInstance().getValue(KEY).orEmpty().split('\n').filter { it.isNotBlank() }

    fun set(folders: List<String>) = PropertiesComponent.getInstance().setValue(KEY, folders.joinToString("\n"))
}
