# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the backend for the Reserved venue platform. Product rules, the API contract, shared data, decisions, backend rules and open items are in the shared project file, which the web and iOS repos use too:

@docs/project.md

Everything below is specific to this repo.

## Commands

- Needs JDK 25 (the Gradle toolchain in `build.gradle.kts` requires it) and Docker. README.md has the full setup.
- `docker compose up -d`: the local Postgres. The `dev` profile (the default) points at it.
- `./gradlew build`: compiles and runs every test. Tests start their own Postgres with Testcontainers, so Docker must be running; they never touch the local database.
- `./gradlew test --tests 'com.venue.reservations.ReservationRulesTest'`: one test class. Add `.methodName` (or a backticked test name in quotes) for one test.
- `./gradlew bootRun`: the API on http://localhost:8080, with the dev seed data loaded. The web app's `npm run dev` sends its `/api` requests here.
- `docker compose down && docker compose up -d`: reset the local database. Its data is in memory (`tmpfs`), so a new container is always empty and the app's next start loads the migrations and a fresh seed.

## Structure

One Gradle project, with one package per module under `com.venue` (see Backend rules in the shared file):

- Each module exposes an interface (`UserService`, `VenueService`, `ReservationService`) with a `Default…Service` implementation. Other modules depend only on the interface and the DTOs next to it.
- `common`: `ApiException` and its subclasses (thrown for every user-facing error), `ApiExceptionHandler`, `SqlErrors` (SQLState checks), `AppProperties` (`app.*` settings), and `security/` (the filter chain, the JWT encoder and decoder, and `Jwt.userId()`).
- `auth`: signup, login and token issuing, plus the login limits: `AuthRateLimitFilter` (per IP) and `LoginThrottle` (per email), both in memory. Passwords are Argon2id through `BoundedPasswordEncoder` (in `common/security`), which caps how many hashes run at once. `users`: profile, plus the `User` entity. `venues`: CRUD, plus the `Venue` and `VenueImage` entities; a venue stores `hostId`, not a `User` relation.
- `reservations`: JDBC only (`ReservationRepository`), because of the `tstzrange` period. Also holds the shared booking-rule constants and `HoldExpiryJob`.
- `search`: `/venues/search` and `/venues/{id}/availability`, combining `VenueService` and `ReservationService`.
- `payments`: the signed webhook, `PaymentService` (idempotent per event id, one transaction per event), and the dev-only `DevPaymentController`.

## Conventions

- Controllers stay thin: they turn HTTP into types (path and query parameters, e.g. `when=upcoming` into an enum) and pass request DTOs (nullable fields) and `jwt.userId()` to a service. Services validate with `validate(condition) { "message" }`, so the rules hold for every caller and a missing field gets a clear `VALIDATION_ERROR` message instead of a generic JSON error. Messages are shown to users as they are, and match the web app's.
- A venue's city must be a `SupportedCity`; its state and time zone come from there, never from the request. Coordinates are null until geocoding exists (Open items), and are cleared when the address changes.
- The caller is always `@AuthenticationPrincipal jwt: Jwt` → `jwt.userId()`. Ownership is checked in services: 404 for missing, then 403 for not yours.
- Services are `@Transactional`. A reservation's effective status comes from `ReservationRow.effectiveStatus(now)`, so a lapsed hold reads as EXPIRED before the job runs.
- Read the time only from the injected `Clock` (`Instant.now(clock)`), never `Instant.now()` or SQL `now()`; repository queries take `now` as a parameter. Entity `createdAt` defaults are the one exception. Servers rely on NTP to agree on the time.
- A use case that needs two modules that already depend on each other one way gets its own service (`VenueDeletionService`, like `SearchService`), never a two-way dependency, which Spring can't build.
- Booking takes a shared lock on the venue row (`for share`) and deleting takes an exclusive one (`for update`), so a booking can't slip in between the delete's check and the delete.
- Map database errors by SQLState (`isExclusionViolation`, `isUniqueViolation`) at the place they can happen, never wholesale.
- Profiles: `dev` (the default; local secrets and the `db/dev` seed), `test` (`src/test/resources/application-test.properties`); anything else needs `DB_PASSWORD`, `JWT_SECRET` and `PAYMENT_WEBHOOK_SECRET`.
- Tests extend `IntegrationTest`: MockMvc against the real app on a shared Postgres container, with tables truncated before each test and helpers to sign up, create venues and book. `clock.advance(...)` (`TestClock`) moves time for the whole app, e.g. past a hold or a token's expiry. In MockMvc paths, write spaces as spaces (`city=New York`); the path is a URI template and gets encoded again.

## Where things stand (Nov 3, 2026)

Update this section when a step lands, so a new session can pick up from here.

- Phase 1 is complete on `feature/adding_db_flyway`, open as PR 6 (https://github.com/Venu-Reservation/venue-reservation/pull/6), not merged yet. Every endpoint in `docs/project.md` exists. There are 30 Testcontainers tests, including the six required ones, and `./gradlew build` passes. Since then: Argon2id passwords, auth rate limits, an injected `Clock` for all time, venue addresses with nullable coordinates, pausing venues, and blocking deletes of venues with upcoming bookings.
- The user is walking through the code file by file (see Working with the user). Done: build setup, migrations and seed, `common`, security, `auth`, `users`, `venues`, `search`, `reservations`. Next: `payments`, tests, then the web app.
- Migrations are V1 to V4, one per table (users, venues and images, reservations, payments). They were squashed before anything ran outside local machines; once merged, never edit them and add V5 and up. The dev seed (`db/dev/R__dev_seed.sql`) dates its bookings from the day it runs, and since the database is in memory, every new container gets fresh dates.
- The web app (`reserved_frontend`) was checked end to end against this backend. Its next screens are availability, Checkout, Confirmation, My bookings and the Host portal, all against existing endpoints. Backend work comes from what those screens need, and from decisions on the Open items in `docs/project.md` (host cancellation, bookings on deleted venues, a guests filter, `GET /cities`).
- After a merge to `main`, start new work on a new branch from `main`.

## Working with the user

- The user is the architect, and trains a junior backend developer on this repo. Keep code plain and idiomatic, and explain non-obvious choices (tstzrange, the exclusion constraint, hold expiry) in commit messages or short comments.
- Ask before product or API decisions. Record the answers in `docs/project.md` and sync it to the web repo.
- Push and edit PRs only when asked. `gh` is installed and signed in, and `origin` uses SSH.

## Workflow

- Make small changes, build after each one (`./gradlew build`), and run the tests before saying a task is done.
- Commit after each completed step. Don't add dependencies that aren't needed.
- Before large work, summarize the plan and wait for approval.
- `docs/project.md` is shared with the web and iOS repos. Contract or product changes go there first, then get synced to the other repos.
