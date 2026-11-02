-- Development data, loaded only by the dev profile (see application-dev.properties).
-- Matches the web app's mock (mock/db.json): the same users, venues and photos,
-- and the same test password for every user: devpass123.
--
-- Flyway re-runs this file whenever it changes. Every insert skips rows that
-- already exist, so re-running never duplicates or overwrites data. The local
-- database lives in memory (tmpfs in docker-compose.yaml), so to start over
-- with fresh dates: docker compose down && docker compose up -d, then start the app.

-- A time on a day relative to today, in a venue's time zone, e.g.
-- pg_temp.venue_time('America/New_York', 1, 18) is tomorrow 6:00 PM New York time.
CREATE FUNCTION pg_temp.venue_time(tz TEXT, days INT, hour INT) RETURNS TIMESTAMPTZ
LANGUAGE sql AS $$
    SELECT (date_trunc('day', now() AT TIME ZONE tz) + make_interval(days => days, hours => hour)) AT TIME ZONE tz
$$;

-- Every user's password_hash is the same precomputed Argon2id hash of
-- devpass123 (Postgres can't compute Argon2 itself). Fine for a dev-only password.
INSERT INTO users (id, name, email, password_hash, created_at) VALUES
    (1, 'Alex Morgan', 'alex@dev.local', '{argon2}$argon2id$v=19$m=19456,t=2,p=1$s9xmz6NAPBDLydQ/6Q4Z7Q$hK/EBKa9MHFVETh+RDIcdVsWbqTUpjXiUrvfA/l1IaQ', '2026-08-15T12:00:00Z'),
    (2, 'Jamie Lee', 'jamie@dev.local', '{argon2}$argon2id$v=19$m=19456,t=2,p=1$s9xmz6NAPBDLydQ/6Q4Z7Q$hK/EBKa9MHFVETh+RDIcdVsWbqTUpjXiUrvfA/l1IaQ', '2026-08-20T12:00:00Z'),
    (3, 'Sam Rivera', 'sam@dev.local', '{argon2}$argon2id$v=19$m=19456,t=2,p=1$s9xmz6NAPBDLydQ/6Q4Z7Q$hK/EBKa9MHFVETh+RDIcdVsWbqTUpjXiUrvfA/l1IaQ', '2026-08-25T12:00:00Z')
ON CONFLICT DO NOTHING;

-- Jamie hosts the New York venues, Sam hosts Chicago, Alex hosts nothing.
INSERT INTO venues (id, host_id, name, capacity, size_sqft, city, state, timezone, hourly_rate, thumbnail_url, created_at) VALUES
    (1, 2, 'The Atrium at SoHo', 120, 2400, 'New York', 'NY', 'America/New_York', 320.00, 'https://picsum.photos/seed/atrium/800/600', '2026-09-01T14:00:00Z'),
    (2, 2, 'Mercer Street Loft', 80, 1800, 'New York', 'NY', 'America/New_York', 285.00, 'https://picsum.photos/seed/mercer/800/600', '2026-09-03T10:30:00Z'),
    (3, 2, 'Gallery 41', 60, 1550, 'New York', 'NY', 'America/New_York', 240.00, 'https://picsum.photos/seed/gallery41/800/600', '2026-09-10T16:00:00Z'),
    (4, 2, 'Hudson Rooftop', 150, NULL, 'New York', 'NY', 'America/New_York', 410.00, NULL, '2026-09-12T09:00:00Z'),
    (5, 3, 'Fulton Market Studio', 45, 1100, 'Chicago', 'IL', 'America/Chicago', 175.00, 'https://picsum.photos/seed/fulton/800/600', '2026-09-20T18:00:00Z')
ON CONFLICT DO NOTHING;

INSERT INTO venue_images (id, venue_id, image_url, display_order) VALUES
    (1, 1, 'https://picsum.photos/seed/atrium/1600/1000', 0),
    (2, 1, 'https://picsum.photos/seed/atrium-2/1600/1000', 1),
    (3, 2, 'https://picsum.photos/seed/mercer/1600/1000', 0),
    (4, 3, 'https://picsum.photos/seed/gallery41/1600/1000', 0),
    (5, 5, 'https://picsum.photos/seed/fulton/1600/1000', 0)
ON CONFLICT DO NOTHING;

-- Bookings relative to the day the seed first runs. The first one means a
-- default web search (tomorrow 6 PM, 4 hours, New York) leaves out the Atrium.
INSERT INTO reservations (id, venue_id, renter_id, period, total_price, status, expires_at) VALUES
    -- Alex: upcoming, confirmed. Tomorrow 6-10 PM at the Atrium.
    (1, 1, 1, tstzrange(pg_temp.venue_time('America/New_York', 1, 18), pg_temp.venue_time('America/New_York', 1, 22), '[)'), 1280.00, 'CONFIRMED', NULL),
    -- Alex: upcoming, confirmed. In a week, 2-6 PM at Mercer Street.
    (2, 2, 1, tstzrange(pg_temp.venue_time('America/New_York', 7, 14), pg_temp.venue_time('America/New_York', 7, 18), '[)'), 1140.00, 'CONFIRMED', NULL),
    -- Sam: upcoming, confirmed, on one of Jamie's venues (shows in Jamie's host view).
    (3, 3, 3, tstzrange(pg_temp.venue_time('America/New_York', 3, 10), pg_temp.venue_time('America/New_York', 3, 15), '[)'), 1200.00, 'CONFIRMED', NULL),
    -- Alex: past, confirmed. Ten days ago in Chicago.
    (4, 5, 1, tstzrange(pg_temp.venue_time('America/Chicago', -10, 18), pg_temp.venue_time('America/Chicago', -10, 20), '[)'), 350.00, 'CONFIRMED', NULL),
    -- Alex: cancelled.
    (5, 4, 1, tstzrange(pg_temp.venue_time('America/New_York', 5, 19), pg_temp.venue_time('America/New_York', 5, 22), '[)'), 1230.00, 'CANCELLED', NULL)
ON CONFLICT DO NOTHING;

-- Keep the id sequences ahead of the seeded ids, so new rows don't collide.
SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT max(id) FROM users), 1));
SELECT setval(pg_get_serial_sequence('venues', 'id'), GREATEST((SELECT max(id) FROM venues), 1));
SELECT setval(pg_get_serial_sequence('venue_images', 'id'), GREATEST((SELECT max(id) FROM venue_images), 1));
SELECT setval(pg_get_serial_sequence('reservations', 'id'), GREATEST((SELECT max(id) FROM reservations), 1));
