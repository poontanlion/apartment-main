ALTER TABLE app_notifications ADD COLUMN dedupe_key varchar(255);

CREATE UNIQUE INDEX uk_app_notifications_recipient_dedupe
    ON app_notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;
