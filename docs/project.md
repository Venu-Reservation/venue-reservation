# Reserved: shared project context

The single source of truth for every app in the project: the Spring backend, the web app and the iOS app. Product rules, the API contract, shared data and decisions live here; each repo's own CLAUDE.md adds only what is specific to that repo.

Keep one canonical copy of this file and sync it into the other repos (copy it, or pull it in as a git submodule), then import it from each repo's CLAUDE.md with a line containing `@docs/project.md`. When code and this file disagree, stop and ask before choosing. Don't silently change the contract: change this file first, then the apps.

## Product and repos

Phase 1 MVP of a venue reservation platform: people search venues by city and time, book them with a 10-minute payment hold, and list their own venues.

- Backend: `Venu-Reservation/venue-reservation` on GitHub. Kotlin, Spring Boot 4, PostgreSQL 17, Flyway. Runs on http://localhost:8080.
- Web: `Venu-Reservation/reserved_frontend` on GitHub. Vite, React, JavaScript, Tailwind. Runs on http://localhost:5173.
- iOS: `Venu-Reservation/reserved_ios` on GitHub, not started yet (no Xcode project). Uses the same API and the same mock API as the web app. Its design and how it maps to the API: see iOS design and the API.
- Design: Figma Make prototype at https://basil-fluent-18847322.figma.site/ with seven screens reached from the header menu: Discover, Venue details, Checkout, Confirmation, My bookings, Host portal, Sign in. There is no design for Sign up or for listing a venue; those reuse the existing styles.

## Decisions

Agreed while building (Oct and Nov 2026). Each one overrides anything older that says otherwise.

- No user roles. Every account is a plain user who can both rent and list. A user hosts a venue because `venues.host_id` is their id. Login and signup return `hasListings`; host-only screens (host portal, my listings) show only when it is true, while "List your venue" is open to every signed-in user.
- The token decides whose data an endpoint returns. There are no `/me` paths and no user ids in paths, bodies or queries: `GET /users` is your profile, `GET /venues` your listings, `GET /reservations` your bookings. Single resources (`/venues/{id}`, `/reservations/{id}`) check on the server that you're allowed.
- Every venue has an IANA time zone (`venues.timezone`, e.g. `America/New_York`). The API sends and receives UTC ISO-8601; clients show and enter every venue time in the venue's zone, never the device's. Use zone names, not fixed offsets, so daylight saving is handled.
- Search is by city from a fixed list (see Shared data), each with its zone, so a search time can be converted to UTC before any venue loads. A `GET /cities` endpoint will replace the list later.
- Venues can only be listed in a supported city. Clients send just `city`; the backend derives `state` and `timezone` from the city list, so a venue can't end up with the wrong zone.
- Every venue has a street address (`addressLine1`, optional `addressLine2`, `postalCode`), shown publicly on venue details. It also has `latitude` and `longitude`, which stay null until the backend geocodes addresses (see Open items); clients never send them. Search stays by city until a map or area search is built.
- A booking is a start time on the hour plus a whole number of hours (1 to 12). The design's Guests field is left out until search accepts a guests or capacity filter.
- Search lives at `GET /venues/search` and returns a page object (`items`, `page`, `size`, `totalItems`, `totalPages`); pages are zero-based.
- Signup returns the same `{ token, user }` as login, so new users are signed in at once. Passwords need 8 to 128 characters, with no other complexity rules. Tokens are HS256 JWTs whose subject is the user id, valid for 7 days, with no refresh yet.
- Venue photos are image URLs until file storage exists (MinIO is deferred). The first URL is the thumbnail.
- `GET /venues/{id}/availability` gives hourly slots for one day, for the "Live availability" grid on Venue details.
- Until a payment provider is chosen, the dev profile has `POST /reservations/{id}/pay` to pay your own pending booking; it goes through the same logic as the webhook.
- Clients leave out design elements the API has no data for (ratings, match %, saved venues, fees, descriptions, Google sign-in, forgot password) and list them under Open items, instead of faking data.
- Web and iOS develop against one shared mock API (see Mock API) that serves the real `/api/v1` paths and response shapes, or against the backend with its dev seed data. Both use the same users, venues and test password.

