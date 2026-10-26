# Java Buddy — IntelliJ plugin

A free, friendly helper for people learning Java in IntelliJ. The user is a Java beginner taking a
Pluralsight course (about 3 months from 2026-10-18), and is the first user. Everything the plugin
says must make sense to a beginner: plain words, short sentences, a tiny example where it helps.
Visuals should pop, like Git Dashboard's (sibling folder `../git-dashboard-plugin`, same setup).

GitHub: `Kay37/java-buddy-plugin` (private). See `HANDOFF.md` for current state and next steps.

## Plan (user, 2026-10-18)

One plugin, five tools, built in this order, each as a tab in the Java Buddy side panel:
1. **Error Explainer**: plain-English bubbles under red errors, crash explanations after a run.
2. **Concept Cards**: hover a keyword (static, final, extends, generics…) for a short card + example.
3. **Practice Drills**: small exercises per course topic, checked by hidden tests (✓ / ✗ + why).
4. **Progress Map**: which Java concepts the user's code has used over time (from commits).
5. **Memory Picture**: boxes-and-arrows view of variables and objects while debugging. Built because
   the beginner-friendly "Java Visualizer" plugin was abandoned in 2022 (no dark mode, breakpoint bug)
   and the maintained "Visual Debugger" uses UML diagrams aimed at professionals.

## Working with this user

- **Maven, not Gradle.** The user chose this knowingly; don't suggest switching.
- **The user's classes use JDK 17** (Amazon Corretto 17). The playground, drills and code examples
  target Java 17; don't use newer language features in them. (The plugin itself still builds with JDK 21.)
- **Explain in plain, ELI5 terms, in the chat itself.** Don't bury explanations between tool calls.
- **Plugin wording level:** match `errors/Explanations.kt` (approved 2026-10-18). Short sentences,
  everyday words ("made", not "declared"; "put (int) in front", not "cast"), and a few words in
  brackets for any Java term that can't be avoided.
- **Commit and push only when the user approves.** The usual loop: build → relaunch the test IDE →
  the user tries it → they say "commit" → commit → they say "push" → push.
- User-facing text (notifications, dialogs, tooltips) is plain English. Explain what happened and
  what to do; never dump raw git output when a friendly message is possible.

## Working rules (token and effort savers)

Adopted from the user's DHARMA Initiative project (2026-10-18), trimmed to what fits a solo plugin.

- **Suggest a model and effort** at the start of each new request, in one line, with why. The user
  switches them in the app's model picker. When in doubt go one step lower; step up if work comes back wrong.

  | Kind of work | Model | Effort |
  |---|---|---|
  | New feature design, tricky IntelliJ API / threading bugs | Opus 5.5 | high |
  | Feature from a clear spec, UI polish passes | Opus 5.5 | medium |
  | Routine code, docs, HANDOFF/CLAUDE.md updates, wrap-up | Sonnet 5.5 | medium |
  | Look-ups, renames, one-line fixes, relaunching the test IDE | Haiku 5.5 or Sonnet 5.5 | low |

- **Literal changes.** Repeat each request back in one line, then change only what the user named.
  Ideas go in a "you might also want" line; never build them unasked. If the words could mean two
  things, ask one short question.
- **Lean reading.** Search first (Grep), then read only the lines needed. Never read `idea.log`,
  `ide-platform/pom.xml` or anything in `target/` whole — grep them. For IntelliJ APIs use `javap`
  on the one class (below), not broad jar listings.
- **Bounded check passes.** Build fully, relaunch once (`scripts/restart-test-ide.ps1`), let the user
  try it, then fix everything they found in one batch. One message with five notes is one round.
- **Ask for the best tool early.** If a tool would make a task faster or better, say so at the start
  (what it is, size, free, why). Free and open-source only; installing anything needs a yes.
- **Test before writing "broken".** Before a note says something doesn't work, rerun it once and try
  the documented fix; the note says what was tried.
- **Windows habits.** Scripts longer than a line or with nested quotes go in a file in the scratchpad,
  never inline. Builds and the IDE restart run with `run_in_background`; foreground `sleep` is blocked.
