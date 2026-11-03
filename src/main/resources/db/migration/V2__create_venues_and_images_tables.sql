CREATE TABLE venues (
    id BIGSERIAL PRIMARY KEY,
    host_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    size_sqft INT,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    -- City, state and time zone come from the supported city list, never from clients.
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    -- IANA time zone (e.g. America/New_York). Booking times are stored in UTC;
    -- clients show and enter them in the venue's zone.
    timezone VARCHAR(64) NOT NULL,
    -- Null until the address is geocoded. 6 decimal places is about 10 cm.
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    hourly_rate DECIMAL(10, 2) NOT NULL,
    thumbnail_url VARCHAR(500),
    -- The host stopped new bookings; existing ones stay.
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Soft delete: every read excludes rows with deleted_at set.
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_venue_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    )
);

CREATE TABLE venue_images (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_venues_host_id ON venues(host_id);
-- Search looks venues up by city, case-insensitively, among active, unpaused ones.
CREATE INDEX idx_venues_active_city ON venues (lower(city)) WHERE deleted_at IS NULL AND NOT paused;
CREATE INDEX idx_venue_images_venue_id ON venue_images(venue_id);