## Domain rules

- Users: one `users` table, no roles. Passwords are stored only as an Argon2id hash. Emails are stored lowercased and unique case-insensitively.
- Venues: owned by the user in `host_id`, soft-deleted via `deleted_at`; all reads exclude deleted venues. Each has an IANA `timezone`, derived from its city. Coordinates are both set or both null.
- A host can pause a venue to stop new bookings: a paused venue is left out of search, shows no available slots, and rejects new bookings, while existing bookings stay. Resuming undoes it.
- A venue can't be deleted while it has upcoming bookings (CONFIRMED or PENDING within its hold, not ended yet). Bookings are never deleted. To remove a venue, pause it, let its bookings finish, then delete it.
- Reservation statuses: PENDING, CONFIRMED, CANCELLED, EXPIRED.
- A reservation starts PENDING with `expires_at = now + 10 minutes`, waiting on payment.
- Payment success within the hold confirms it (CONFIRMED, `expires_at` cleared). If the hold already expired, do NOT confirm: flag the payment for refund. A wrong amount, a second payment, or a payment for a cancelled booking is also flagged for refund.
- An expiry job runs every minute and marks stale PENDING rows EXPIRED. The reserve transaction also expires stale holds for that venue before inserting, because the exclusion constraint cannot reference `now()`. A PENDING row past its expiry is reported as EXPIRED even before the job runs.
- No double booking: enforced by a Postgres exclusion constraint on `(venue_id, period)` for status IN (PENDING, CONFIRMED). Application checks are only for friendlier errors.
- Search and availability treat CONFIRMED and unexpired PENDING reservations as blocking.
- Only the guest who booked can cancel. Cancelling twice is fine; an expired booking, or one that has already started, can't be cancelled.
- All times are UTC. Booking ranges are half-open `[start, end)`, so back-to-back bookings don't overlap.
- Money is a decimal with 2 places (BigDecimal on the backend); JSON carries it as a number. A reservation's total is the hourly rate times the hours.

## API contract

Base path `/api/v1`. JSON in and out. Authenticated requests send `Authorization: Bearer <token>`. The user is identified only by the token: never send a user id.

Public (no token): `POST /auth/signup`, `POST /auth/login`, `GET /venues/search`, `GET /venues/{id}`, `GET /venues/{id}/availability`, `POST /payments/webhook` (signed instead). Everything else needs a token. On those, a missing, invalid or expired token is a 401, so clients drop the token and sign the user out on any 401. Public endpoints ignore the `Authorization` header, so a stale token never breaks browsing or signing in.

### Errors

Every error is `{ "code": "...", "message": "..." }`, where `message` is safe to show to users.

