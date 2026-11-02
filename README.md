# Reservation

The Reserved backend: a Kotlin Spring Boot API on PostgreSQL. It serves the web and iOS apps at `http://localhost:8080/api/v1`. The API contract, product rules and decisions are in [docs/project.md](docs/project.md), which is shared with those apps.

This guide is for a new developer on a Mac, from a working `java` command through IntelliJ, the local database, sample data and tests.

The project compiles with **JDK 25** (`build.gradle.kts` sets the Java toolchain to 25). An older JDK already on the machine, such as 21, will not build this app.

Start the Docker container before you run the app. The `dev` profile, which is the default, already points at it, so there is nothing extra to configure once it's up.

## 1. Install Homebrew

Homebrew installs the JDK and Docker Desktop. Skip this if `brew --version` already prints a version.

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

When the installer finishes, it prints two or three lines that add `brew` to your shell. Run those lines. On an Apple silicon Mac they look like this:

```bash
echo >> ~/.zprofile
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

Open a new terminal tab and confirm:

```bash
brew --version
```

## 2. Put Java 25 on your PATH

Homebrew installs OpenJDK inside its own directory and leaves it off your `PATH`. macOS also will not see it until it is linked into `/Library/Java/JavaVirtualMachines`. Do both steps.

```bash
brew install openjdk@25
```

Register the JDK with macOS (this asks for your Mac login password):

```bash
sudo ln -sfn "$(brew --prefix)/opt/openjdk@25/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-25.jdk
```

`brew --prefix` prints `/opt/homebrew` on Apple silicon and `/usr/local` on an Intel Mac that still has Homebrew. The command above uses whichever prefix your machine has.

Add the JDK to your shell. Open `~/.zshrc` (create it if it does not exist) and add:

```bash
export PATH="$(brew --prefix)/opt/openjdk@25/bin:$PATH"
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
```

If `brew` is not available yet when `.zshrc` runs, write the prefix in directly. Run `brew --prefix` once, then use that path. On Apple silicon:

```bash
export PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH"
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
```

Reload the shell and check that both commands point at 25:

```bash
source ~/.zshrc
java -version
echo "$JAVA_HOME"
/usr/libexec/java_home -V
```

`java -version` should report version **25**. `JAVA_HOME` should end in `openjdk-25.jdk/Contents/Home`.

## 3. Install Docker and start the database

The app needs one service on localhost. The credentials and port in `docker-compose.yaml` match `src/main/resources/application-dev.properties`.

| Service    | Container        | Host address     | Login |
|------------|------------------|------------------|-------|
| PostgreSQL | `reserved-postgres` | `localhost:5432` | database `appdb`, user `appuser`, password `secretpassword` |

Install and launch Docker Desktop:

```bash
brew install --cask docker
open -a Docker
```

Wait until the whale menu icon says Docker is running. Then, from the project root (the folder that contains `docker-compose.yaml`):

```bash
docker compose up -d --remove-orphans
```

`--remove-orphans` also removes the Redis and Kafka containers from earlier versions of this project, which are no longer used. Check that Postgres is running and answers:

```bash
docker compose ps
docker exec reserved-postgres pg_isready -U appuser -d appdb
```

`pg_isready` should say it is accepting connections.

Leave the container running while you work. The data lives in memory (`tmpfs`), so **stopping the container deletes it**. That's on purpose: every new container is an empty database, and the app's next start creates the tables and loads fresh seed data, with booking dates relative to that day. Restarting only the app keeps the data.

```bash
docker compose down     # stop and remove the container (the data goes with it)
docker compose up -d    # start an empty database; then start the app to fill it
```

If the database restarts while the app is running, restart the app too, so it creates the tables again.

Port `5432` already in use means a local Postgres is bound to that port. Quit it, then run `docker compose up -d` again.

## 4. Set up IntelliJ IDEA

Community Edition can build and run this project.

```bash
brew install --cask intellij-idea-ce
open -a "IntelliJ IDEA CE"
```

1. On the welcome screen, choose **Open** and select this project folder (the one that contains `build.gradle.kts` and `settings.gradle.kts`). Trust the project when asked.
2. If IntelliJ asks how to import it, open it as a **Gradle** project and use the **Gradle wrapper** (`./gradlew`), which is already in the repo. The wrapper downloads Gradle 9.8.1 on the first sync.
3. Set the project JDK to 25.
   - **File → Project Structure…** (⌘;) → **Project**.
   - **SDK:** 25. If 25 is missing, choose **Add SDK → JDK** and select the Homebrew JDK. After the symlink in step 2, that path is:

     `/Library/Java/JavaVirtualMachines/openjdk-25.jdk/Contents/Home`

   - **Language level:** 25 (or the SDK default).
4. Point Gradle at the same JDK.
   - **Settings → Build, Execution, Deployment → Build Tools → Gradle** (⌘,).
   - **Gradle JVM:** Project SDK, or the JDK 25 you just added.
5. Wait for the Gradle sync to finish. The Gradle tool window should list `bootRun` under **Tasks → application**.

## 5. Run the app

The container from step 3 needs to be up first. The app listens on **http://localhost:8080**.

**From IntelliJ:** open `src/main/kotlin/com/venue/VenueApplication.kt` and use the gutter icon next to `fun main`. Choose **Run 'VenueApplicationKt'**.

**From the terminal,** in the project root:

```bash
./gradlew bootRun
```

The first run downloads dependencies. When startup succeeds, the log includes a line like:

```text
Started VenueApplicationKt in ... seconds
```

Confirm the app is up and can reach the database:

```bash
curl http://localhost:8080/actuator/health
```

A healthy process returns HTTP 200 with `{"status":"UP"}`.

Stop a terminal run with Ctrl+C. Stop an IntelliJ run with the red square in the Run tool window.

## 6. Sample data and test users

On startup, Flyway creates or updates the schema (`src/main/resources/db/migration`). The `dev` profile also loads sample data from `src/main/resources/db/dev/R__dev_seed.sql`. It's the same data as the web app's mock API, so both behave the same.

| User | Email | Password | Has listings |
|---|---|---|---|
| Alex Morgan | alex@dev.local | devpass123 | No. Has upcoming, past and cancelled bookings |
| Jamie Lee | jamie@dev.local | devpass123 | Yes, the 4 New York venues |
| Sam Rivera | sam@dev.local | devpass123 | Yes, the Chicago venue |

Bookings are dated relative to the day the seed runs. For example, Alex has the Atrium booked tomorrow from 6 to 10 PM New York time, so that venue doesn't show in a search for that time. To reload fresh data, restart the database and then the app:

```bash
docker compose down
docker compose up -d
./gradlew bootRun
```

Try it:

```bash
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alex@dev.local","password":"devpass123"}'
```

The response has a `token`. Send it as `Authorization: Bearer <token>` to calls that need sign-in, such as `GET /api/v1/reservations`.

In the `dev` profile, `POST /api/v1/reservations/{id}/pay` pays for your own pending reservation, so you can finish a booking without a payment provider.

## 7. Run the tests

```bash
./gradlew build
```

This compiles and runs the tests. Tests start their own throwaway Postgres in Docker (Testcontainers), so Docker Desktop must be running, but they don't touch your local database or its data.

## The API

Everything is under `/api/v1`, and errors are always `{ "code": "...", "message": "..." }`. The full contract, with request and response examples, is in [docs/project.md](docs/project.md). In short:

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/signup`, `POST /auth/login` |
| Your profile | `GET /users`, `PUT /users` |
| Venues | `GET /venues/search`, `GET /venues/{id}`, `GET /venues/{id}/availability`, `GET /venues` (yours), `POST /venues`, `PUT /venues/{id}`, `DELETE /venues/{id}` |
| Reservations | `POST /reservations`, `GET /reservations` (yours, or `?as=host`), `GET /reservations/{id}`, `DELETE /reservations/{id}` (cancel) |
| Payments | `POST /payments/webhook`, and `POST /reservations/{id}/pay` in dev only |

