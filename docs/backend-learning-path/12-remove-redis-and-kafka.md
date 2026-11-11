# Story 12: Remove Redis and Kafka

**Kind:** Build · **Size:** XS · **Time:** 1 to 2 hours

## In plain words

When the project was first created, it was set up with two extra tools: **Redis** (a very fast
temporary store, often used as a cache) and **Kafka** (a system for sending messages between
programs). They're useful for large systems, but Phase 1 doesn't need either, and our project
document lists them as out of scope.

Right now they still cost us something: the app tries to connect to them, Docker starts them on
your laptop, and there are settings for them that nobody uses. Today you'll remove them
cleanly, from the three places they live. It's mostly deleting, which makes it a good way to
learn what each of those places does.

## New words

- **Dependency**: a library our app uses. They're listed in `build.gradle`, and Gradle downloads
  them for us.
- **Starter**: a Spring Boot dependency that brings in everything needed for one feature, like
  `spring-boot-starter-data-redis` for Redis.
- **Gradle**: the build tool. It downloads dependencies, compiles our code and runs tests
  (`./gradlew test`, `./gradlew bootRun`).
- **Docker Compose**: starts the programs our app needs (here, the database) from
  `docker-compose.yaml`, each in its own container.
- **Out of scope**: deliberately not part of this phase of the project.

## Before you start (15 minutes)

- In `docs/project.md` on the `feature/adding_db_flyway` branch, read "Backend rules": find the
  line that lists what's out of scope for Phase 1.
- Gradle "Declaring dependencies", the first section:
  <https://docs.gradle.org/current/userguide/declaring_dependencies.html>

## Steps

1. Make sure `main` is up to date (`git checkout main`, `git pull`) and create a branch:
   `git checkout -b feature/remove-redis-kafka`.
2. **`build.gradle`**: delete the four lines with `redis` or `kafka` in them: two under
   `implementation`, two under `testImplementation`.
3. **`application.properties`**: delete the `# Redis` and `# Kafka` sections. (Did you notice that
   the Redis host says `localhostz`? That typo never mattered, because nothing used Redis.)
4. **`docker-compose.yaml`**: delete the `redis:` and `kafka:` services, and their
   `redis_data` and `kafka_data` volumes at the bottom. Keep `postgres`.
5. Stop the old containers: `docker compose down --remove-orphans`, then start the database
   again: `docker compose up -d`. Run `docker ps`: only Postgres should be listed.
6. In IntelliJ, click the Gradle elephant icon ("Sync") so it notices the changes.
7. Run `./gradlew test`, then `./gradlew bootRun`, and check `/actuator/health` in Postman. Both
   should still work.
8. Commit, push, and open a pull request.

## How you know it works

- `grep -ri "redis\|kafka" build.gradle src docker-compose.yaml` finds nothing.
- `docker ps` lists only the Postgres container.
- The tests pass and `/actuator/health` answers `{"status":"UP"}`.

## Check your understanding

Answer in the pull request, in your own words:

1. What's the difference between a line under `implementation` and one under `testImplementation`?
2. Before this change, the app started even though nothing used Redis. Why remove it anyway?
3. What does `--remove-orphans` do in step 5?

## Peek at the answer

On `feature/adding_db_flyway`, compare:

- `build.gradle.kts` (written in Kotlin instead of Groovy; that's story 13): look at the
  `dependencies { }` block. There's no Redis or Kafka.
- `docker-compose.yaml`: only Postgres.

## Stuck?

- **IntelliJ shows red errors about missing classes** after step 2: something still imports Redis
  or Kafka. Search the project for `redis` and `kafka` (Edit → Find → Find in Files). On `main`
  there shouldn't be any, so tell us if you find one.
- **"port is already allocated"** in step 5: the old containers are still running. Run
  `docker ps`, then `docker stop` on any Redis or Kafka container.

**Pull request title:** `story 12: remove Redis and Kafka`
