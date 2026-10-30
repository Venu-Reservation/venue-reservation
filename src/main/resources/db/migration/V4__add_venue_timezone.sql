-- Each venue's IANA time zone (e.g. America/New_York). Booking times are stored
-- in UTC, and clients show and enter them in the venue's zone.
-- The default only fills rows that already exist, then it's dropped so new
-- venues must set their zone explicitly.
ALTER TABLE venues ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'America/New_York';
ALTER TABLE venues ALTER COLUMN timezone DROP DEFAULT;
