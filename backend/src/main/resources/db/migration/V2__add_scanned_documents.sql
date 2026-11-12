ALTER TABLE leases ADD COLUMN IF NOT EXISTS scanned_document text;
ALTER TABLE maintenance_tasks ADD COLUMN IF NOT EXISTS scanned_document text;
ALTER TABLE maintenance_logs ADD COLUMN IF NOT EXISTS scanned_document text;
ALTER TABLE utility_bills ADD COLUMN IF NOT EXISTS scanned_document text;

