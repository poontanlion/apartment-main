-- Bank account and QR code printed on invoices (single settings row, all optional).
ALTER TABLE interest_rate_configs ADD COLUMN bank_name varchar(255);
ALTER TABLE interest_rate_configs ADD COLUMN bank_account_name varchar(255);
ALTER TABLE interest_rate_configs ADD COLUMN bank_account_number varchar(255);
ALTER TABLE interest_rate_configs ADD COLUMN bank_qr_image text;