| Status | Codes | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing or invalid fields or parameters |
| 401 | `UNAUTHORIZED` | No token, or an invalid or expired one |
| 401 | `INVALID_CREDENTIALS` | Wrong email or password on login |
| 401 | `INVALID_SIGNATURE` | Payment webhook signature missing or wrong |
| 403 | `FORBIDDEN` | Signed in but not allowed (e.g. not the venue's host) |
| 404 | `NOT_FOUND` | No such resource, including soft-deleted venues |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method for the path |
| 409 | `EMAIL_ALREADY_REGISTERED` | Signup with a taken email |
| 409 | `RESERVATION_CONFLICT` | The hours overlap an active booking |
| 409 | `RESERVATION_NOT_CANCELLABLE` | Cancelling an expired or already started booking |
| 409 | `VENUE_NOT_ACCEPTING_BOOKINGS` | Booking a paused venue |
| 409 | `VENUE_HAS_BOOKINGS` | Deleting a venue that has upcoming bookings |
| 429 | `TOO_MANY_REQUESTS` | Too many sign-in or sign-up attempts; the `Retry-After` header says how many seconds to wait |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

Only Postgres SQLState 23P01 (exclusion violation) maps to `RESERVATION_CONFLICT`.

### Auth

`POST /auth/signup` with `{ name, email, password }` returns 201. `POST /auth/login` with `{ email, password }` returns 200. Both return:

```json
{
  "token": "<JWT>",
  "user": { "id": 1, "name": "Alex Morgan", "email": "alex@dev.local", "hasListings": false }
}
```

`hasListings` is true when the user hosts at least one non-deleted venue. Emails are compared case-insensitively.

Both endpoints are rate limited, and over a limit return 429 `TOO_MANY_REQUESTS` with a `Retry-After` header (seconds):

- Per client IP: 10 requests a minute across signup and login.
- Per email: after 5 wrong passwords in a row, each further login attempt for that email must wait 1, 2, 4… seconds, up to 5 minutes. A correct login, or 15 minutes without a failure, resets it. Delays instead of a hard lockout, so nobody can lock a real user out.

### Users

- `GET /users`: your profile, `{ id, name, email, phone, hasListings, createdAt }`.
- `PUT /users` with `{ name, phone }`: updates it and returns the profile. `name` is required; `phone` is optional (at most 50 characters). The email can't be changed yet.

### Venues

Venue response:

```json
{
  "id": 1,
  "hostId": 2,
  "name": "The Atrium at SoHo",
  "capacity": 120,
  "sizeSqft": 2400,
  "addressLine1": "120 Prince St",
  "addressLine2": null,
  "city": "New York",
  "state": "NY",
  "postalCode": "10012",
  "timezone": "America/New_York",
  "latitude": 40.725300,
  "longitude": -73.998600,
  "hourlyRate": 320.00,
  "thumbnailUrl": "https://...",
  "images": [{ "id": 1, "imageUrl": "https://...", "displayOrder": 0 }],
  "paused": false,
  "createdAt": "2026-09-01T14:00:00Z"
}
```

`sizeSqft`, `addressLine2`, `thumbnailUrl`, `latitude` and `longitude` may be null; `images` may be empty and is sorted by `displayOrder`. `state` and `timezone` come from the city. `paused` is true while the host has stopped new bookings.

- `GET /venues/search?city=&startTime=&endTime=&page=0&size=20` (public): active, unpaused venues in the city (case-insensitive). With `startTime` and `endTime` (send both or neither), only venues free for that whole half-open range. `size` is 1 to 100. Returns:

  ```json
  { "items": [ /* venues */ ], "page": 0, "size": 20, "totalItems": 3, "totalPages": 1 }
  ```

- `GET /venues/{id}` (public): one venue, or 404. Paused venues are still returned (with `paused: true`), so guests with a booking can see them.
- `GET /venues/{id}/availability?date=YYYY-MM-DD` (public): the date is in the venue's zone. Returns one slot per bookable start hour (06:00 to 23:00 venue time); a slot is available if that hour is in the future, not booked, and the venue isn't paused:

  ```json
  {
    "venueId": 1, "date": "2026-12-05", "timezone": "America/New_York",
    "slots": [{ "startTime": "2026-12-05T11:00:00Z", "localTime": "06:00", "available": true }]
  }
  ```

- `GET /venues`: your active listings, newest first.
- `POST /venues` (any signed-in user, who becomes the host): returns 201 with the venue. Request:

  ```json
  {
    "name": "Wicker Park Loft", "capacity": 75, "sizeSqft": null,
    "addressLine1": "1550 N Milwaukee Ave", "addressLine2": "Floor 2",
    "city": "Chicago", "postalCode": "60622",
    "hourlyRate": 150.5, "imageUrls": ["https://..."]
  }
  ```

  No `hostId`: the backend takes it from the token. No `state`, `timezone` or coordinates either: `state` and `timezone` come from the city (case-insensitive, one of the supported cities), and coordinates from geocoding later. The first image URL becomes `thumbnailUrl`.
- `PUT /venues/{id}` (host only): the same body; replaces every field, including the photos. It doesn't change `paused`.
- `POST /venues/{id}/pause` and `POST /venues/{id}/resume` (host only): stop or restart new bookings, and return the venue. Repeating either is fine.
- `DELETE /venues/{id}` (host only): soft delete, 204. 409 `VENUE_HAS_BOOKINGS` while it has upcoming bookings.

### Reservations

Reservation response. Times are UTC; show them in `venue.timezone`:

```json
{
  "id": 1,
  "venue": { "id": 1, "name": "The Atrium at SoHo", "city": "New York", "state": "NY",
             "timezone": "America/New_York", "thumbnailUrl": "https://..." },
  "renter": { "id": 1, "name": "Alex Morgan" },
  "startTime": "2026-11-01T23:00:00Z",
  "endTime": "2026-11-02T03:00:00Z",
  "hours": 4,
  "totalPrice": 1280.00,
  "status": "PENDING",
  "expiresAt": "2026-11-01T03:09:13Z",
  "createdAt": "2026-11-01T02:59:13Z"
}
```

`expiresAt` is set only while the status is PENDING: it's when the hold lapses, for the checkout countdown.

- `POST /reservations` with `{ venueId, startTime, endTime }`: creates a PENDING hold, 201. Checked in the venue's zone: in the future, on the hour, starting 06:00 to 23:00, 1 to 12 whole hours. Overlapping an active booking is 409 `RESERVATION_CONFLICT`; a paused venue is 409 `VENUE_NOT_ACCEPTING_BOOKINGS`.
- `GET /reservations?when=upcoming|past&as=host`: your bookings. `when=upcoming` means not over yet and still active (CONFIRMED, or PENDING within its hold), soonest first; `when=past` is everything else (finished, cancelled, expired), newest first; without `when`, all of them, newest first. `as=host` lists bookings on venues you host instead.
- `GET /reservations/{id}`: the guest or the venue's host; anyone else gets 403.
- `DELETE /reservations/{id}`: cancels (guest only) and returns the updated reservation.

### Payments

- `POST /payments/webhook` (from the payment provider): body `{ eventId, reservationId, amount, status }` where `status` is `SUCCEEDED` or `FAILED`, signed with header `X-Signature: <hex HMAC-SHA256 of the raw body>` using the shared webhook secret. Returns `{ eventId, outcome, duplicate }`, where `outcome` is `CONFIRMED`, `REFUND_REQUIRED` or `IGNORED`. Each `eventId` is handled once; repeats return the first outcome with `duplicate: true`. An event for a reservation that doesn't exist gets 200 with `IGNORED` and isn't recorded, so the provider stops retrying it.
- `POST /reservations/{id}/pay` (dev profile only, guest only): pays your own booking as if the provider had confirmed it, and returns the reservation.

## Shared data and validation

Supported cities (until `GET /cities` exists):

| City | State | Time zone |
|---|---|---|
| New York | NY | America/New_York |
| Chicago | IL | America/Chicago |

| Field | Rule |
|---|---|
| Name (user) | 1 to 255 characters |
| Password | 8 to 128 characters |
| Email | Trimmed, lowercased, `something@domain.tld` |
| Venue name | 1 to 255 characters |
| Capacity, size (sq ft) | Whole number, at least 1 (size optional) |
| Hourly rate | More than 0, at most 2 decimal places |
| Venue city | One of the supported cities above (case-insensitive) |
| Address line 1 | 1 to 255 characters |
| Address line 2 | Optional, at most 255 characters |
| Postal code | 1 to 20 characters |
| Photo URL | http(s), at most 500 characters, at most 20 per venue |
| Booking | Starts on the hour, 06:00 to 23:00 venue time, 1 to 12 hours, not in the past |

Development users, in both the mock and the backend's dev seed: Alex Morgan (`alex@dev.local`, no listings), Jamie Lee (`jamie@dev.local`, hosts the 4 New York venues) and Sam Rivera (`sam@dev.local`, hosts the Chicago venue). They share the dev-only password `devpass123`. The backend seed also has bookings: Alex's upcoming, past and cancelled ones, and one by Sam on a venue Jamie hosts.

## Mock API

A json-server mock lives in the web repo (`mock/`) and serves the same `/api/v1` paths and shapes as the real backend, so web and iOS can work without the backend.

- Run it from the web repo: `npm run mock:api`. Base URL `http://localhost:3001/api/v1`. The iOS Simulator reaches it at that same address.
- Seed data: `mock/db.json`. Each start copies it to the gitignored `mock/db.runtime.json` and serves the copy, so anything created resets on restart.
- Faked in `mock/middleware.cjs`: login, signup (new users kept in memory), `GET /users`, search (returns the page object), `GET /venues` (your listings) and `POST /venues` with a token check. Tokens are `mock-token-<userId>`, not JWTs.
- Not faked: availability (search ignores `startTime`/`endTime` and treats every venue as free, and there's no availability endpoint), `PUT /users`, venue edits, deletes, pause and resume, reservations and payments. Use the real backend for those. json-server's 404 body is `{}`, not `{ code, message }`.
- Seed data holds only fields the real API returns.

## Rules for clients (web and iOS)

- Build screens to match the design. Don't change layouts, spacing or copy unless asked.
- Talk only to the documented API. If a screen needs data the API doesn't return, flag it and propose the backend change instead of faking it.
- Authenticate with the bearer token; never send a user id.
- Handle loading, empty, error and 409 conflict states on every screen that calls the API. On any 401 while a token was sent, drop the token and sign the user out.
- Keep the API client layer separate from views, with models matching the DTOs above.

## iOS design and the API

The iOS design is a Figma Make prototype: https://www.figma.com/make/qiV1HkBHXQZN1pbH1oV6lN/Venue-Reservation-Platform-Wireframe (its live preview opens without signing in). Seven screens on an iPhone 15 canvas (390 × 844 pt, 8 pt grid, 44 pt touch targets) and a tab bar: Discover, Bookings, Host, Account. Reviewed Nov 3, 2026 against the contract above.

For each screen: **Build** what the API already supports, **Leave out** what it has no data for (as the web app does; see Decisions), and **Needs backend** for small API additions, listed under Open items. Show every time in `venue.timezone`.

**01 Discover**
- Build: the city heading, the search card (city, then date and time; "any date" is a search without `startTime`/`endTime`), venue cards from `GET /venues/search` (name, thumbnail, hourly rate, city), and the avatar's initials from `user.name`. There's no "featured" flag; the large card can be the first result.
- Leave out: category chips (Celebrations, Meetings, Photo shoots), match %, ratings, the heart (saved venues), the guests range, neighbourhood names ("SoHo"), "Instant book", and "Available this weekend" (it needs a search over a date range, not one time).
- Later: "Nearby spaces", distances and the Map link need map and area search (Open items). `latitude` and `longitude` already exist, null until geocoding.

**02 Venue details**
- Build: the photo gallery and its counter (`images`), name, address (`formatAddress` on web), capacity, size, the Live availability strip from `GET /venues/{id}/availability` with a date picker (picking consecutive hours is client logic), the hourly rate, the total (rate × hours), and Reserve (`POST /reservations`). When `paused` is true, disable Reserve and say the venue isn't taking bookings. Share can share the web link `/venues/{id}`.
- Leave out: match %, rating and review count, the heart, "About this space" (no `description` yet), Message (no messaging), and fees: the design's $1,420 for 4 hours at $320 includes fees, ours is $1,280.
- Needs backend: the host's display name ("Hosted by Jordan…"); the venue has only `hostId`.

**03 Checkout**
- Build: the hold countdown from `expiresAt` (10 minutes), the summary (venue, date, hours, time range), and the 409 `RESERVATION_CONFLICT`, 409 `VENUE_NOT_ACCEPTING_BOOKINGS` and expired-hold states. "Edit" goes back to venue details; a changed time is a new hold, so cancel the old one (`DELETE`) first. Step 1, contact details: name and email from `GET /users` (the email is read-only), phone saved with `PUT /users`.
- Leave out: step 2, event details (event type, guest count, note to host; reservations have none of these), "72 guests", and "Free cancellation for 48 hours" (there's no cancellation policy; today a guest can cancel until the start).
- Payment (step 3) waits for the payment provider (Open items). Card numbers and Apple Pay must go through the provider's iOS SDK and never reach our backend. Until then, a dev-only Pay button calls `POST /reservations/{id}/pay`.

**04 Confirmation**
- Build: the confirmed state, venue, date and time, total, "View booking details" (`GET /reservations/{id}`), and Add to Calendar (on the device, with EventKit: start, end and the venue's address).
- Leave out: guests.
- Needs backend: a booking reference like "RSV–8F42K". Until then, show the reservation id.

**05 Bookings**
- Build: Upcoming and Past tabs (`GET /reservations?when=upcoming|past`) with their counts, the "Next up · in N days" card (the first upcoming one), status badges, and Manage reservation (details, and cancel with `DELETE`). Directions opens Maps with the venue's address, from `GET /venues/{id}`.
- Leave out: View pass (there's no ticket or pass).
- Needs backend (optional): the venue's address in the reservation's `venue` summary, to save that extra call.

**06 Host portal** (only when `hasListings`)
- Build: the greeting, "Your venues" from `GET /venues` with "Live & available" from `paused`, today's schedule from `GET /reservations?as=host` (guest name from `renter.name`, the booking total), View calendar from the same list, Manage (edit, pause or resume, delete, with the 409 `VENUE_HAS_BOOKINGS` state), and "List a new venue" (`POST /venues`).
- Leave out: occupancy percentages, revenue change from last month, and company names ("Brighton Co."). Monthly revenue and "bookings this month" can be summed from the `as=host` list on the device for now.
- Needs backend (later): a host stats endpoint, once revenue and occupancy are defined.

**07 Authentication**
- Build: email and password sign in (`POST /auth/login`), "Create an account" (`POST /auth/signup`), and the 401 `INVALID_CREDENTIALS` and 429 `TOO_MANY_REQUESTS` messages.
- Leave out: Continue with Apple and with Google, Forgot password (all in Open items), and the Terms and Privacy links until those documents exist.

**Account tab**: the design has no screen for it. Until it does: the profile (`GET /users`, edit with `PUT /users`) and Sign out.

## Backend rules

- Kotlin Spring Boot modular monolith, PostgreSQL, Flyway, Gradle Kotlin DSL. Packages under `com.venue`: auth, users, venues, reservations, search, payments, common. Modules talk through service interfaces only (`UserService`, `VenueService`, `ReservationService`), never another module's repository or tables.
- Out of scope for Phase 1: Kafka, Elasticsearch, Redis, MinIO, API gateway, microservices.
- Flyway is the only thing that creates or changes schema; Hibernate `ddl-auto` is validate or none. Never edit a migration that has been applied anywhere; add a new one. Dev-only seed data lives in `db/dev` and loads only in the `dev` profile.
- Use the latest stable Spring Boot and the versions its BOM manages. Never invent version numbers.
- JPA entities are regular Kotlin classes, not data classes (kotlin-jpa and kotlin-spring plugins). Map Postgres TEXT[] with `@JdbcTypeCode(SqlTypes.ARRAY)`.
- Reservations use a `tstzrange` column, read and written with JdbcTemplate or native queries, not JPA mapping.
- Never return entities from controllers; use DTOs.
- Dev-only endpoints use `@Profile("dev")`.
- No secrets in code: the DB password, JWT secret and webhook secret come from env vars (`DB_PASSWORD`, `JWT_SECRET`, `PAYMENT_WEBHOOK_SECRET`); only the dev and test profiles have placeholder values.
- Tests use Testcontainers PostgreSQL; never mock the database for reservation logic. Required: concurrent overlapping bookings give exactly one success and one 409; back-to-back ranges both succeed; expired holds don't block search or booking; payment after expiry isn't confirmed; a duplicate webhook is idempotent; non-owners can't edit, delete or cancel.

## Open items

To decide:

- A guests or capacity filter on search.
- `GET /cities` to replace the fixed city list.
- Geocoding venue addresses into `latitude` and `longitude`, through a provider whose terms allow storing results (OpenCage or an OpenStreetMap-based one; Google and Mapbox limit or charge for storage). Geocode in the background on create and on address changes, leave coordinates null when an address isn't found, and backfill existing venues once. Optionally let hosts confirm the pin on a map.
- Map and area search (a radius or the visible map box) using the coordinates, with PostGIS or `earthdistance`, alongside the city search.
- Search sends the ids of every booked venue, in every city, back to the venues query as a `not in` list. When search grows, replace it with one read-only query in `search` that uses `not exists` against reservations (an exception to the module rule for reads).
- A venue `description` field, fees (cleaning and service), and a cancellation policy (today a guest can cancel any time before the start).
- Whether hosts can cancel guests' bookings, and whether hosts can book their own venues (today they can't cancel and can book).
- Restoring a deleted venue (e.g. `POST /venues/{id}/restore`), for hosts who delete one by mistake.
- Calendar blackouts: hosts block specific times (holidays, private events). One option is a host-made row in `reservations` with its own status, so the exclusion constraint prevents overlaps for free.
- Whether My bookings shows cancelled future bookings under past (today `when=past` includes them).
- Photo upload storage, email changes, and token refresh (short access tokens plus rotating refresh tokens). Add `iss` and `aud` claims to tokens with the refresh work.
- Sending email, through a provider (e.g. Amazon SES, Postmark, Resend) with SPF, DKIM and DMARC on our domain, and Mailpit in docker-compose for local dev. It unlocks:
  - Email verification on signup. Signup would answer the same "check your email" whether or not the email is taken, so it no longer reveals which emails have accounts. Today signup's 409 `EMAIL_ALREADY_REGISTERED` does. It also means signup no longer signs you in at once, which reverses that Decision.
  - Forgot password.
  - Passkey recovery.
- Google sign-in, plus Sign in with Apple (the App Store requires it alongside Google). The client sends the provider's ID token to a new endpoint; the backend verifies its signature, `iss` and `aud`, and returns our usual `{ token, user }`. Needs a nullable `password_hash` and a `user_identities (user_id, provider, subject)` table. Link to an existing account with the same email only when the provider says the email is verified.
- Passkeys (WebAuthn), through a library (Spring Security's WebAuthn4J support or Yubico's `java-webauthn-server`), with a credentials table. iOS needs an `apple-app-site-association` file on a real HTTPS domain. To decide: passkey sign-up or only adding one to an existing account, and recovery when a device is lost.
- Suggested order for the items above: email, then Google and Apple, then passkeys.
- Rate limits are counted in memory per server. With several servers, move them to a shared store or the edge (reverse proxy or CDN).
- The real payment provider and its event format; the webhook shape above is ours for now. Verify its webhooks with the provider's SDK, including its signed timestamp (rejecting events older than about 5 minutes), so a captured request can't be replayed later.
- Refunds: issue them through the provider for `REFUND_REQUIRED` payments. Today they're only recorded in the `payments` table.
- From the iOS design (see iOS design and the API):
  - The host's display name on venues ("Hosted by …").
  - A booking reference code (e.g. "RSV–8F42K") on reservations.
  - Event details on reservations: guest count, event type and a note to the host. A guest count also needs the venue's capacity check.
  - The venue's address in the reservation's `venue` summary, for Directions.
  - Host stats (monthly revenue, change from last month, occupancy), once they're defined.
  - Messaging between guests and hosts, saved venues, reviews and ratings, and categories.

Clients:

- The web app needs to drop the token on any 401 and build Checkout, Confirmation, My bookings and the Host portal against the endpoints above.
