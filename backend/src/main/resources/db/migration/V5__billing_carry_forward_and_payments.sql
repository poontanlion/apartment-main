-- Normalize legacy balances before enabling partial payments.
UPDATE utility_bills
SET total_due = CASE
    WHEN COALESCE(total_due, 0) = 0
    THEN COALESCE(total_amount, 0) + COALESCE(previous_outstanding, 0) + COALESCE(interest_amount, 0)
    ELSE total_due
END;

UPDATE utility_bills
SET outstanding_balance = GREATEST(COALESCE(total_due, total_amount, 0) - COALESCE(amount_paid, 0), 0),
    status = CASE
        WHEN GREATEST(COALESCE(total_due, total_amount, 0) - COALESCE(amount_paid, 0), 0) <= 0 THEN 'Paid'
        WHEN COALESCE(amount_paid, 0) > 0 THEN 'Partial'
        ELSE 'Pending'
    END;
