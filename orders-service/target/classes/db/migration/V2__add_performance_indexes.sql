

CREATE INDEX IF NOT EXISTS idx_orders_user_created
    ON orders (user_id, created_at DESC);


CREATE INDEX IF NOT EXISTS idx_orders_pending
    ON orders (created_at)
    WHERE status = 'PAYMENT_PENDING';


CREATE INDEX IF NOT EXISTS idx_outbox_sent_at
    ON outbox_events (sent_at)
    WHERE sent = true;
