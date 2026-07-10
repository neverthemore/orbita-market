-- Orders Service Schema
-- Migration V1: Initial schema

CREATE TABLE IF NOT EXISTS orders (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         VARCHAR(255) NOT NULL,
    product_type    VARCHAR(50)  NOT NULL,
    price           BIGINT       NOT NULL CHECK (price > 0),
    status          VARCHAR(50)  NOT NULL,
    payload         TEXT         NOT NULL,
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders (status);

-- Transactional Outbox: events published atomically with the order.
-- OutboxPoller reads unsent rows and publishes to Kafka.
CREATE TABLE IF NOT EXISTS outbox_events (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID         NOT NULL UNIQUE,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    topic         VARCHAR(255) NOT NULL,
    sent          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    sent_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_sent ON outbox_events (sent, created_at)
    WHERE sent = false;

-- Inbox for payment result events — prevents duplicate status updates.
CREATE TABLE IF NOT EXISTS payment_result_inbox (
    event_id    UUID         PRIMARY KEY,
    order_id    UUID         NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_result_inbox_order_id ON payment_result_inbox (order_id);
