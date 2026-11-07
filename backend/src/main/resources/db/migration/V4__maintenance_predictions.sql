CREATE TABLE IF NOT EXISTS maintenance_task_types (
    id varchar(255) PRIMARY KEY,
    code varchar(255) NOT NULL UNIQUE,
    category_key varchar(255) NOT NULL UNIQUE,
    name_th varchar(255) NOT NULL,
    name_en varchar(255) NOT NULL,
    default_interval_days integer,
    is_recurring boolean NOT NULL,
    CHECK (default_interval_days IS NULL OR default_interval_days > 0)
);

INSERT INTO maintenance_task_types
    (id, code, category_key, name_th, name_en, default_interval_days, is_recurring)
VALUES
    ('mtt-aircon', 'AIRCON', 'Air-con servicing', 'ล้าง/ซ่อมแอร์', 'Air-con service', 180, true),
    ('mtt-light', 'LIGHT', 'Light bulb replacement', 'เปลี่ยนหลอดไฟ', 'Light bulb replacement', 365, true),
    ('mtt-electrical', 'ELECTRICAL', 'Electrical', 'ตรวจระบบไฟฟ้า', 'Electrical inspection', 365, true),
    ('mtt-plumbing', 'PLUMBING', 'Plumbing', 'ตรวจระบบประปา', 'Plumbing inspection', 365, true),
    ('mtt-general', 'GENERAL', 'General Repair', 'ซ่อมทั่วไป', 'General repair', NULL, false)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE maintenance_logs
    ADD COLUMN IF NOT EXISTS task_type_id varchar(255) REFERENCES maintenance_task_types(id),
    ADD COLUMN IF NOT EXISTS completed_date date;

CREATE TABLE IF NOT EXISTS maintenance_predictions (
    id varchar(255) PRIMARY KEY,
    room_id varchar(255) NOT NULL REFERENCES rooms(id),
    task_type_id varchar(255) NOT NULL REFERENCES maintenance_task_types(id),
    predicted_date date,
    override_date date,
    computed_at timestamp(6),
    CONSTRAINT uk_maintenance_predictions_room_task UNIQUE (room_id, task_type_id)
);

-- Exact, case-sensitive category matching only. Preserve any existing assignment.
UPDATE maintenance_logs l
SET task_type_id = t.id
FROM maintenance_task_types t
WHERE l.task_type_id IS NULL AND l.category = t.category_key;

-- Legacy completed_at is timestamp WITHOUT time zone, written by LocalDateTime.
-- Interpret its wall-clock value as Bangkok local time, not as a UTC instant.
UPDATE maintenance_logs l
SET completed_date = ((t.completed_at AT TIME ZONE 'Asia/Bangkok') AT TIME ZONE 'Asia/Bangkok')::date
FROM maintenance_tasks t
WHERE l.completed_date IS NULL
  AND l.task_no = t.task_no
  AND t.status = 'Completed'
  AND t.completed_at IS NOT NULL
  AND EXISTS (SELECT 1 FROM rooms r WHERE r.id = l.room_id);

-- Only ISO date-only strings are produced by the existing log seed/sync.
-- Regex alone cannot reject 2026-02-30; catch calendar errors as well.
CREATE OR REPLACE FUNCTION pg_temp.maintenance_legacy_date(value text)
RETURNS date LANGUAGE plpgsql AS $$
BEGIN
    IF value IS NULL OR value !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN
        RETURN NULL;
    END IF;
    RETURN value::date;
EXCEPTION WHEN invalid_datetime_format OR datetime_field_overflow THEN
    RETURN NULL;
END;
$$;

UPDATE maintenance_logs l
SET completed_date = pg_temp.maintenance_legacy_date(l.date)
WHERE l.completed_date IS NULL
  AND (l.task_no IS NULL OR btrim(l.task_no) = '')
  AND EXISTS (SELECT 1 FROM rooms r WHERE r.id = l.room_id);

DROP FUNCTION pg_temp.maintenance_legacy_date(text);
