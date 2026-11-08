ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS previous_outstanding double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS interest_rate double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS interest_amount double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS total_due double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS amount_paid double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS outstanding_balance double precision NOT NULL DEFAULT 0;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS outstanding_from_month varchar(255);
UPDATE utility_bills SET total_due = COALESCE(total_amount, 0), outstanding_balance = CASE WHEN status = 'Paid' THEN 0 ELSE COALESCE(total_amount, 0) END, amount_paid = CASE WHEN status = 'Paid' THEN COALESCE(total_amount, 0) ELSE 0 END;

CREATE TABLE interest_rate_configs (
    id varchar(255) PRIMARY KEY,
    rate double precision NOT NULL DEFAULT 0,
    updated_at timestamp(6)
);
INSERT INTO interest_rate_configs (id, rate, updated_at) VALUES ('default', 0, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

CREATE TABLE utility_bill_payments (
    id varchar(255) PRIMARY KEY,
    bill_id varchar(255) NOT NULL,
    amount double precision NOT NULL CHECK (amount > 0),
    payment_date varchar(255),
    note text,
    created_at timestamp(6),
    CONSTRAINT fk_utility_bill_payments_bill FOREIGN KEY (bill_id) REFERENCES utility_bills(id) ON DELETE CASCADE
);
CREATE INDEX idx_utility_bill_payments_bill_id ON utility_bill_payments (bill_id);
