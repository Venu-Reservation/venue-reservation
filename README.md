# Reservation

Spring Boot app (Kotlin) that talks to PostgreSQL, Redis, and Kafka on your machine. This guide is for a new developer on a Mac, from a working `java` command through IntelliJ and the local Docker services.

The project compiles with **JDK 25** (`build.gradle` sets the Java toolchain to 25). An older JDK already on the machine, such as 21, will not build this app.

Start the Docker containers before you run the app. `application.properties` already points at those containers, so there is nothing extra to configure once they are up.

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

## 3. Install Docker and start the containers

The app connects to three services on localhost. The credentials and ports in `docker-compose.yaml` match `src/main/resources/application.properties`.

| Service    | Container       | Host address     | Login                          |
|------------|-----------------|------------------|--------------------------------|
| PostgreSQL | `local-postgres`| `localhost:5432` | database `appdb`, user `appuser`, password `secretpassword` |
| Redis      | `local-redis`   | `localhost:6379` | no password                    |
| Kafka      | `local-kafka`   | `localhost:9092` | no auth                        |

Install and launch Docker Desktop:

```bash
brew install --cask docker
open -a Docker
```

Wait until the whale menu icon says Docker is running. Then, from the project root (the folder that contains `docker-compose.yaml`):

```bash
docker compose up -d
```

The first run downloads the Postgres, Redis, and Kafka images. Kafka can take half a minute before it accepts connections. Check that all three containers are running:

```bash
docker compose ps
```

You want `local-postgres`, `local-redis`, and `local-kafka` each in a running state. Then confirm Postgres and Redis answer:

```bash
docker exec local-postgres pg_isready -U appuser -d appdb
docker exec local-redis redis-cli ping
```

`pg_isready` should say it is accepting connections. Redis should reply `PONG`.

Leave these containers running while you work. Data is stored in Docker volumes, so it survives a restart.

```bash
docker compose stop    # stop the containers, keep the data
docker compose start   # start them again
docker compose down    # remove the containers, keep the data
docker compose down -v # remove the containers and delete the data
```

Port `5432`, `6379`, or `9092` already in use means a local Postgres, Redis, or Kafka is bound to that port. Quit that process, or stop its app, then run `docker compose up -d` again.

## 4. Set up IntelliJ IDEA

Community Edition can build and run this project.

```bash
brew install --cask intellij-idea-ce
open -a "IntelliJ IDEA CE"
```

1. On the welcome screen, choose **Open** and select this project folder (the one that contains `build.gradle` and `settings.gradle`). Trust the project when asked.
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

Containers from step 3 need to be up first. The app listens on **http://localhost:8080**.

**From IntelliJ:** open `src/main/kotlin/com/venue/reservation/ReservationApplication.kt` and use the gutter icon next to `fun main`. Choose **Run 'ReservationApplicationKt'**.

**From the terminal,** in the project root:

```bash
./gradlew bootRun
```

The first run downloads dependencies. When startup succeeds, the log includes a line like:

```text
Started ReservationApplication in ... seconds
```

Confirm the app can reach the containers:

```bash
curl http://localhost:8080/actuator/health
```

A healthy process returns HTTP 200 and JSON whose top-level `status` is `UP`. The `components` object includes `db` (PostgreSQL), `redis`, and `kafka`, each with `"status": "UP"`. If one of those services is down, its status is `DOWN` and the overall status is `DOWN` as well.

Stop a terminal run with Ctrl+C. Stop an IntelliJ run with the red square in the Run tool window.

## If something fails

**`java: command not found`, or `java -version` is not 25.** Open a new terminal tab after editing `~/.zshrc`. Confirm the `PATH` line is above any other JDK, then run `which java`. It should print a path under `openjdk@25`.

**Gradle says it cannot find a Java installation for language version 25.** The toolchain in `build.gradle` requires a local JDK 25. Repeat the symlink in step 2, set **Gradle JVM** to that JDK in IntelliJ, and sync Gradle again.

**The app exits while connecting to `localhost:5432`, `6379`, or `9092`.** Docker Desktop is not running, or the containers are not up. Run `docker compose ps` in the project root and start anything that is missing with `docker compose up -d`. Give Kafka a few more seconds, then run the app again.

**A container exits immediately or the port is already allocated.** Run `docker compose logs` and look for "address already in use". Free that port, then `docker compose up -d` again.
