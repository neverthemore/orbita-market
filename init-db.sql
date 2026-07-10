-- Run by Docker's postgres entrypoint on first start.
-- Creates two isolated databases — one per service (no shared tables).

CREATE DATABASE payments_db;
CREATE DATABASE orders_db;

-- Grant all privileges to the orbita user
GRANT ALL PRIVILEGES ON DATABASE payments_db TO orbita;
GRANT ALL PRIVILEGES ON DATABASE orders_db TO orbita;
