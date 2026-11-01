-- Reservations become a half-open tstzrange [start, end) with a payment hold,
-- and Postgres itself prevents double booking.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Replace start_time/end_time with period, keeping any existing rows.
ALTER TABLE reservations ADD COLUMN period TSTZRANGE;
UPDATE reservations SET period = tstzrange(start_time, end_time, '[)');
ALTER TABLE reservations ALTER COLUMN period SET NOT NULL;
ALTER TABLE reservations DROP CONSTRAINT chk_reservation_times;
DROP INDEX idx_reservations_dates;
ALTER TABLE reservations DROP COLUMN start_time, DROP COLUMN end_time;

-- PENDING holds expire 10 minutes after creation. Older PENDING rows had no
-- hold time, so they're treated as expired.
ALTER TABLE reservations ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reservations DROP CONSTRAINT chk_reservation_status;
UPDATE reservations SET status = 'EXPIRED' WHERE status = 'PENDING';
ALTER TABLE reservations ADD CONSTRAINT chk_reservation_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'));
ALTER TABLE reservations ADD CONSTRAINT chk_pending_has_expiry
    CHECK (status <> 'PENDING' OR expires_at IS NOT NULL);
ALTER TABLE reservations ADD CONSTRAINT chk_reservation_period
    CHECK (NOT isempty(period) AND lower_inc(period) AND NOT upper_inc(period)
        AND NOT lower_inf(period) AND NOT upper_inf(period));

-- No two active (PENDING or CONFIRMED) reservations for one venue may overlap.
-- A violation raises SQLState 23P01, which the API returns as RESERVATION_CONFLICT.
ALTER TABLE reservations ADD CONSTRAINT no_overlapping_reservations
    EXCLUDE USING gist (venue_id WITH =, period WITH &&)
    WHERE (status IN ('PENDING', 'CONFIRMED'));

-- The expiry job looks for stale holds.
CREATE INDEX idx_reservations_pending_expiry ON reservations (expires_at) WHERE status = 'PENDING';
