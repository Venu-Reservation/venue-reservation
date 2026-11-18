# Story 16: A database that starts fresh each time

**Kind:** Build · **Size:** XS · **Time:** 1 to 2 hours

## In plain words

Our database runs in **Docker**: a tool that runs programs in a *container*, a small sealed box
with everything the program needs. `docker-compose.yaml` describes our box: "Postgres version 17,
this database name, this user and password".

Right now, the box keeps its data on your disk in a *volume*, so the data survives restarts. That
sounds good, but while we're building it causes trouble: old test rows pile up, and when we change
a migration, the database still remembers the old one and refuses to start ("Migrations have failed
validation"; you may have seen it).

So for local development we'll keep the data **in memory** instead. Every time the container
starts, the database is empty, and our migrations build it again from scratch. A fresh start
becomes one command.

## New words

- **Docker image**: a ready-made recipe for a box, like `postgres:17`.
- **Container**: a running box made from an image. You can start, stop and remove it.
- **Volume**: storage on your disk that a container keeps between restarts.
- **tmpfs**: storage in memory. It's fast, and it disappears when the container stops.
- **Docker Compose**: starts the containers described in `docker-compose.yaml`
  (`docker compose up -d`) and stops them (`docker compose down`).

## Before you start (15 minutes)

- Docker's "What is a container?" overview: <https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-a-container/>

## Steps

1. Update `main` and create a branch: `git checkout -b feature/fresh-database`.
2. In `docker-compose.yaml`, in the `postgres` service:
   - Rename `container_name: local-postgres` to `container_name: reserved-postgres`, so it can't
     clash with a container from another project on the same machine.
   - Replace the `volumes:` lines of the service with:
     ```yaml
     tmpfs:
       - /var/lib/postgresql/data
     ```
   - Add a comment above it explaining, in your own words, that the data lives in memory and
     every start is an empty database.
3. Delete the `volumes:` section at the very bottom of the file (`postgres_data:`), since nothing
   uses it now.
4. Recreate the container: `docker compose down`, then `docker compose up -d`. Run `docker ps`:
   you should see `reserved-postgres`.
5. Start the app with `./gradlew bootRun`. In the log, find Flyway's line
   `Successfully applied 1 migration to schema "public"`: it built the empty database again.
6. Try the fresh start. Add a venue straight into the database (story 17 explains this command):
   `docker exec reserved-postgres psql -U appuser -d appdb -c "INSERT INTO venues (name, timezone) VALUES ('Test', 'UTC')"`.
   Count the venues with
   `docker exec reserved-postgres psql -U appuser -d appdb -c "SELECT count(*) FROM venues"`: 1.
   Now run `docker compose down && docker compose up -d`, restart the app, and count again: 0.
   (Your repository test can't show this: tests undo their own changes when they finish.)
7. Remove the old volume you no longer need: `docker volume ls` shows its name (it ends in
   `postgres_data`), then `docker volume rm <that name>`.
8. Run `./gradlew test`, then commit, push and open a pull request.

## How you know it works

- `docker ps` shows `reserved-postgres`.
- After `docker compose down && docker compose up -d` and an app restart, the database is empty
  again, and Flyway's log shows it applying the migration.

## Check your understanding

Answer in the pull request, in your own words:

1. What's the difference between a Docker image and a container?
2. What's the advantage of a database that starts empty every time, and what's the downside?
3. After you restart the database container, why do you also need to restart the app?

## Peek at the answer

On `feature/adding_db_flyway`, open `docker-compose.yaml`. It's the same idea, with a comment that
also mentions sample data a later story adds.

## Stuck?

- **"Conflict. The container name /reserved-postgres is already in use"**: an old container with
  that name exists. Run `docker rm -f reserved-postgres` and try again.
- **The app can't connect after a database restart**: the app was started before the new
  database. Stop it and run `./gradlew bootRun` again.

**Pull request title:** `story 16: a fresh database each time`
