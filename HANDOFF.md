# Hand-off — state as of 2026-10-18

Read `CLAUDE.md` first (plan, build commands, conventions, how the user likes to work, code map).

## Done

- Project set up 2026-10-18 from Git Dashboard's Maven setup; `ide-platform/pom.xml` also lists the
  `plugins/java/lib` jars. Git repo on `main`, remote `origin` = `Kay37/java-buddy-plugin` (private).
  First commit e7f1ab0 (whole project, including playground) is pushed to `origin/main`.
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

- **2. Concept Cards** (`concepts/`): built 2026-10-18 overnight. Loads cleanly, but **the user hasn't
  tried it yet**, and nobody has seen the popup on screen. Their design choices (2026-10-18): own popup
  (not IntelliJ's doc popup), core words + types (~40 cards), tab = current card on top + search + list.
  - Rest the mouse ~0.5 s on a Java word → blue card under it (topic chip, title, what it means,
    example with ✗/✓, "Good to know", "Open in Java Buddy panel"). Closes on click, Esc, typing, or
    moving to another word; a click keeps it closed until the mouse moves to another word.
  - Hover works on keywords, `< >` (generics), `[ ]` (arrays), the `( )` of a method (parameters) and
    the `@` of `@Override`. Not on `String` / `Override`: IntelliJ shows its own popup there, so those
    cards appear only in the tab (cursor on the word).
  - Concepts tab: card for the word under the text cursor (or last hovered / picked), then search and
    6 topic groups. Toggle: editor right-click → Java Buddy → Explain Java Words When Hovering.
  - "Methods" cards (static, void, return, parameters) written extra clear: the user's class covers
    static methods on 2026-10-19.

## How to try things in the playground

1. Test IDE: File → Open → `java-buddy-plugin\playground` → Trust Project. If asked for a JDK:
   Setup SDK → corretto-17. After a `pom.xml` change: Maven panel → Reload All Maven Projects.
2. `errors/src/main/java/playground/TryErrors.java`: bubbles under each `MISTAKE` line.
3. `crashes/.../TryCrash.java`: press ▶ next to main; change `WHICH` (1–5) for other crashes.
4. `concepts/.../TryConcepts.java`: hover Java words; runs fine with ▶. (Reload Maven once: new module.)

## Next steps

1. The user tries Concept Cards (steps above) and sends notes; fix them in one batch.
2. Practice Drills: **ask for the Pluralsight course name or module list first**, so drills follow it.
3. Progress Map, then Memory Picture.

## Ideas (not asked for yet)

- Explain javac errors from the Build window too (rules already hold javac wording as alternates).
- Explain failing JUnit test messages ("expected: <5> but was: <4>").
- Explain the "does not support the required jvm target" build message (JDK older than the
  project's Java version); the user hit it on day one.

## Known gotchas

- Swing can't draw colour emoji on Windows: use `AllIcons` or ✓ ✗ ▶ characters, not emoji.
- Long shell commands with nested quotes break the Bash tool here: put them in a scratchpad script.
- Run listeners must attach in `processStarting`; anything later can miss a fast program's output.
- Popups with wrapping text (`JTextArea`) need a width before they know their height: use
  `fitToWidth` in `ui/Cards.kt`.
