CREATE TABLE venues (
    id BIGSERIAL PRIMARY KEY,
    host_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    size_sqft INT,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    hourly_rate DECIMAL(10, 2) NOT NULL,
    thumbnail_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE venue_images (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_venues_host_id ON venues(host_id);
CREATE INDEX idx_venue_images_venue_id ON venue_images(venue_id);
