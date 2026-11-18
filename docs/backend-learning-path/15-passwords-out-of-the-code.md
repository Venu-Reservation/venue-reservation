# Story 15: Keep passwords out of the code

**Kind:** Build · **Size:** S · **Time:** 2 to 3 hours

## In plain words

`application.properties` holds the app's settings, including the database password
(`secretpassword`). Because the file is in Git, anyone who can see the repository can see the
password. For your laptop that's harmless, but the same file will one day run on a real server,
where the password must stay secret.

The usual fix has two parts:

1. In `application.properties`, the password becomes a **placeholder**: `${DB_PASSWORD}` means
   "use the value of the environment variable `DB_PASSWORD`". On a real server, whoever runs the
   app sets that variable, and the password never appears in our code.
2. For our laptops, a small extra file, `application-dev.properties`, fills in the local
   password. It only loads in the **dev profile**, a mode for local development, which we make the
   default.

So on your laptop nothing changes, and on a real server the app can't start until it's given
a real password.

## New words

- **Environment variable**: a named value set outside the program, in the terminal or by the
  server, like `DB_PASSWORD=...`. Programs can read it, but it isn't in the code.
- **Placeholder**: `${DB_PASSWORD}` in a properties file, filled in from an environment variable.
  `${DB_URL:jdbc:postgresql://localhost:5432/appdb}` also gives a default after the `:`, used when
  the variable isn't set.
- **Profile**: a named set of settings. Spring Boot loads `application.properties` always, and
  `application-dev.properties` only when the `dev` profile is on.
- **`spring.profiles.default`**: which profile to use when nobody chose one.

## Before you start (20 minutes)

- Spring Boot "Externalized Configuration", the sections "Property Placeholders" and
  "Profile Specific Files": <https://docs.spring.io/spring-boot/reference/features/external-config.html>

## Steps

1. Update `main` and create a branch: `git checkout -b feature/passwords-out-of-code`.
2. Open `src/main/resources/application.properties` and change the database lines to:
   ```properties
   spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/appdb}
   spring.datasource.username=${DB_USERNAME:appuser}
   spring.datasource.password=${DB_PASSWORD}
   ```
   The address and username get defaults because they aren't secret. The password gets none, on
   purpose.
3. At the top, after `spring.application.name`, add a comment and this line:
   ```properties
   spring.profiles.default=dev
   ```
4. Create `src/main/resources/application-dev.properties` with a comment saying these values are
   for local development only, and this line:
   ```properties
   spring.datasource.password=secretpassword
   ```
5. Remove `spring.datasource.driver-class-name`, `spring.flyway.enabled` and
   `spring.flyway.locations`: Spring Boot already uses exactly those values when they're left
   out. Fewer lines, same behaviour.
6. Start the app with `./gradlew bootRun`. Near the top of the log, find the line
   `No active profile set, falling back to 1 default profile: "dev"`.
7. **See the safety net work.** Stop the app, then start it with a different profile, so the dev
   file isn't loaded: `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun`. The log now says
   `The following 1 profile is active: "prod"`, and the app fails to start with
   `FATAL: password authentication failed for user "appuser"`. Nobody gave it `DB_PASSWORD`, so it
   sent the placeholder text itself, `${DB_PASSWORD}`, as the password, and the database said no.
   Now give it the password on the command line instead:
   `SPRING_PROFILES_ACTIVE=prod DB_PASSWORD=secretpassword ./gradlew bootRun`. It starts. That's
   how a real server would run it.
8. Run `./gradlew test`, then commit, push and open a pull request.

## How you know it works

- `application.properties` has no password in it.
- `./gradlew bootRun` with no extra settings starts in the `dev` profile and works as before.
- In step 7, the app refused to start without `DB_PASSWORD`, and started once it was given.

## Check your understanding

Answer in the pull request, in your own words:

1. Why is it a problem to keep a real password in `application.properties`?
2. Why does the password have no default after a `:`, when the address and username do?
3. In step 7, why did the app fail with `SPRING_PROFILES_ACTIVE=prod`, but start without it? And
   what password did it actually send to the database?

## Peek at the answer

On `feature/adding_db_flyway`, open `src/main/resources/application.properties` and
`application-dev.properties`. You'll see the same pattern, used for two more secrets that later
stories add (`JWT_SECRET` and `PAYMENT_WEBHOOK_SECRET`).

## Stuck?

- **"password authentication failed" even without step 7**: the dev file isn't being loaded. Check
  its name is exactly `application-dev.properties`, in `src/main/resources`, and that
  `spring.profiles.default=dev` has no typo. Look for the `falling back to 1 default profile: "dev"`
  line in the log.
- **Tests fail with the same error**: the tests also use the default `dev` profile, so it's the same
  cause: check the dev file.

**Pull request title:** `story 15: keep passwords out of the code`
