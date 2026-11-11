# Backend learning path

A series of small stories that grow `main` into the finished Phase 1 backend, one idea at a
time. They're written for someone new to Kotlin, Spring Boot and backend development.

This folder lives on the `feature/adding_db_flyway` branch, next to the finished code, so each
story can point at the exact files to compare with. The stories are published a few at a time as
issues on the [project board](https://github.com/orgs/Venu-Reservation/projects/2); this folder
keeps every story, including the ones not published yet.

## How each story works

Every story has the same parts:

- **In plain words**: what we're doing and why, without jargon.
- **New words**: the few terms the story uses, each in one sentence.
- **Before you start**: one short reading or video (15 to 30 minutes).
- **Steps**: small numbered actions.
- **How you know it works**: exactly what you should see.
- **Check your understanding**: 2 or 3 questions. Answer them in your pull request, in your own
  words. There are no trick questions; "I'm not sure, I think…" is a fine answer.
- **Peek at the answer**: the finished version of the same thing, on the `feature/adding_db_flyway`
  branch. Look at it **after** your own attempt, and note what's different and why.
- **Stuck?**: the most common problem and what to do about it.

"Understand" stories have no code to merge: they're reading and small exercises, so the next
coding story makes sense.

## Ground rules

- **Type the code yourself.** AI tools are welcome as a teacher: ask them to explain an error, a
  word, or a line you don't follow. Don't ask them to write the story's code for you; the point
  of each story is what you learn while writing it.
- **One story, one pull request** (except "understand" stories), with the story's number in the
  title. Small is good.
- **Stuck for more than an hour?** Ask. Being stuck is part of learning, but an hour is enough.
- **The finished branch is an answer key, not a shortcut.** Copying from it skips the learning.

## The stories

Status: **Published** (on the board), **Planned** (written later, adjusted to how the published
ones went), **Done**.

### Part 1: Getting comfortable

| # | Story | Kind | Status |
|---|---|---|---|
| 8 | [Understand your test pull request](08-understand-your-tests.md) | Build | Published |
| 9 | [How a request travels through our code](09-how-a-request-travels.md) | Understand | Published |
| 10 | [Kotlin basics: values, types and "maybe empty"](10-kotlin-basics.md) | Understand | Published |
| 11 | [Kotlin classes, data classes and functions](11-kotlin-classes-and-functions.md) | Understand | Published |
| 12 | [Remove Redis and Kafka](12-remove-redis-and-kafka.md) | Build | Published |
| 13 | Write the build file in Kotlin (`build.gradle.kts`) | Build | Planned |
| 14 | Rename the package to `com.venue` | Build | Planned |
| 15 | Keep passwords out of the code (environment variables, a "dev" profile) | Build | Planned |
| 16 | A database that starts fresh each time (Docker Compose) | Build | Planned |

### Part 2: The database

| # | Story | Kind | Status |
|---|---|---|---|
| 17 | Tables, rows, ids and basic SQL | Understand | Planned |
| 18 | Create the users table (a migration) | Build | Planned |
| 19 | The `User` class and saving users | Build | Planned |
| 20 | The venues table and the `Venue` class | Build | Planned |
| 21 | Venue photos: one venue, many photos | Build | Planned |

### Part 3: Talking clearly to the apps

| # | Story | Kind | Status |
|---|---|---|---|
| 22 | HTTP status codes and JSON | Understand | Planned |
| 23 | One error format for the whole API | Build | Planned |
| 24 | Checking input, and helpful 400 messages | Build | Planned |
| 25 | Tests that use their own real database (Testcontainers) | Build | Planned |

### Part 4: Accounts and signing in

| # | Story | Kind | Status |
|---|---|---|---|
| 26 | Why passwords are scrambled, and what a token is | Understand | Planned |
| 27 | Sign up | Build | Planned |
| 28 | Log in and get a token | Build | Planned |
| 29 | Lock the doors: which endpoints need a token | Build | Planned |
| 30 | "Who am I?": your profile from the token | Build | Planned |
| 31 | Slow down password guessing (rate limits) | Build | Planned |
| 32 | Controlling time in tests | Build | Planned |

### Part 5: Venues

| # | Story | Kind | Status |
|---|---|---|---|
| 33 | List a venue in a supported city | Build | Planned |
| 34 | View a venue, and "my venues" | Build | Planned |
| 35 | Edit and delete, only your own | Build | Planned |
| 36 | Search, with pages of results | Build | Planned |
| 37 | Pause and resume bookings | Build | Planned |

### Part 6: Bookings

| # | Story | Kind | Status |
|---|---|---|---|
| 38 | Time zones, UTC, and two people booking the same hour | Understand | Planned |
| 39 | The reservations table, with the no-double-booking rule | Build | Planned |
| 40 | Book a venue: a 10-minute hold | Build | Planned |
| 41 | Booking rules, in the venue's time zone | Build | Planned |
| 42 | Two people at the same moment: exactly one wins | Build | Planned |
| 43 | My bookings: list and view | Build | Planned |
| 44 | Cancel a booking | Build | Planned |
| 45 | Holds that run out after 10 minutes | Build | Planned |
| 46 | Which hours are free on a day | Build | Planned |
| 47 | A venue with upcoming bookings can't be deleted | Build | Planned |

### Part 7: Payments and finishing

| # | Story | Kind | Status |
|---|---|---|---|
| 48 | How a payment company tells us someone paid | Understand | Planned |
| 49 | The payments table and the webhook | Build | Planned |
| 50 | Pretend payments for development, and sample data | Build | Planned |
| 51 | Automatic checks on every pull request, and the docs | Build | Planned |

After story 51, `main` matches this branch, and its pull request (#6) can be closed.
