CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE projects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug varchar(120) NOT NULL,
    name varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_slug UNIQUE (slug)
);
