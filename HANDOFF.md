# Hand-off — state as of 2026-10-19 (after an overnight run)

Read `CLAUDE.md` first (plan, build commands, conventions, how the user likes to work, code map).

## Where things stand

All five planned tools are built, committed and pushed to `origin/main` (`Kay37/java-buddy-plugin`).
Each is a tab in the Java Buddy side panel: **Errors, Concepts, Drills, Progress, Memory**.
The user approved overnight commits and pushes ("Feel free to commit and push until you're done").

| Tool | State |
|---|---|
| 1. Error Explainer | Tried and approved by the user (wording simplified once, at their request). |
| 2. Concept Cards | Built by a separate session. Loads cleanly; **not tried yet**, popup never seen on screen. |
| 3. Practice Drills | 59 drills covering Workbooks 1 and 2 (25 Workbook 2 drills added 2026-10-26); every drill tested headless with a right answer (passes) and its starter (fails sensibly). **Not tried in the IDE yet.** |
| 4. Progress Map | Tested headless on `C:\Users\kimba\pluralsight`: 8 repos, 14 commits, ideas and dates correct. **Not tried in the IDE yet.** |
| 5. Memory Picture | Picture tested by painting a sample to PNG (light and dark). Debugger hookup **not tried yet**: nobody has debugged with it. |

Nothing in tools 2–5 has been seen running in the IDE: no screen access overnight. The test IDE
loads the plugin with no errors in `idea.log`.

## Practice Drills (details)

- Follows the user's Pluralsight course, grouped under big "Workbook 1" / "Workbook 2" headings, topics
  in workbook order. Workbook 1 (Intro to Java): Variables and math (6), Scanner and printf (3),
  if/else (3), Static methods (8), switch (2). Workbook 2 (2a: Strings, Loops and Arrays, PDF in the
  user's Downloads): Text (12), Text into numbers and dates (4), StringBuilder (2), Classes and objects (4),
  Overloading (2), Loops (7), Arrays (6). Many Workbook 2 drills mirror its exercises (full names,
  high score, theater, address builder, cell phone, test statistics) and the user's test mistakes
  (substring end, do/while runs once, `b = a` shares the array, `split("\\|")`).
- Start opens a scratch file (Scratches and Consoles, not the course repos). The bar on top has
  Check my answer, Hint, Start again (asks first), and Next drill once it's done.
- The check compiles with the project's JDK (falls back to any known JDK with javac). Results: ✓/✗ per
  test with expected/got, a "why" hint for the first failure, crash explanations (reuses
  `explainCrash`), compile errors (reuses `explainCompileError`; javac's "cannot find symbol" is
  turned into IntelliJ's wording), "Write the X method" when the method doesn't exist yet.

## Progress Map (details)

- Reads `git log -p` for `.java` files in the open project's VCS roots plus added folders. 47 ideas in
  8 groups; click a tile for first use (date, project, commit message) and links to its concept card
  and a drill. When no folder is added, it suggests folders in the home folder that hold 2+ Java git
  projects; for this user that's `C:\Users\kimba\pluralsight` (one click: "Add").

## Memory Picture (details)

- Debug a program, pause at a breakpoint: the tab draws variables (left) and arrays, lists, maps and
  objects (right) with arrows; values changed since the last step are yellow. Strings, numbers and
  enums are shown inside the box. Up to 30 boxes, 20 cells each. On the first pause it switches to the
  Memory tab, but only if the Java Buddy panel is already open.

## How to try everything (give the user these steps)

1. Test IDE: open `java-buddy-plugin\playground` (JDK: corretto-17). Maven panel → **Reload All Maven
   Projects** (new modules `concepts` and `memory`).
2. Errors: `errors/.../TryErrors.java`. Crashes: `crashes/.../TryCrash.java`, press ▶.
3. Concepts: `concepts/.../TryConcepts.java`, rest the mouse on `static`, `int`, `return`, the `( )` of `twice(5)`.
4. Drills tab: Start "Your first static method", write it, press Check my answer. Try a wrong answer too.
5. Progress tab: press Add next to `C:\Users\kimba\pluralsight`, then click a few tiles.
6. Memory: `memory/.../TryMemory.java`, red dot on the "RED DOT HERE" line, Debug main, open the
   Memory tab, press F8 a few times.

## Next steps

1. The user tries all of the above and sends notes; fix them in one batch, then commit and push on their yes.
2. Things worth watching in that try-out: the hover popup's position near the screen edge; Tile wrap
   in a narrow panel; the drill bar in scratch files; Memory Picture on a real debugger pause
   (JDI access on the manager thread is the riskiest code).

## Ideas (not asked for yet)

- Explain javac errors from the Build window too (rules already hold javac wording as alternates).
- Explain failing JUnit test messages ("expected: <5> but was: <4>").
- Explain the "does not support the required jvm target" build message (the user hit it on day one).
- More drills as the course moves on (ArrayList, file I/O, inheritance).

## Known gotchas

- Swing can't draw colour emoji on Windows: use `AllIcons` or ✓ ✗ ▶ characters, not emoji.
- Long shell commands with nested quotes break the Bash tool here: put them in a scratchpad script.
- Run listeners must attach in `processStarting`; anything later can miss a fast program's output.
- Popups with wrapping text (`JTextArea`) need a width before they know their height: use `fitToWidth`.
- Kotlin names `layout`/`layout()` clash with `java.awt.Container` members inside a JComponent.
- Headless tests need the IDE's `jbr` Java (IDE jars are class version 69).
- A session started from another one won't commit on a relayed go-ahead; the parent commits.
