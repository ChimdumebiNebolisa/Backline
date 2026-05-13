CREATE TABLE run_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id uuid NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
    event_type varchar(40) NOT NULL,
    message text NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
