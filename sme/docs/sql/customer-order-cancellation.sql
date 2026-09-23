-- Additive migration; existing orders remain valid.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS customer_access_token_hash varchar(64),
    ADD COLUMN IF NOT EXISTS customer_access_expires_at timestamp,
    ADD COLUMN IF NOT EXISTS customer_access_sent_at timestamp,
    ADD COLUMN IF NOT EXISTS cancellation_request_status varchar(20),
    ADD COLUMN IF NOT EXISTS cancellation_request_reason varchar(500),
    ADD COLUMN IF NOT EXISTS cancellation_review_note varchar(500),
    ADD COLUMN IF NOT EXISTS cancellation_requested_at timestamp,
    ADD COLUMN IF NOT EXISTS cancellation_reviewed_at timestamp;
