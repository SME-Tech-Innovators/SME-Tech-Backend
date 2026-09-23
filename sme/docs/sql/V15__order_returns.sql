-- Apply before deploying return/refund endpoints (manual migration, like V14).
CREATE TABLE IF NOT EXISTS order_returns (
    order_id UUID PRIMARY KEY REFERENCES orders(id),
    shipment_status VARCHAR(255) NOT NULL,
    refund_status VARCHAR(255) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    tracking_reference VARCHAR(255),
    shipment_id VARCHAR(255),
    refund_id VARCHAR(255),
    payment_id UUID REFERENCES payments(id),
    received_at TIMESTAMP,
    quoted_at TIMESTAMP,
    shipment_payload JSONB,
    rates JSONB
);
