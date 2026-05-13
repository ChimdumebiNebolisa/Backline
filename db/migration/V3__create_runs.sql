CREATE TABLE runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES projects (id) ON DELETE RESTRICT,
    environment varchar(60) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'PASSED', 'FAILED', 'ERROR', 'CANCELLED')),
    idempotency_key varchar(180) NULL,
    config_hash varchar(128) NOT NULL,
    source varchar(60) NULL,
    queued_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz NULL,
    finished_at timestamptz NULL,
    locked_by varchar(120) NULL,
    locked_at timestamptz NULL,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_runs_idempotency_key ON runs (idempotency_key) WHERE idempotency_key IS NOT NULL;
