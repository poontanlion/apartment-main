ALTER TABLE supply_items
    ADD COLUMN IF NOT EXISTS low_stock_threshold integer NOT NULL DEFAULT 2;
ALTER TABLE supply_items
    ADD COLUMN IF NOT EXISTS low_stock_alert_active boolean NOT NULL DEFAULT false;

-- Keep the outstanding-balance branch's amount_paid column and expose the
-- paid_amount column expected by the current application model.
ALTER TABLE utility_bills
    ADD COLUMN IF NOT EXISTS paid_amount double precision NOT NULL DEFAULT 0;
UPDATE utility_bills
SET paid_amount = COALESCE(amount_paid, 0);

ALTER TABLE utility_bills ALTER COLUMN status SET DEFAULT 'Unpaid';
UPDATE utility_bills
SET status = CASE
    WHEN GREATEST(COALESCE(total_due, total_amount, 0) - COALESCE(paid_amount, 0), 0) <= 0 THEN 'Paid'
    WHEN COALESCE(paid_amount, 0) > 0 THEN 'Partial'
    ELSE 'Unpaid'
END;

CREATE TABLE IF NOT EXISTS bill_payments (
    id varchar(255) PRIMARY KEY,
    bill_id varchar(255) NOT NULL REFERENCES utility_bills(id) ON DELETE CASCADE,
    amount double precision NOT NULL CHECK (amount > 0),
    payment_date timestamp(6) NOT NULL,
    note varchar(1000),
    proof_image text
);
CREATE INDEX IF NOT EXISTS idx_bill_payments_bill_id
    ON bill_payments (bill_id, payment_date);

-- Preserve payment history created by the earlier feature branch.
INSERT INTO bill_payments (id, bill_id, amount, payment_date, note)
SELECT old_payment.id,
       old_payment.bill_id,
       old_payment.amount,
       COALESCE(
           CASE WHEN old_payment.payment_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
                THEN old_payment.payment_date::timestamp END,
           old_payment.created_at,
           CURRENT_TIMESTAMP
       ),
       old_payment.note
FROM utility_bill_payments old_payment
ON CONFLICT (id) DO NOTHING;

-- Keep any recorded paid amount that was not represented in the old payment
-- history as a legacy payment, avoiding double-counting existing payments.
INSERT INTO bill_payments (id, bill_id, amount, payment_date, note)
SELECT 'legacy-' || bill.id,
       bill.id,
       GREATEST(bill.paid_amount - COALESCE(SUM(payment.amount), 0), 0),
       COALESCE(bill.created_at, CURRENT_TIMESTAMP),
       'Imported legacy paid amount'
FROM utility_bills bill
LEFT JOIN bill_payments payment ON payment.bill_id = bill.id
GROUP BY bill.id, bill.paid_amount, bill.created_at
HAVING GREATEST(bill.paid_amount - COALESCE(SUM(payment.amount), 0), 0) > 0
ON CONFLICT (id) DO NOTHING;
