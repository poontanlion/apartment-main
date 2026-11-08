ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS previous_outstanding double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS interest_rate double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS interest_amount double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS total_due double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS outstanding_balance double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS outstanding_from_month varchar(255);
UPDATE utility_bills
SET total_due = COALESCE(total_amount, 0),
    outstanding_balance = GREATEST(COALESCE(total_amount, 0) - COALESCE(paid_amount, 0), 0);

CREATE TABLE interest_rate_configs (
    id varchar(255) PRIMARY KEY,
    rate double precision NOT NULL DEFAULT 0,
    updated_at timestamp(6)
);
INSERT INTO interest_rate_configs (id, rate, updated_at) VALUES ('default', 0, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;
