-- Venues are soft-deleted; every read excludes rows with deleted_at set.
ALTER TABLE venues ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- Search looks venues up by city, case-insensitively, among active ones.
CREATE INDEX idx_venues_active_city ON venues (lower(city)) WHERE deleted_at IS NULL;
