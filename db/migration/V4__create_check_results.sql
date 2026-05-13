CREATE TABLE check_results (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id uuid NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
    check_id uuid NULL REFERENCES checks (id) ON DELETE SET NULL,
    check_key varchar(120) NOT NULL,
    check_name varchar(200) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PASSED', 'FAILED', 'ERROR', 'SKIPPED')),
    actual_status integer NULL,
    latency_ms bigint NULL CHECK (latency_ms IS NULL OR latency_ms >= 0),
    error_code varchar(60) NULL,
    error_message text NULL,
    response_preview text NULL,
    assertions_json jsonb NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_check_results_run_check_key UNIQUE (run_id, check_key)
);
