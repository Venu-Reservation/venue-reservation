CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    renter_id BIGINT NOT NULL REFERENCES users(id),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_reservation_times CHECK (end_time > start_time),
    CONSTRAINT chk_reservation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_reservations_venue_id ON reservations(venue_id);
CREATE INDEX idx_reservations_renter_id ON reservations(renter_id);
CREATE INDEX idx_reservations_dates ON reservations(venue_id, start_time, end_time);
