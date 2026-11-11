# Story 8: Understand your test pull request

**Kind:** Build (finishes [PR #13](https://github.com/Venu-Reservation/venue-reservation/pull/13)) · **Size:** S · **Time:** about half a day

## In plain words

In story 7 you wrote two tests, and they pass. Before we build anything new, let's make sure
you understand **why** they pass, and what each line does. You'll run them, break them on
purpose, and fix four small things in the pull request that would cause trouble for the rest of
the team. Then the pull request gets merged.

A *test* is a small program that uses our real code and checks the result. If someone changes
the code later and breaks something, the test fails and tells us, before a user ever notices.

## New words

- **Test**: a function marked `@Test` that runs our code and checks the result with `assertEquals`
  and friends. If a check is wrong, the test *fails*.
- **`@DataJpaTest`**: starts only the database part of the app (entities and repositories), not
  the web part. Good for testing "can I save and load this?".
- **`@WebMvcTest`**: starts only the web part (controllers), not the database. Good for testing
  "does this URL answer with the right JSON?".
- **Mock** (`@MockitoBean`): a pretend version of a class. In your controller test, `VenueService`
  is a mock: it does nothing real, it just returns what you told it to with `given(...)`.
- **Dependency** (in `build.gradle`): a library our app uses, like a package you install.

## Before you start (20 minutes)

- Read "Testing Spring Boot Applications", only the parts about **Data JPA Tests** and
  **Spring MVC Tests**: <https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html>
- Skim what a mock is: <https://site.mockito.org/> (the first section, "What is Mockito?").

## Steps

**Part A: run and understand**

1. Switch to your branch: `git checkout feature/venue-integration-tests`, then `git pull`.
2. Run every test: `./gradlew test`. It should say `BUILD SUCCESSFUL`.
3. Run just one test class at a time:
   - `./gradlew test --tests "*VenueRepositoryTest"`
   - `./gradlew test --tests "*VenueControllerTest"`
4. Break the repository test on purpose: change `"Test Auditorium"` in the `assertEquals` line to
   `"Wrong Name"`. Run it again and read the failure message: it tells you what it expected and
   what it got. Change it back.
5. Break the controller test on purpose: change `.value("STUB")` to `.value("REAL")`. Run it and
   read the message. Change it back.
6. Stop your database, then run both tests again. One fails and one still passes. Write down
   which, and why (hint: which test talks to a real database?). Start the database again.

**Part B: fix four small things**

These are all small, but each would cause a problem for someone else on the team:

7. **Your personal database name is in `application.properties`.** It now says
   `venue_reservation` and `zahraalkubaish`. Those only exist on your laptop, so the app won't start
   for anyone else. Change them back to the shared values in `docker-compose.yaml` (database
   `appdb`, user `appuser`), and use the Docker database: `docker compose up -d postgres`.
8. **Test data is in the real `Venue` class.** `Venue` now defaults to `"Test Auditorium"` and
   `"America/New_York"`, so every venue anywhere in the app would get those values unless someone
   remembered to change them. Remove the defaults from `name` and `timezone` (keep
   `var id: UUID? = null`). Your test already passes its own values, so it keeps working.
9. **`spring-boot-starter-web` is listed twice under two names.** `spring-boot-starter-webmvc` was
   already there, and `-web` is the old name for the same thing. Remove the `-web` line you added.
10. **A typo in a file name:** `VunueControllerTest.kt` should be `VenueControllerTest.kt` (the
    class inside is already spelled right). In IntelliJ, right-click the file → Refactor → Rename.
11. Delete the commented-out lines (the old `import`s and the old `id` line). Git remembers old
    versions, so there's no need to keep them as comments.

**Part C: finish**

12. Run `./gradlew test` again: everything should pass.
13. Commit, push, and answer the questions below in the pull request's description.

## How you know it works

- `./gradlew test` ends with `BUILD SUCCESSFUL`.
- In step 6, the repository test fails with a connection error when the database is stopped, and
  the controller test still passes.
- `git diff main` on your branch no longer shows your personal database name.

## Check your understanding

Answer in the pull request, in your own words:

1. What's the difference between `@DataJpaTest` and `@WebMvcTest`? Why do we have both?
2. In the controller test, `VenueService` is a mock. What would be different if it were the real
   service?
3. In step 6, why did one test fail and the other pass?
4. Why is putting `"Test Auditorium"` as a default in `Venue` a problem, even though the tests pass?

## Peek at the answer

There's no exact match on `feature/adding_db_flyway`: the finished code tests everything against a
throwaway database of its own (that's story 25), so tests never depend on the database on your
laptop. If you're curious, look at `src/test/kotlin/com/venue/IntegrationTest.kt`, but don't worry
about understanding it yet.

## Stuck?

- **"Connection refused" or "database does not exist"** after step 7: the Docker database isn't
  running. Run `docker compose up -d postgres`, wait a few seconds, and try again.
- **"Validate failed: Migrations have failed validation"**: Flyway remembers a different version
  of a migration than the file you have. Ask, and we'll reset the local database together.

**Pull request:** keep using [PR #13](https://github.com/Venu-Reservation/venue-reservation/pull/13).
