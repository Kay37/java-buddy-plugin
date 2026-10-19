# Java Buddy

An IntelliJ plugin for people learning Java. It explains errors and crashes in plain English, right
under your code, with how to fix them and a small example. More learning tools are on the way:
concept cards, practice drills, a progress map and a picture of your program's memory.

## Install

Build it (below), then in IntelliJ: Settings → Plugins → ⚙ → Install Plugin from Disk, and pick
`target/java-buddy-<version>.zip`.

## Building with Maven

JetBrains only publishes a Gradle plugin for building IntelliJ plugins, so this project compiles
against the IntelliJ installed on this machine instead. `ide-platform/pom.xml` is a **generated**
parent POM listing the IDE's jars; after updating IntelliJ, regenerate it:

```powershell
.\scripts\generate-ide-platform-pom.ps1 -IdeaHome "C:\Program Files\JetBrains\IntelliJ IDEA <version>"
```

| Command | What it does |
| --- | --- |
| `mvn package` | Builds `target/java-buddy-<version>.zip` and installs it into the test IDE. |
| `mvn package exec:exec@run-ide` | Builds, then opens a separate test IntelliJ with the plugin. |
| `mvn package exec:exec@debug-ide` | Same, with a debugger port on `localhost:5005`. |

The test IDE keeps its own settings in `%USERPROFILE%\.idea-sandbox\java-buddy`, separate from your
normal IntelliJ. `playground/` is a small Java project with mistakes and crashes to try it on.
