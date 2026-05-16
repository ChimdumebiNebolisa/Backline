ALTER TABLE runs ADD COLUMN timeout_at timestamptz NULL;

ALTER TABLE runs ADD COLUMN last_error text NULL;
