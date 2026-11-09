-- Optional per-room recurring extra charge (e.g. parking, internet), snapshotted onto each bill.
ALTER TABLE rooms ADD COLUMN extra_charge_label varchar(255);
ALTER TABLE rooms ADD COLUMN extra_charge_amount double precision;

ALTER TABLE utility_bills ADD COLUMN extra_charge_label varchar(255);
ALTER TABLE utility_bills ADD COLUMN extra_charge_amount double precision;
