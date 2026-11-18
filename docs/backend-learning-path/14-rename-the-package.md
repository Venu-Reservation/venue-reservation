# Story 14: Rename the package to `com.venue`

**Kind:** Build · **Size:** S · **Time:** 2 hours

## In plain words

Every Kotlin file starts with a line like `package com.venue.reservation.venues`. A **package**
is like a folder address: it says where the file lives and keeps names from clashing. Ours all
start with `com.venue.reservation`, which reads as "the reservation part of the venue project",
but the whole project *is* the reservation app. The extra `reservation` level makes every name
longer without telling us anything.

Today you'll move everything up one level, to `com.venue`, so `com.venue.reservation.venues`
becomes `com.venue.venues`, and rename the main class from `ReservationApplication` to
`VenueApplication`. IntelliJ does the hard part for you, so the real lesson is *why* the
location of that main class matters to Spring Boot.

## New words

- **Package**: the address at the top of a Kotlin file. It matches the folders the file is in:
  `com/venue/venues/Venue.kt` has `package com.venue.venues`.
- **Import**: a line like `import com.venue.venues.Venue` that lets one file use a class from
  another package.
- **`@SpringBootApplication`**: the label on our main class. It tells Spring Boot to start here
  and to look for our controllers, services and repositories **in this package and every package
  below it**.
- **Refactor**: changing how code is organised without changing what it does. IntelliJ's
  *Refactor* menu can rename and move things and update every file that refers to them.

## Before you start (15 minutes)

- Kotlin "Packages and imports", the first two sections: <https://kotlinlang.org/docs/packages.html>
- Spring Boot "Structuring Your Code", the "Locating the Main Application Class" section:
  <https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html>

## Steps

1. Update `main` and create a branch: `git checkout -b feature/rename-package`.
2. **Move the code up a level.** In IntelliJ's project view, open `src/main/kotlin/com/venue/reservation`.
   Select the `venues` and `reservations` folders and `ReservationApplication.kt`, press F6
   (Refactor → Move), and choose `com.venue` as the destination. IntelliJ changes the `package`
   lines and every `import` for you.
3. Do the same in `src/test/kotlin/com/venue/reservation`: move `venues` and
   `ReservationApplicationTests.kt` to `com.venue`.
4. Delete the now-empty `reservation` folders (in both `main` and `test`).
5. **Rename the main class.** Click on `ReservationApplication`, press Shift+F6 (Refactor → Rename)
   and type `VenueApplication`. Rename the file to `VenueApplication.kt` if IntelliJ doesn't offer
   to. Do the same for the test: `ReservationApplicationTests` becomes `VenueApplicationTests`.
6. Search the project for `com.venue.reservation.` **with the dot at the end** (Edit → Find → Find
   in Files). There should be nothing left. (Without the dot, the search also finds the new
   `com.venue.reservations`, which is correct.)
7. **See why the location matters** (then undo it): move `VenueApplication.kt` back down into a
   new package `com.venue.app`, start the app, and call `GET /api/v1/venues/search` in Postman. It
   answers 404: Spring Boot no longer finds the controller, because `com.venue.venues` isn't below
   `com.venue.app`. Move it back to `com.venue` and check it works again.
8. Run `./gradlew test` and `./gradlew bootRun`, check the search endpoint in Postman, then commit,
   push and open a pull request.

## How you know it works

- Every file's `package` line starts with `com.venue.` and has no `reservation` level.
- `./gradlew test` passes, and `GET /api/v1/venues/search` answers 200 again.
- In step 7 you saw the 404, and it went away when the class moved back.

## Check your understanding

Answer in the pull request, in your own words:

1. What does the `package` line at the top of a file tell Kotlin?
2. In step 7, why did the endpoint answer 404 when `VenueApplication` was in `com.venue.app`?
3. Why do we let IntelliJ move and rename things instead of editing the `package` lines by hand?

## Peek at the answer

On `feature/adding_db_flyway`, open `src/main/kotlin/com/venue/VenueApplication.kt`. It sits
directly in `com.venue`, with every part of the app in a package below it: `com.venue.venues`,
`com.venue.users`, `com.venue.auth` and so on. It has two more labels than yours
(`@ConfigurationPropertiesScan`, `@EnableScheduling`): later stories add those.

## Stuck?

- **Tests fail with "Unable to find a @SpringBootConfiguration"**: a test is in a package that's
  not under `com.venue`, so it can't find `VenueApplication`. Check its `package` line.
- **Git shows every file as deleted and new**: that's normal for a move. In the pull request,
  GitHub shows them as renamed.

**Pull request title:** `story 14: rename the package to com.venue`
