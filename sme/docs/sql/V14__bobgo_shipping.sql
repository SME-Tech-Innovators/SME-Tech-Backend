-- Step 14: Bob Go shipping integration (reference DDL; Hibernate ddl-auto also creates these)

CREATE TABLE IF NOT EXISTS workspace_shipping_settings (
    workspace_id                  UUID PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,
    provider                      VARCHAR(50) NOT NULL DEFAULT 'bobgo',
    enabled                       BOOLEAN NOT NULL DEFAULT FALSE,
    collection_address            JSONB,
    fallback_flat_rate_amount     NUMERIC(19, 2),
    fallback_flat_rate_currency   VARCHAR(3) DEFAULT 'ZAR',
    allow_pickup                  BOOLEAN NOT NULL DEFAULT FALSE,
    pickup_label                  VARCHAR(255),
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_shipments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id              UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    workspace_id          UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    provider              VARCHAR(50) NOT NULL,
    shipping_option_id    VARCHAR(255),
    bobgo_shipment_id     VARCHAR(255),
    tracking_reference    VARCHAR(255),
    status                VARCHAR(50) NOT NULL DEFAULT 'pending',
    last_error            TEXT,
    raw_response          JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_shipments_workspace
    ON order_shipments(workspace_id);
