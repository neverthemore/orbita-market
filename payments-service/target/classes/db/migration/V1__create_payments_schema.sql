-- Payments Service Schema
-- Migration V1: Initial schema

CREATE TABLE IF NOT EXISTS accounts (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     VARCHAR(255) NOT NULL UNIQUE,
    balance     BIGINT       NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts (user_id);

-- Transactional Inbox: prevents duplicate event processing.
-- The UNIQUE constraint on event_id is the idempotency guard.
CREATE TABLE IF NOT EXISTS payment_inbox (
    event_id      UUID         PRIMARY KEY,
    order_id      UUID         NOT NULL,
    user_id       VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'PROCESSING',
    processed_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_inbox_order_id ON payment_inbox (order_id);
