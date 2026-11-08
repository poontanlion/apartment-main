ALTER TABLE supply_items ADD COLUMN IF NOT EXISTS low_stock_threshold integer NOT NULL DEFAULT 2;
ALTER TABLE supply_items ADD COLUMN IF NOT EXISTS low_stock_alert_active boolean NOT NULL DEFAULT false;

ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS paid_amount double precision NOT NULL DEFAULT 0;
UPDATE utility_bills SET paid_amount = COALESCE(total_amount, 0) WHERE status = 'Paid';
ALTER TABLE utility_bills ALTER COLUMN status SET DEFAULT 'Unpaid';
UPDATE utility_bills SET status = 'Unpaid' WHERE status = 'Pending';

CREATE TABLE bill_payments (
    id varchar(255) PRIMARY KEY,
    bill_id varchar(255) NOT NULL REFERENCES utility_bills(id) ON DELETE CASCADE,
    amount double precision NOT NULL CHECK (amount > 0),
    payment_date timestamp(6) NOT NULL,
    note varchar(1000),
    proof_image text
);
CREATE INDEX idx_bill_payments_bill_id ON bill_payments (bill_id, payment_date);

INSERT INTO bill_payments (id, bill_id, amount, payment_date, note, proof_image)
SELECT 'legacy-' || id, id, paid_amount,
       COALESCE(NULLIF(payment_date, '')::timestamp, created_at, CURRENT_TIMESTAMP),
       'Imported existing paid bill', slip_image
FROM utility_bills WHERE paid_amount > 0;
