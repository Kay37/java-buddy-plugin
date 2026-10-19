# Hand-off — state as of 2026-10-18

Read `CLAUDE.md` first (plan, build commands, conventions, how the user likes to work, code map).

## Done

- Project set up 2026-10-18 from Git Dashboard's Maven setup; `ide-platform/pom.xml` also lists the
  `plugins/java/lib` jars. Git repo on `main`, remote `origin` = `Kay37/java-buddy-plugin` (private).
  First commit made 2026-10-18 (whole project, including playground); **not pushed yet**.
- **1. Error Explainer** (`errors/`): tried by the user in the test IDE and approved.
  - Yellow bubble under each line with a red error (max 5 per file; not under a line edited in the
    last 2 s): bold title + "How to fix". Click → Java Buddy panel. "Did you mean 'x'?" for unknown names.
  - Run crashes: balloon "Your program crashed (line N of File.java)" with Explain more / Go to; red
    crash card on top of the Errors tab. Also "Main method not found" / "Could not find main class".
  - Errors tab: one card per error (line chip, what it means, how to fix, ✗/✓ example, Go to line);
    grey card for errors without a rule yet; green "No errors" card. Toggles: editor right-click → Java Buddy.
  - 41 compile rules, 14 crash rules, 2 launch rules.
- Fixes after the user's try-out:
  - `CrashWatcher` listens from `processStarting`, not `processStarted`: a short program could crash
    and exit before `processStarted` fired, so no crash was shown. It logs `Run ended (exit N, …)` per run.
  - All explanation texts reworded a step simpler (user: "a tad" too much jargon). Patterns unchanged.
    The current level is approved: keep new text at this level (see CLAUDE.md).
  - Playground targets Java 17 (the user's classes use Corretto 17).

## How to try things in the playground

1. Test IDE: File → Open → `java-buddy-plugin\playground` → Trust Project. If asked for a JDK:
   Setup SDK → corretto-17. After a `pom.xml` change: Maven panel → Reload All Maven Projects.
2. `errors/src/main/java/playground/TryErrors.java`: bubbles under each `MISTAKE` line.
3. `crashes/.../TryCrash.java`: press ▶ next to main; change `WHICH` (1–5) for other crashes.

## Next steps

1. Push, when the user says "push".
2. Concept Cards (tool 2), in a fresh session.
3. Practice Drills: **ask for the Pluralsight course name or module list first**, so drills follow it.
4. Progress Map, then Memory Picture.

## Ideas (not asked for yet)

- Explain javac errors from the Build window too (rules already hold javac wording as alternates).
- Explain failing JUnit test messages ("expected: <5> but was: <4>").
- Explain the "does not support the required jvm target" build message (JDK older than the
  project's Java version); the user hit it on day one.

## Known gotchas

- Swing can't draw colour emoji on Windows: use `AllIcons` or ✓ ✗ ▶ characters, not emoji.
- Long shell commands with nested quotes break the Bash tool here: put them in a scratchpad script.
- Run listeners must attach in `processStarting`; anything later can miss a fast program's output.