Secrets come from environment variables outside the `dev` profile: `DB_PASSWORD` (plus optional `DB_URL` and `DB_USERNAME`), `JWT_SECRET` (at least 32 characters) and `PAYMENT_WEBHOOK_SECRET`. Set `SPRING_PROFILES_ACTIVE` to something other than `dev` in real environments, so the dev data and the dev-only pay endpoint aren't loaded.

## If something fails

**`java: command not found`, or `java -version` is not 25.** Open a new terminal tab after editing `~/.zshrc`. Confirm the `PATH` line is above any other JDK, then run `which java`. It should print a path under `openjdk@25`.

**Gradle says it cannot find a Java installation for language version 25.** The toolchain in `build.gradle.kts` requires a local JDK 25. Repeat the symlink in step 2, set **Gradle JVM** to that JDK in IntelliJ, and sync Gradle again.

**The app exits while connecting to `localhost:5432`.** Docker Desktop is not running, or the container is not up. Run `docker compose ps` in the project root and start it with `docker compose up -d`.

**Tests fail with "Could not find a valid Docker environment".** Docker Desktop isn't running. Start it and run `./gradlew build` again.

**The container exits immediately or the port is already allocated.** Run `docker compose logs` and look for "address already in use". Free that port, then `docker compose up -d` again.

**Flyway reports a checksum mismatch.** A migration that already ran was edited. Never change an applied migration; add a new one instead. On your own machine, `docker compose down && docker compose up -d` resets the database.
