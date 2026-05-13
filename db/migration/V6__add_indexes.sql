CREATE INDEX idx_runs_project_environment_status ON runs (project_id, environment, status);

CREATE INDEX idx_runs_project_finished_at ON runs (project_id, environment, finished_at DESC);

CREATE INDEX idx_runs_status_next_attempt ON runs (status, next_attempt_at) WHERE status = 'QUEUED';

CREATE INDEX idx_check_results_run_id ON check_results (run_id);

CREATE INDEX idx_check_results_check_id_created_at ON check_results (check_id, created_at DESC);

CREATE INDEX idx_run_events_run_id_created_at ON run_events (run_id, created_at);

CREATE INDEX idx_checks_project_active ON checks (project_id, active);
