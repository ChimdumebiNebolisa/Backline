package model

import "time"

type ExitCode int

const (
	ExitPass               ExitCode = 0
	ExitCompatibilityFail  ExitCode = 10
	ExitInconclusive       ExitCode = 20
	ExitConfigInvalid      ExitCode = 21
	ExitPrerequisite       ExitCode = 22
	ExitOrchestrationError ExitCode = 23
	ExitInternalError      ExitCode = 24
	ExitCleanupFailed      ExitCode = 25
)

type VerdictStatus string

const (
	VerdictPass         VerdictStatus = "PASS"
	VerdictFail         VerdictStatus = "FAIL"
	VerdictInconclusive VerdictStatus = "INCONCLUSIVE"
)

type OverallStatus string

const (
	OverallPass         OverallStatus = "PASS"
	OverallFail         OverallStatus = "FAIL"
	OverallInconclusive OverallStatus = "INCONCLUSIVE"
	OverallError        OverallStatus = "ERROR"
)

type StageOutcome string

const (
	StagePass       StageOutcome = "PASS"
	StageFail       StageOutcome = "FAIL"
	StageError      StageOutcome = "ERROR"
	StageSkipped    StageOutcome = "SKIPPED"
	StageIncomplete StageOutcome = "INCOMPLETE"
)

type RollbackMode string

const (
	RollbackRaw      RollbackMode = "RAW"
	RollbackPrepared RollbackMode = "PREPARED"
)

type ReasonCode string

const (
	ReasonNone                             ReasonCode = "NONE"
	ReasonConfigInvalid                    ReasonCode = "CONFIG_INVALID"
	ReasonSafetyPolicyBlocked              ReasonCode = "SAFETY_POLICY_BLOCKED"
	ReasonPrerequisiteUnavailable          ReasonCode = "PREREQUISITE_UNAVAILABLE"
	ReasonBuildFailed                      ReasonCode = "BUILD_FAILED"
	ReasonSharedEnvironmentFailed          ReasonCode = "SHARED_ENVIRONMENT_FAILED"
	ReasonBootstrapFailed                  ReasonCode = "BOOTSTRAP_FAILED"
	ReasonBaseStartupFailed                ReasonCode = "BASE_STARTUP_FAILED"
	ReasonBaselineFailed                   ReasonCode = "BASELINE_FAILED"
	ReasonTransitionHookFailed             ReasonCode = "TRANSITION_HOOK_FAILED"
	ReasonBaseLostReadiness                ReasonCode = "BASE_LOST_READINESS"
	ReasonBaseAfterTransitionControlFailed ReasonCode = "BASE_AFTER_TRANSITION_CONTROL_FAILED"
	ReasonCandidateStartupFailed           ReasonCode = "CANDIDATE_STARTUP_FAILED"
	ReasonCandidateControlFailed           ReasonCode = "CANDIDATE_CONTROL_FAILED"
	ReasonBaseWithCandidateControlFailed   ReasonCode = "BASE_WITH_CANDIDATE_CONTROL_FAILED"
	ReasonCoexistenceScenarioFailed        ReasonCode = "COEXISTENCE_SCENARIO_FAILED"
	ReasonRequiredComponentExited          ReasonCode = "REQUIRED_COMPONENT_EXITED"
	ReasonCommandLaunchFailed              ReasonCode = "COMMAND_LAUNCH_FAILED"
	ReasonCandidateOnlyHookFailed          ReasonCode = "CANDIDATE_ONLY_HOOK_FAILED"
	ReasonCandidateTrafficFailed           ReasonCode = "CANDIDATE_TRAFFIC_FAILED"
	ReasonCandidateTrafficIncomplete       ReasonCode = "CANDIDATE_TRAFFIC_INCOMPLETE"
	ReasonNoCandidateMutationScenarioRan   ReasonCode = "NO_CANDIDATE_MUTATION_SCENARIO_RAN"
	ReasonRollbackHookFailed               ReasonCode = "ROLLBACK_HOOK_FAILED"
	ReasonBaseRestartFailed                ReasonCode = "BASE_RESTART_FAILED"
	ReasonRollbackScenarioFailed           ReasonCode = "ROLLBACK_SCENARIO_FAILED"
	ReasonInterrupted                      ReasonCode = "INTERRUPTED"
	ReasonOrchestrationError               ReasonCode = "ORCHESTRATION_ERROR"
	ReasonInternalError                    ReasonCode = "INTERNAL_ERROR"
	ReasonCleanupFailed                    ReasonCode = "CLEANUP_FAILED"
)

func AllReasonCodes() []ReasonCode {
	return []ReasonCode{
		ReasonNone, ReasonConfigInvalid, ReasonSafetyPolicyBlocked, ReasonPrerequisiteUnavailable,
		ReasonBuildFailed, ReasonSharedEnvironmentFailed, ReasonBootstrapFailed, ReasonBaseStartupFailed,
		ReasonBaselineFailed, ReasonTransitionHookFailed, ReasonBaseLostReadiness,
		ReasonBaseAfterTransitionControlFailed, ReasonCandidateStartupFailed, ReasonCandidateControlFailed,
		ReasonBaseWithCandidateControlFailed, ReasonCoexistenceScenarioFailed, ReasonRequiredComponentExited,
		ReasonCommandLaunchFailed, ReasonCandidateOnlyHookFailed, ReasonCandidateTrafficFailed,
		ReasonCandidateTrafficIncomplete, ReasonNoCandidateMutationScenarioRan, ReasonRollbackHookFailed,
		ReasonBaseRestartFailed, ReasonRollbackScenarioFailed, ReasonInterrupted, ReasonOrchestrationError,
		ReasonInternalError, ReasonCleanupFailed,
	}
}

