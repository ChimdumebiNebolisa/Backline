CREATE TABLE checks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    key varchar(120) NOT NULL,
    name varchar(200) NOT NULL,
    method varchar(16) NOT NULL CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),
    url text NOT NULL,
    expected_status integer NOT NULL CHECK (expected_status BETWEEN 100 AND 599),
    max_latency_ms integer NULL CHECK (max_latency_ms IS NULL OR max_latency_ms > 0),
    assertions_json jsonb NULL,
    config_hash varchar(128) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_checks_project_key UNIQUE (project_id, key)
);
