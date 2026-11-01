-- One row per payment event received from the provider. The unique event_id
-- makes the webhook idempotent: a repeated event is recognised and ignored.
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id),
    amount DECIMAL(10, 2) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- CONFIRMED: the payment confirmed its reservation.
    -- REFUND_REQUIRED: money was taken but the reservation can't be confirmed
    --   (the hold expired, it was cancelled, or the amount was wrong).
    -- IGNORED: an event that isn't a successful payment.
    CONSTRAINT chk_payment_outcome CHECK (outcome IN ('CONFIRMED', 'REFUND_REQUIRED', 'IGNORED'))
);

CREATE INDEX idx_payments_reservation_id ON payments(reservation_id);
