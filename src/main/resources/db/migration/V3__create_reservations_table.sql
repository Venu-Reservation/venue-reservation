-- A reservation covers a half-open range [start, end), so back-to-back
-- bookings don't overlap, and Postgres itself prevents double booking.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    renter_id BIGINT NOT NULL REFERENCES users(id),
    period TSTZRANGE NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- When a PENDING hold lapses (10 minutes after creation); null otherwise.
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_reservation_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_pending_has_expiry
        CHECK (status <> 'PENDING' OR expires_at IS NOT NULL),
    CONSTRAINT chk_reservation_period
        CHECK (NOT isempty(period) AND lower_inc(period) AND NOT upper_inc(period)
            AND NOT lower_inf(period) AND NOT upper_inf(period)),

    -- No two active (PENDING or CONFIRMED) reservations for one venue may overlap.
    -- A violation raises SQLState 23P01, which the API returns as RESERVATION_CONFLICT.
    CONSTRAINT no_overlapping_reservations
        EXCLUDE USING gist (venue_id WITH =, period WITH &&)
        WHERE (status IN ('PENDING', 'CONFIRMED'))
);

CREATE INDEX idx_reservations_venue_id ON reservations(venue_id);
CREATE INDEX idx_reservations_renter_id ON reservations(renter_id);
-- The expiry job looks for stale holds.
CREATE INDEX idx_reservations_pending_expiry ON reservations (expires_at) WHERE status = 'PENDING';