type Verdict struct {
	Status      VerdictStatus `json:"status"`
	ReasonCodes []ReasonCode  `json:"reason_codes"`
	Message     string        `json:"message,omitempty"`
}

type ConfigIdentity struct {
	Path      string `json:"path"`
	SourceSHA string `json:"source_sha"`
	SHA256    string `json:"sha256"`
}

type RevisionIdentity struct {
	Ref string `json:"ref"`
	SHA string `json:"sha"`
}

type EnvironmentIdentity struct {
	EnvFileDisplay      string            `json:"env_file_display,omitempty"`
	EnvFileExternal     bool              `json:"env_file_external"`
	EnvFileSHA256       string            `json:"env_file_sha256,omitempty"`
	DockerContext       string            `json:"docker_context,omitempty"`
	DockerVersion       string            `json:"docker_version,omitempty"`
	ComposeVersion      string            `json:"compose_version,omitempty"`
	SelectedServices    []string          `json:"selected_services"`
	Networks            []string          `json:"networks"`
	SharedServiceImages map[string]string `json:"shared_service_images"`
}

type StageResult struct {
	Name                 string       `json:"name"`
	Outcome              StageOutcome `json:"outcome"`
	ReasonCodes          []ReasonCode `json:"reason_codes,omitempty"`
	StartedAt            time.Time    `json:"started_at,omitempty"`
	FinishedAt           time.Time    `json:"finished_at,omitempty"`
	DurationMS           int64        `json:"duration_ms,omitempty"`
	Message              string       `json:"message,omitempty"`
	Revision             string       `json:"revision,omitempty"`
	Runner               string       `json:"runner,omitempty"`
	Command              []string     `json:"command,omitempty"`
	ExitCode             *int         `json:"exit_code,omitempty"`
	LogPath              string       `json:"log_path,omitempty"`
	Signal               string       `json:"signal,omitempty"`
	TerminationAttempted bool         `json:"termination_attempted"`
	TerminationSucceeded bool         `json:"termination_succeeded"`
	Truncated            bool         `json:"truncated"`
}

type StepResult struct {
	Stage                string       `json:"stage"`
	Scenario             string       `json:"scenario"`
	Step                 string       `json:"step"`
	Target               string       `json:"target,omitempty"`
	Outcome              StageOutcome `json:"outcome"`
	ReasonCode           ReasonCode   `json:"reason_code"`
	StartedAt            time.Time    `json:"started_at"`
	FinishedAt           time.Time    `json:"finished_at"`
	ExitCode             *int         `json:"exit_code,omitempty"`
	TimedOut             bool         `json:"timed_out"`
	Signal               string       `json:"signal,omitempty"`
	TerminationAttempted bool         `json:"termination_attempted"`
	TerminationSucceeded bool         `json:"termination_succeeded"`
	DurationMS           int64        `json:"duration_ms"`
	LogPath              string       `json:"log_path,omitempty"`
	Message              string       `json:"message,omitempty"`
	Command              []string     `json:"command,omitempty"`
	CWDRevision          string       `json:"cwd_revision,omitempty"`
	RevisionSHA          string       `json:"revision_sha,omitempty"`
	WorkingDirectory     string       `json:"working_directory,omitempty"`
	Truncated            bool         `json:"truncated"`
}

type OperationalError struct {
	Stage      string     `json:"stage,omitempty"`
	ReasonCode ReasonCode `json:"reason_code"`
	Message    string     `json:"message"`
}

type CleanupResult struct {
	Status      StageOutcome `json:"status"`
	ReasonCodes []ReasonCode `json:"reason_codes"`
	Remaining   []string     `json:"remaining,omitempty"`
	DurationMS  int64        `json:"duration_ms"`
}

type Summary struct {
	SchemaVersion      int                     `json:"schema_version"`
	RunID              string                  `json:"run_id"`
	Status             OverallStatus           `json:"status"`
	ExitCode           ExitCode                `json:"exit_code"`
	StartedAt          time.Time               `json:"started_at"`
	FinishedAt         time.Time               `json:"finished_at"`
	Config             ConfigIdentity          `json:"config"`
	Base               RevisionIdentity        `json:"base"`
	Candidate          RevisionIdentity        `json:"candidate"`
	Environment        EnvironmentIdentity     `json:"environment"`
	ComponentImages    map[string]string       `json:"component_images"`
	ComponentEndpoints map[string]string       `json:"component_endpoints"`
	RollbackMode       RollbackMode            `json:"rollback_mode"`
	Verdicts           map[string]Verdict      `json:"verdicts"`
	Controls           map[string]StageOutcome `json:"controls"`
	Stages             []StageResult           `json:"stages"`
	Scenarios          []StepResult            `json:"scenarios"`
	OperationalErrors  []OperationalError      `json:"operational_errors"`
	Artifacts          map[string]string       `json:"artifacts"`
	Cleanup            CleanupResult           `json:"cleanup"`
	Warnings           []string                `json:"warnings"`
}
