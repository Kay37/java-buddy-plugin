package dev.javabuddy.memory

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Memory tab shows: a picture, or why there isn't one. */
sealed class MemoryState {
    /** No debugging going on. */
    data object Idle : MemoryState()
    /** Debugging, but the program is running (not paused at a breakpoint). */
    data object Running : MemoryState()
    data class Paused(val snapshot: MemorySnapshot, val changed: Set<String>) : MemoryState()
    data class Unavailable(val reason: String) : MemoryState()
}

/**
 * Watches debug sessions. Each time the program pauses (a breakpoint, a step, or picking another
 * frame), reads the variables on the debugger's own thread and publishes a [MemorySnapshot].
 */
@Service(Service.Level.PROJECT)
class MemoryService(private val project: Project) : Disposable {
    private val _state = MutableStateFlow<MemoryState>(MemoryState.Idle)
    val state: StateFlow<MemoryState> = _state.asStateFlow()
    @Volatile private var previous: Map<String, String>? = null
    private var started = false
    private var shownThisSession = false

    fun start() {
        if (started) return
        started = true
        project.messageBus.connect(this).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
            override fun processStarted(debugProcess: XDebugProcess) = watch(debugProcess.session)
            override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
                if (currentSession == null) reset(MemoryState.Idle) else capture(currentSession)
            }
        })
        XDebuggerManager.getInstance(project).debugSessions.forEach(::watch)
    }

    private fun watch(session: XDebugSession) {
        shownThisSession = false
        val disposable = Disposer.newDisposable(this, "Java Buddy memory listener")
        val listener = object : XDebugSessionListener {
            override fun sessionPaused() = capture(session)
            override fun stackFrameChanged() = capture(session)
            override fun sessionResumed() {
                if (XDebuggerManager.getInstance(project).currentSession == session) _state.value = MemoryState.Running
            }
            override fun sessionStopped() {
                reset(MemoryState.Idle)
                Disposer.dispose(disposable)
            }
        }
        session.addSessionListener(listener, disposable)
        if (session.isSuspended) capture(session) else _state.value = MemoryState.Running
    }

    private fun reset(state: MemoryState) {
        previous = null
        _state.value = state
    }

    fun refresh() {
        XDebuggerManager.getInstance(project).currentSession?.let(::capture) ?: reset(MemoryState.Idle)
    }

    private fun capture(session: XDebugSession) {
        if (session != XDebuggerManager.getInstance(project).currentSession) return
        val process = (session.debugProcess as? JavaDebugProcess)?.debuggerSession?.process
        val frame = session.currentStackFrame as? JavaStackFrame
        val suspendContext = session.suspendContext as? SuspendContextImpl
        if (process == null) return publishProblem("The Memory Picture works with Java programs only.")
        if (frame == null || suspendContext == null) {
            _state.value = if (session.isSuspended) MemoryState.Unavailable("In the Debug window, click a method in the Frames list (the list of methods that are running) to see its variables.") else MemoryState.Running
            return
        }
        process.managerThread.schedule(object : SuspendContextCommandImpl(suspendContext) {
            override fun contextAction(suspendContext: SuspendContextImpl) {
                val state = try {
                    val snapshot = SnapshotBuilder().build(frame.stackFrameProxy)
                    val keys = snapshot.valueKeys()
                    val before = previous
                    val changed = if (before == null) emptySet() else keys.filter { (key, value) -> before[key] != value }.keys
                    previous = keys
                    MemoryState.Paused(snapshot, changed)
                }
                catch (e: Exception) {
                    LOG.info("Memory Picture couldn't read the frame", e)
                    MemoryState.Unavailable(
                        if (e.javaClass.simpleName.contains("AbsentInformation") || e.message?.contains("information", ignoreCase = true) == true)
                            "Java didn't keep the variable names for this code (it isn't compiled for debugging). Try a breakpoint in your own code."
                        else "Java Buddy couldn't read the variables here. Step once more and it will try again."
                    )
                }
                _state.value = state
                if (state is MemoryState.Paused && !shownThisSession) {
                    shownThisSession = true
                    ApplicationManager.getApplication().invokeLater({ showTabIfPanelOpen() }, project.disposed)
                }
            }
        })
    }

    private fun publishProblem(reason: String) {
        _state.value = MemoryState.Unavailable(reason)
    }

    /** On the first pause, switch to the Memory tab, but only if the learner already has Java Buddy open. */
    private fun showTabIfPanelOpen() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        if (!toolWindow.isVisible) return
        toolWindow.contentManager.findContent(TAB_NAME)?.let { toolWindow.contentManager.setSelectedContent(it) }
    }

    fun showPanel() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.show { toolWindow.contentManager.findContent(TAB_NAME)?.let { toolWindow.contentManager.setSelectedContent(it) } }
    }

    override fun dispose() = Unit

    companion object {
        const val TOOL_WINDOW_ID = "Java Buddy"
        const val TAB_NAME = "Memory"
        private val LOG = logger<MemoryService>()
    }
}
