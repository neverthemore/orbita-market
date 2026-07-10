-- V2: Performance indexes and constraints improvement

-- Partial index: only index unprocessed inbox entries (fast duplicate check)
CREATE INDEX IF NOT EXISTS idx_payment_inbox_processing
    ON payment_inbox (event_id)
    WHERE status = 'PROCESSING';

-- Index for audit queries: find all payments for a given order
CREATE INDEX IF NOT EXISTS idx_payment_inbox_order_created
    ON payment_inbox (order_id, created_at DESC);

-- Add check constraint to prevent negative balance via application bug
-- (belt-and-suspenders: balance check is also enforced in AccountService)
ALTER TABLE accounts
    ADD CONSTRAINT chk_balance_non_negative CHECK (balance >= 0);
