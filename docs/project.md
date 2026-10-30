# Reserved: shared project context

The single source of truth for every app in the project: the Spring backend, the web app and the iOS app. Product rules, the API contract, shared data and decisions live here; each repo's own CLAUDE.md adds only what is specific to that repo.

Keep one canonical copy of this file and sync it into the other repos (copy it, or pull it in as a git submodule), then import it from each repo's CLAUDE.md with a line containing `@docs/project.md`. When code and this file disagree, stop and ask before choosing. Don't silently change the contract: change this file first, then the apps.

## Product and repos

Phase 1 MVP of a venue reservation platform: people search venues by city and time, book them with a 10-minute payment hold, and list their own venues.

- Backend: `Venu-Reservation/venue-reservation` on GitHub. Kotlin, Spring Boot, PostgreSQL, Flyway.
- Web: `reserved_frontend` (this file's first home). Vite, React, JavaScript, Tailwind.
- iOS: planned. Uses the same API and the same mock API as the web app.
- Design: Figma Make prototype at https://basil-fluent-18847322.figma.site/ with seven screens reached from the header menu: Discover, Venue details, Checkout, Confirmation, My bookings, Host portal, Sign in. There is no design for Sign up or for listing a venue; those reuse the existing styles.

## Decisions

Agreed while building the web app (Oct 2026). Each one overrides anything older that says otherwise.

- No user roles. Every account is a plain user who can both rent and list. A user hosts a venue because `venues.host_id` is their id. Login and signup return `hasListings`; host-only screens (host portal, my listings) show only when it is true, while "List your venue" is open to every signed-in user.
- Every venue has an IANA time zone (`venues.timezone`, e.g. `America/New_York`). The API sends and receives UTC ISO-8601; clients show and enter every venue time in the venue's zone, never the device's. Use zone names, not fixed offsets, so daylight saving is handled.
- Search is by city from a fixed list (see Shared data), each with its zone, so a search time can be converted to UTC before any venue loads. A `GET /cities` endpoint will replace the list later.
- A booking is a start time on the hour plus a whole number of hours (1 to 12). The design's Guests field is left out until search accepts a guests or capacity filter.
- Search lives at `GET /venues/search` (matching the backend stub), not `GET /venues`.
- Signup returns the same `{ token, user }` as login, so new users are signed in at once. Passwords need at least 8 characters, with no other complexity rules.
- Venue photos are image URLs until file storage exists (MinIO is deferred). The first URL is the thumbnail.
- Clients leave out design elements the API has no data for (ratings, match %, saved venues, fees, descriptions, the availability grid, Google sign-in, forgot password) and list them under Open items, instead of faking data.
- Web and iOS develop against one shared mock API (see Mock API) that serves the real `/api/v1` paths and response shapes.

## Domain rules

- Users: one `users` table, no roles. Passwords are stored only as a hash (e.g. BCrypt).
- Venues: owned by the user in `host_id`, soft-deleted via `deleted_at`; all reads exclude deleted venues. Each has an IANA `timezone`.
- Reservation statuses: PENDING, CONFIRMED, CANCELLED, EXPIRED.
- A reservation starts PENDING with `expires_at = now + 10 minutes`, waiting on payment.
- Payment success within the hold confirms it (CONFIRMED, `expires_at` cleared). If the hold already expired, do NOT confirm: flag the payment for refund.
- An expiry job runs every minute and marks stale PENDING rows EXPIRED. The reserve transaction also expires stale holds for that venue before inserting, because the exclusion constraint cannot reference `now()`.
- No double booking: enforced by a Postgres exclusion constraint on `(venue_id, period)` for status IN (PENDING, CONFIRMED). Application checks are only for friendlier errors.
- Search treats CONFIRMED and unexpired PENDING reservations as blocking.
- All times are UTC. Booking ranges are half-open `[start, end)`, so back-to-back bookings don't overlap.
- Money is a decimal with 2 places (BigDecimal on the backend); JSON carries it as a number.

## API contract

Base path `/api/v1`. JSON in and out. Authenticated requests send `Authorization: Bearer <JWT>`. The user is identified only by the token: never send a user id in a body or query.

### Errors

Every error is `{ "code": "...", "message": "..." }`, where `message` is safe to show to users.

| Status | Codes | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing or invalid fields |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS` | No or bad token; wrong email or password on login |
| 403 | `FORBIDDEN` | Signed in but not allowed (e.g. not the venue's owner) |
| 404 | `NOT_FOUND` | No such resource (including soft-deleted venues) |
| 409 | `EMAIL_ALREADY_REGISTERED`, `RESERVATION_CONFLICT` | Signup with a taken email; overlapping booking |

Only Postgres SQLState 23P01 (exclusion violation) maps to `RESERVATION_CONFLICT`.

### Auth

`POST /auth/signup` with `{ name, email, password }` returns 201. `POST /auth/login` with `{ email, password }` returns 200. Both return:

```json
{
  "token": "<JWT>",
  "user": { "id": 1, "name": "Alex Morgan", "email": "alex@example.com", "hasListings": false }
}
```

`hasListings` is true when the user hosts at least one non-deleted venue. Emails are compared case-insensitively. Users: `GET /users/me`, `PUT /users/me`.

### Venues

Venue response (field names follow the Kotlin entity; confirm when the DTO is written):

```json
{
  "id": 1,
  "hostId": 2,
  "name": "The Atrium at SoHo",
  "capacity": 120,
  "sizeSqft": 2400,
  "city": "New York",
  "state": "NY",
  "timezone": "America/New_York",
  "hourlyRate": 320.0,
  "thumbnailUrl": "https://...",
  "images": [{ "id": 1, "imageUrl": "https://...", "displayOrder": 0 }],
  "createdAt": "2026-09-01T14:00:00Z"
}
```

`sizeSqft` and `thumbnailUrl` may be null; `images` may be empty.

- `GET /venues/search?city=&startTime=&endTime=&page=&size=`: venues in the city that are free for the whole half-open range. Times are UTC ISO-8601. The response format (plain list or page object) is still open; clients currently expect a plain list.
- `GET /venues/{id}`: one venue, or 404.
- `POST /venues` (any signed-in user, who becomes the host): returns 201 with the venue. Request:

  ```json
  {
    "name": "Wicker Park Loft", "capacity": 75, "sizeSqft": null,
    "city": "Chicago", "state": "IL", "timezone": "America/Chicago",
    "hourlyRate": 150.5, "imageUrls": ["https://..."]
  }
  ```

  No `hostId`: the backend takes it from the token. The first image URL becomes `thumbnailUrl`.
- `GET /venues/mine`: the caller's listings.
- `PUT /venues/{id}`, `DELETE /venues/{id}` (soft delete): owner only, otherwise 403.

### Reservations and payments

Not yet detailed; the web app hasn't reached Checkout.

- `POST /reservations`, `GET /reservations/me`, `GET /reservations/{id}`, `DELETE /reservations/{id}` (cancel).
- The reservation response must include `expiresAt` so clients can show the hold countdown.
- `POST /payments/webhook` (signature verified). `POST /reservations/{id}/pay` exists only in the dev profile.

## Shared data and validation

Supported cities (until `GET /cities` exists):

| City | State | Time zone |
|---|---|---|
| New York | NY | America/New_York |
| Chicago | IL | America/Chicago |

| Field | Rule |
|---|---|
| Password | At least 8 characters |
| Email | Trimmed, lowercased, `something@domain.tld` |
| Venue name | 1 to 255 characters |
| Capacity, size (sq ft) | Whole number, at least 1 (size optional) |
| Hourly rate | More than 0, at most 2 decimal places |
| Photo URL | http(s), at most 500 characters |
| Booking | Starts on the hour, 06:00 to 23:00 venue time, 1 to 12 hours, not in the past |

## Mock API

A json-server mock lives in the web repo (`mock/`) and serves the same `/api/v1` paths and shapes as the real backend, so web and iOS can work before the backend is ready.

- Run it from the web repo: `npm run mock:api`. Base URL `http://localhost:3001/api/v1`. The iOS Simulator reaches it at that same address.
- Seed data: `mock/db.json`. Each start copies it to the gitignored `mock/db.runtime.json` and serves the copy, so anything created resets on restart.
- Test users: Alex (`alex@dev.local`, no listings), Jamie (`jamie@dev.local`, hosts the New York venues) and Sam (`sam@dev.local`, hosts Chicago). They share the test password in `mock/middleware.cjs`.
- Faked in `mock/middleware.cjs`: login, signup (new users kept in memory), and `POST /venues` with a token check. Tokens are `mock-token-<userId>`, not JWTs.
- Not faked: availability (search ignores `startTime`/`endTime` and treats every venue as free), reservations, payments, `GET /venues/mine`. json-server's 404 body is `{}`, not `{ code, message }`.
- Seed data holds only fields the real API returns.

## Rules for clients (web and iOS)

- Build screens to match the design. Don't change layouts, spacing or copy unless asked.
- Talk only to the documented API. If a screen needs data the API doesn't return, flag it and propose the backend change instead of faking it.
- Authenticate with the bearer token; never send a user id.
- Handle loading, empty, error and 409 conflict states on every screen that calls the API. On a 401 for a signed-in user, sign out and send them to sign in.
- Keep the API client layer separate from views, with models matching the DTOs above.

## Backend rules

- Kotlin Spring Boot modular monolith, PostgreSQL, Flyway. Packages under `com.venue`: auth, users, venues, reservations, search, payments, common. Modules talk through service interfaces only, never another module's repository or tables.
- Out of scope for Phase 1: Kafka, Elasticsearch, Redis, MinIO, API gateway, microservices.
- Flyway is the only thing that creates or changes schema; Hibernate `ddl-auto` is validate or none. Never edit a migration that has been applied anywhere; add a new one.
- Use the latest stable Spring Boot and the versions its BOM manages. Never invent version numbers.
- JPA entities are regular Kotlin classes, not data classes (kotlin-jpa and kotlin-spring plugins). Map Postgres TEXT[] with `@JdbcTypeCode(SqlTypes.ARRAY)`.
- Reservations use a `tstzrange` column, read and written with JdbcTemplate or native queries, not JPA mapping.
- Never return entities from controllers; use DTOs.
- Dev-only endpoints use `@Profile("dev")`.
- No secrets in code: the JWT secret, DB password and webhook secret come from config or env vars.
- Tests use Testcontainers PostgreSQL; never mock the database for reservation logic. Required: concurrent overlapping bookings give exactly one success and one 409; back-to-back ranges both succeed; expired holds don't block search or booking; payment after expiry isn't confirmed; a duplicate webhook is idempotent; non-owners can't edit, delete or cancel.

## Open items

Backend: the code on `main` and in PR 6 (`feature/adding_db_flyway`) differs from this file.

- `reservations` uses `start_time`/`end_time`, not `tstzrange`; it has no exclusion constraint, no `expires_at`, and no EXPIRED status (in the enum or the CHECK constraint).
- `users` needs a `password_hash` column. No `role` column is needed.
- `venues` needs `deleted_at`. (`timezone` was added in V4 on PR 6.)
- Packages are under `com.venue.reservation`, not modules under `com.venue`. The build uses Groovy `build.gradle`, not the Kotlin DSL.
- `application.properties` and the dependencies still configure Redis and Kafka, which are out of scope.
- To build to the contract above: signup and login with `hasListings`, `POST /venues` for any signed-in user, `GET /venues/mine`, and search filters `city`, `startTime`, `endTime`. `main` also has a `PATCH /venues/{id}` stub that isn't in this contract.

Still to decide:

- Search response format: plain list or page object (`content`, `totalElements`, ...).
- A guests or capacity filter on search.
- `GET /cities` to replace the fixed city list.
- A venue `description` field, fees (cleaning and service), cancellation policy, an availability endpoint for the hourly grid on Venue details, and photo upload storage.
- Google sign-in and forgot password.
- Token expiry and refresh.
