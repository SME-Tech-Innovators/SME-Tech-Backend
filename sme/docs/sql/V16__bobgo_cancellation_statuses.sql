-- Apply before deploying Bob Go cancellation support. No new columns are needed.
-- Run as one transaction so a failed validation preserves the previous constraint.
BEGIN;

-- Hibernate may have created this enum CHECK constraint on existing databases.
ALTER TABLE order_shipments DROP CONSTRAINT IF EXISTS order_shipments_status_check;

-- V14 used a lowercase default; Hibernate persists enum names in uppercase.
UPDATE order_shipments
SET status = UPPER(status)
WHERE status <> UPPER(status);
ALTER TABLE order_shipments ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE order_shipments ADD CONSTRAINT order_shipments_status_check
    CHECK (status IN ('PENDING', 'CREATED', 'IN_TRANSIT', 'DELIVERED', 'FAILED',
                      'CANCEL_REQUESTED', 'CANCELLATION_UNKNOWN', 'CANCELLED'));

COMMIT;
