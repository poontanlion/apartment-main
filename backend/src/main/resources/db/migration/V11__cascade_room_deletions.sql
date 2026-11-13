-- Allow cascading deletion of maintenance_predictions when a room is deleted.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'maintenance_predictions'::regclass
          AND contype = 'f'
          AND confrelid = 'rooms'::regclass
    ) LOOP
        EXECUTE 'ALTER TABLE maintenance_predictions DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END $$;

ALTER TABLE maintenance_predictions
    ADD CONSTRAINT fk_maintenance_predictions_room
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;
