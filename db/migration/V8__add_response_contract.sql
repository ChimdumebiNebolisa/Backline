-- Observed JSON response-contract snapshots on check results, plus per-check capture settings.
ALTER TABLE checks
    ADD COLUMN contract_json jsonb NULL;

ALTER TABLE check_results
    ADD COLUMN response_contract_json jsonb NULL,
    ADD COLUMN response_contract_hash varchar(64) NULL,
    ADD COLUMN response_contract_status varchar(20) NULL;

ALTER TABLE check_results
    ADD CONSTRAINT chk_check_results_response_contract_status
    CHECK (
        response_contract_status IS NULL
        OR response_contract_status IN (
            'CAPTURED',
            'NOT_JSON',
            'INVALID_JSON',
            'TRUNCATED',
            'DISABLED',
            'ERROR'
        )
    );
