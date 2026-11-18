# Story 17: Tables, rows, ids and basic SQL

**Kind:** Understand (no code to merge) · **Size:** S · **Time:** 2 to 3 hours

## In plain words

Until now, the database has been something your code talks to through a repository. Today you'll
talk to it directly, in its own language, **SQL**, using `psql`, Postgres's command line. Seeing
the tables yourself makes the next stories much easier, because they're all about creating tables
and saving rows.

A database is a set of **tables**. A table is like a spreadsheet: each **row** is one thing (one
venue), and each **column** is one fact about it (its name, its time zone). Every row has an
**id**, a value no other row in that table has, so we can point at exactly one row. And one
table can point at a row in another: each reservation has a `venue_id` that says which venue it
belongs to.

## New words

- **SQL**: the language for asking a database things: `SELECT` (read), `INSERT` (add),
  `UPDATE` (change), `DELETE` (remove).
- **Primary key**: the column that identifies each row, usually `id`. No two rows share one.
- **Foreign key**: a column that points at another table's primary key, like
  `reservations.venue_id`. The database refuses a `venue_id` that doesn't exist.
- **`WHERE`**: picks which rows a statement applies to. Like Kotlin's `filter`, it gives you a
  *new* set of rows that match, and doesn't change or print anything by itself.
- **`psql`**: Postgres's command line, where you type SQL and see the results.

## Before you start (30 minutes)

- The PostgreSQL tutorial, sections 2.1 to 2.6 ("The SQL Language" up to "Joins Between Tables"):
  <https://www.postgresql.org/docs/17/tutorial-sql.html>

## Steps

1. Start the database and the app once, so Flyway creates the tables: `docker compose up -d`, then
   `./gradlew bootRun`. You can stop the app afterwards.
2. Open `psql` inside the database container:
   `docker exec -it reserved-postgres psql -U appuser -d appdb`
   (If you haven't done story 16 yet, the container is called `local-postgres`.)
3. **Look around.** Type `\dt` to list the tables. Then `\d venues` and `\d reservations` to see
   their columns. Find the primary key and the foreign key. Which table did you not create
   yourself? (`flyway_schema_history`.) Run `SELECT * FROM flyway_schema_history;`: that's how Flyway
   remembers which migrations it has already run.
4. **Add rows.**
   ```sql
   INSERT INTO venues (name, timezone) VALUES ('The Atrium', 'America/New_York');
   INSERT INTO venues (name, timezone) VALUES ('Fulton Studio', 'America/Chicago');
   SELECT * FROM venues;
   ```
   You didn't give an `id`. Where did it come from? (Look at `\d venues` again.)
5. **Pick rows with WHERE.** `SELECT name FROM venues WHERE timezone = 'America/Chicago';`
   Then `UPDATE venues SET name = 'The Grand Atrium' WHERE name = 'The Atrium';` and check the
   result with a `SELECT`. What would happen to `UPDATE` without the `WHERE`? (Think, don't try.)
6. **The foreign key at work.** Copy The Grand Atrium's `id` from your `SELECT`, then add a booking
   for it:
   ```sql
   INSERT INTO reservations (venue_id, start_time, end_time, status)
   VALUES ('<the id>', '2026-12-05 18:00+00', '2026-12-05 22:00+00', 'CONFIRMED');
   ```
   Now try the same with a made-up id, `'00000000-0000-0000-0000-000000000000'`. Read the error.
7. **Join two tables.**
   ```sql
   SELECT v.name, r.start_time, r.status
   FROM reservations r JOIN venues v ON v.id = r.venue_id;
   ```
8. **Delete.** `DELETE FROM venues WHERE name = 'The Grand Atrium';` Then `SELECT * FROM
   reservations;`. The booking disappeared too. Find the words in `V1__init_schema.sql` that made
   that happen (`ON DELETE CASCADE`).
9. Type `\q` to leave `psql`.

## How you know it works

- You ran every statement above and can explain each result and each error.

## Check your understanding

Post your answers as a comment on this issue:

1. What's the difference between a primary key and a foreign key?
2. In step 6, why did the database refuse the made-up `venue_id`? Why is it good that the
   *database* checks this, and not only our Kotlin code?
3. In step 8, deleting a venue also deleted its booking. When could that be a bad idea for a
   booking app? (There's no right answer here. Think about it from a guest's point of view.)

## Peek at the answer

There's nothing to compare: this story was about looking. But keep question 3 in mind: on
`feature/adding_db_flyway`, venues are never really deleted (they're *marked* deleted), and a
venue with upcoming bookings can't be deleted at all. Later stories build exactly that.

## Stuck?

- **"role appuser does not exist" or "database appdb does not exist"**: check the values in
  `docker-compose.yaml`, and that you're connecting to the right container (`docker ps`).
- **`psql` seems frozen**: it's waiting for a `;` at the end of your statement. Type `;` and press
  Enter.
