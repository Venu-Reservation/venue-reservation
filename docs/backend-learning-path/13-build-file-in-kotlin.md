# Story 13: Write the build file in Kotlin

**Kind:** Build · **Size:** S · **Time:** 2 to 3 hours

## In plain words

`build.gradle` is the project's recipe: which libraries to download, which Java version to use,
and how to run the tests. Ours is written in **Groovy**, an older language Gradle also understands.
Gradle can read the same recipe written in **Kotlin** instead, in a file called
`build.gradle.kts`. Most new Spring Boot projects use the Kotlin version, and it has a real
advantage for us: IntelliJ understands it fully, so it can autocomplete and point out a mistake
while you type, instead of when the build fails.

Today you'll translate the file, line by line. You won't add or remove anything: the recipe stays
the same, only the language changes. That makes it a good way to read every line of the build
and learn what it does.

## New words

- **Gradle**: the tool that builds our app: it downloads libraries, compiles the code and runs the
  tests. `./gradlew` is a small script that runs the right Gradle version for this project.
- **Plugin**: an add-on for Gradle. `org.springframework.boot` teaches Gradle how to run and
  package a Spring Boot app; `kotlin("jvm")` teaches it how to compile Kotlin.
- **Groovy / Kotlin DSL**: the two languages a Gradle file can be written in. *DSL* means
  "a small language for one job". `build.gradle` is Groovy, `build.gradle.kts` is Kotlin.
- **`settings.gradle`**: a tiny companion file that gives the project its name.

## Before you start (20 minutes)

- Gradle's "Migrating build logic from Groovy to Kotlin", the sections **Prepare your Groovy scripts**
  and **Applying plugins**: <https://docs.gradle.org/current/userguide/migrating_from_groovy_to_kotlin_dsl.html>

## Steps

1. Update `main` and create a branch: `git checkout main`, `git pull`,
   `git checkout -b feature/build-file-in-kotlin`.
2. Create a new file `build.gradle.kts` next to `build.gradle`. Leave the old one in place for now;
   you'll copy from it.
3. Translate it section by section. The rules are few:
   - Text uses **double quotes**: `'com.venue'` becomes `"com.venue"`.
   - Function calls need **brackets**: `implementation 'a:b'` becomes `implementation("a:b")`.
   - Plugins: `id 'org.springframework.boot' version '4.1.1'` becomes
     `id("org.springframework.boot") version "4.1.1"`, and the Kotlin ones have a short form:
     `id 'org.jetbrains.kotlin.jvm' version '2.3.21'` becomes `kotlin("jvm") version "2.3.21"`.
   - Setting a value uses `=`: `group = "com.venue"`.
   - The test block `tasks.named('test') { useJUnitPlatform() }` becomes
     `tasks.withType<Test> { useJUnitPlatform() }`.
   - `freeCompilerArgs.addAll '-Xjsr305=strict', '-Xannotation-default-target=param-property'`
     becomes `freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")`.
4. Remove the `//` at the end of some dependency lines: they were left over and do nothing.
5. Do the same for `settings.gradle`: create `settings.gradle.kts` containing
   `rootProject.name = "reservation"`.
6. Delete `build.gradle` and `settings.gradle`. Gradle reads only one of each, and having both
   would be confusing.
7. In IntelliJ, click the Gradle elephant icon ("Sync"). If a line is red, IntelliJ will tell you
   what it expected; that's the advantage of the Kotlin version.
8. Run `./gradlew test` and `./gradlew bootRun`, and check `/actuator/health` in Postman.
9. Commit, push, open a pull request, and answer the questions below in it.

## How you know it works

- `ls` shows `build.gradle.kts` and `settings.gradle.kts`, and no `build.gradle` or `settings.gradle`.
- `./gradlew test` ends with `BUILD SUCCESSFUL`, and the app starts as before.
- Your list of dependencies is exactly the same as before, just written differently. Check with
  `./gradlew dependencies --configuration runtimeClasspath | head -40` before and after (run it on
  `main` first and save the output).

## Check your understanding

Answer in the pull request, in your own words:

1. What does the `plugins { }` block do, and what does the `dependencies { }` block do?
2. Pick one dependency from your file. What does our app use it for?
3. Why is it useful that IntelliJ underlines a mistake in `build.gradle.kts` while you type?

## Peek at the answer

On `feature/adding_db_flyway`, open `build.gradle.kts` and `settings.gradle.kts`. The shape will
match yours. The finished file has more in it (security, test containers, a block called
`allOpen`), because later stories add those: you'll meet each one when it's needed. Don't add
them now.

## Stuck?

- **"Unresolved reference: kotlin"** in the plugins block: check you wrote `kotlin("jvm")` with
  brackets and double quotes.
- **IntelliJ still shows the old file's errors**: click the Gradle elephant icon to sync, or
  File → Invalidate Caches → Restart.

**Pull request title:** `story 13: build file in Kotlin`