- **Helper agents** only for research or review with a clear end point; never for UI the user must see.
- **One fact, one home.** `CLAUDE.md` = rules and setup that always hold; `HANDOFF.md` = where things
  stand and what's next (rewritten, not appended, under 8 KB). Elsewhere, point to the file.
- **Session budget: about 250k tokens.** The status line shows the size (`scripts/context.mjs`) and a
  hook warns past 250k. Past it: finish the item in hand, run `/wrap-up`, and give the user a one-line
  prompt for a fresh session. A new ask past 250k gets that prompt back instead of new work, unless
  the user says "keep going". One session per feature or day. (The hooks only run when the session is
  opened in this folder, not in `claude_general_PC`.)
- **End every run with three headings:** **Blocked on me** (what only the user can do, step by step),
  **Changed**, **Found**. End a session with `/wrap-up`.

## Build & run

There is no `mvn` on PATH and no `JAVA_HOME`; use IntelliJ's bundled Maven with JDK 21:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
MVN="/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.3/plugins/maven-plugin/lib/maven3/bin/mvn"
"$MVN" -B -ntp -q compile                    # quick compile check
"$MVN" -B -ntp -q package exec:exec@run-ide  # build, install into sandbox, launch test IDE
```

Show only real compile errors:
`... compile 2>&1 | grep -v 'starting the daemon' | grep -E '\[ERROR\] file|e: |w: '`

- The test IDE ("sandbox") lives in `~/.idea-sandbox/java-buddy` (config/system/plugins/log).
  Check `log/idea.log` for `Loaded custom plugins: Java Buddy` and for `ERROR` / `dev.javabuddy`.
- **Restarting the test IDE: never close "all idea64 except X".** The user's own IntelliJ runs
  alongside it (this went wrong once). Only close the PID in
  `~/.idea-sandbox/java-buddy/system/.pid`, and only if that process is `idea64` and started
  within a couple of minutes of the file's write time. If unsure, ask the user to close it.
  **Use `scripts/restart-test-ide.ps1`** (run in the background): it builds first, applies exactly
  that check (exit 2 = unsafe, ask the user), relaunches, and reports the load line and any plugin
  errors from this launch. `-NoBuild` skips the build.
  `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restart-test-ide.ps1`
- `ide-platform/pom.xml` is **generated**: it lists the local IDE's jars as system-scoped deps.
  After an IntelliJ update run `scripts/generate-ide-platform-pom.ps1 -IdeaHome "<new install>"`.
- Target: IntelliJ 2026.2.3 (build 262), JBR 25, Kotlin 2.4 (`kotlin.version` 2.4.21, jvmTarget 21).
- `sandbox/idea64.vmoptions` adds `-XX:-UseCompactObjectHeaders` after a JBR 25 internal crash
  ("Node hash code has changed"); `debug-ide` adds a debugger on localhost:5005.

## Finding IntelliJ APIs

There are no sources; inspect the installed jars with `javap` (Windows paths via `cygpath -w`):

```bash
IDE="/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.3"
CP="$(for j in "$IDE/plugins/vcs-git/lib/vcs-git.jar" "$IDE"/lib/intellij.platform.vcs*.jar; do printf '%s;' "$(cygpath -w "$j")"; done)"
"/c/Program Files/Java/jdk-21/bin/javap.exe" -cp "$CP" git4idea.branch.GitBrancher
```

To find which jar holds a class: loop over `"$IDE"/lib/*.jar` with `unzip -l | grep 'path/Name.class'`.

## Code map (`src/main/kotlin/dev/javabuddy/`)

| Package | What's there |
|---|---|
| (root) | `JavaBuddy.kt` (`JavaBuddyScope`, `notifyUser`, `JavaBuddySettings`), `JavaBuddyToolWindowFactory.kt` (side panel tabs, startup, toggle actions) |
| `errors/` | `Explanations.kt` (rule tables: `COMPILE_RULES` match IntelliJ's own error wording, with javac's as alternates; `CRASH_RULES`; `LAUNCH_RULES`), `ErrorExplainer` (project service: reads the editor's ERROR highlights after each analysis, "did you mean", bubbles, crash state), `ErrorBubbleRenderer` (block inlay under the line), `CrashWatcher` (`ExecutionListener`: parses the run's stack trace, finds the first frame in project code), `ErrorsPanel` (the Errors tab) |
| `concepts/` | `Concepts.kt` (the cards: `Concept`, `Topic`, `Concepts.ALL`, `BY_KEYWORD`, plus `BY_CLASS_NAME` and `BY_CALL` for names like StringBuilder or substring), `ConceptFinder.kt` (`conceptAt`: which card the PSI token at an offset opens; names and constructor/getter/overloaded method declarations open panel-only cards), `ConceptCards` (project service: hover popup after 0.5 s, caret following, `current` card), `ConceptsPanel` (the Concepts tab) |
| `drills/` | `Drills.kt` (`Topic` with its workbook, `WORKBOOKS`, `Check.Returns`/`Prints` (`onClass = false` for object drills like `new Pet(...)`; `steps(...)` for set-up-then-check), the 59 `DRILLS` with starter code, sorted by topic), `DrillChecker` (writes the learner's code + a generated Java 8 test class to a temp dir, `javac` + `java` from the project JDK, 5 s run limit, parses `@@JB` marker lines), `DrillService` (scratch files, check, passed set in `javaBuddy.drills.passed`), `DrillsPanel` (the Drills tab), `DrillNotificationProvider` (the "Check my answer" bar on drill files) |
| `progress/` | `Ideas.kt` (47 `IDEAS`: regex per added line, quotes/comments stripped; links to a concept card and drill topic; `methodMadeIn` lets the scanner spot overloading, the same method name made twice in one file per commit), `ProgressScanner` (`git log -p` per repo, first use per idea, `suggestFolders`), `ProgressService` (open project's VCS roots + folders in `javaBuddy.progress.folders`), `ProgressPanel` (Progress tab, `Tile`) |
| `memory/` | `MemorySnapshot.kt` (`SnapshotBuilder`: JDI frame → rows/boxes; Strings, boxed numbers and enums inline; ArrayList/HashMap read from their fields; Java's own objects not opened), `MemoryService` (`XDebugSessionListener`, builds on the debugger manager thread via `SuspendContextCommandImpl`, "changed" keys), `MemoryPicture` (paints boxes and arrows), `MemoryPanel` (Memory tab) |
| `ui/` | `Cards.kt`: `Card` (rounded, accent stripe; chip/title/section/example/links/custom/progressBar), `CardList`, `BuddyColors`, `emptyState`, `heading`, `fitToWidth` (sizes a popup to its wrapped text) |

Conventions: background work uses the project `JavaBuddyScope` coroutine scope; user messages go
through `notifyUser(...)`; settings are `PropertiesComponent` keys prefixed `javaBuddy.`. Error
wording comes from IntelliJ's bundles (`messages/JavaCompilationErrorBundle.properties` in
`plugins/java/lib/modules/intellij.java.codeserver.highlighting.jar`); check new rules against it.
Rules can be tested without the IDE: `jshell` with `target/classes` and `lib/util-8.jar` (Kotlin stdlib)
on the class path, calling `ExplanationsKt.explainCompileError("...")`.

`playground/` is a small Maven project to open in the test IDE: module `errors` has deliberate
mistakes (bubbles), module `crashes` has programs that crash on purpose (run them). Separate modules
so the broken code doesn't stop the crash programs from building. `concepts` is for hovering words,
`memory` (`TryMemory.java`) for debugging with the Memory tab.

Testing without the IDE (scripts in the session scratchpad, not committed): run Java against
`target/classes` + `"$IDE/lib/*"` with **the IDE's own `jbr/bin/java.exe`** (the IDE jars need Java 25).
Drills: call `DrillChecker.INSTANCE.check(drill, code, corretto17Home)` with a right answer and the
starter for every drill. Visuals: build `MemoryPicture` / `Card`s and paint them into a PNG (headless,
plus `--add-exports=java.desktop/sun.swing=ALL-UNNAMED`; `Card.example` needs a running IDE).

## Environment notes

- Windows 11, project inside OneDrive. Git warns about LF→CRLF on every commit; that's normal.
- `gh` CLI is signed in as Kay37.
