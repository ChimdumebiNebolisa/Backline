package config

type Config struct {
	Version           int               `yaml:"version" json:"version"`
	Revisions         Revisions         `yaml:"revisions" json:"revisions"`
	Artifacts         Artifacts         `yaml:"artifacts" json:"artifacts"`
	SharedEnvironment SharedEnvironment `yaml:"shared_environment" json:"shared_environment"`
	Security          Security          `yaml:"security" json:"security"`
	Release           Release           `yaml:"release" json:"release"`
	Lifecycle         Lifecycle         `yaml:"lifecycle" json:"lifecycle"`
	Workloads         Workloads         `yaml:"workloads" json:"workloads"`
}

type Revisions struct {
	Base                 string `yaml:"base" json:"base"`
	RequireCleanCheckout *bool  `yaml:"require_clean_checkout" json:"require_clean_checkout"`
}

func (r Revisions) CleanCheckoutRequired() bool {
	return r.RequireCleanCheckout == nil || *r.RequireCleanCheckout
}

type Artifacts struct {
	Directory             string `yaml:"directory" json:"directory"`
	MaxLogBytesPerCommand int64  `yaml:"max_log_bytes_per_command" json:"max_log_bytes_per_command"`
}

type SharedEnvironment struct {
	ComposeFile            string   `yaml:"compose_file" json:"compose_file"`
	Services               []string `yaml:"services" json:"services"`
	EnvFile                string   `yaml:"env_file" json:"env_file,omitempty"`
	StartupTimeoutSeconds  int      `yaml:"startup_timeout_seconds" json:"startup_timeout_seconds"`
	ShutdownTimeoutSeconds int      `yaml:"shutdown_timeout_seconds" json:"shutdown_timeout_seconds"`
}

type Security struct {
	PassthroughEnvironment []string `yaml:"passthrough_environment" json:"passthrough_environment"`
	RedactEnvironment      []string `yaml:"redact_environment" json:"redact_environment"`
}

type Release struct {
	Components []Component `yaml:"components" json:"components"`
}

type Component struct {
	Name  string         `yaml:"name" json:"name"`
	Build ComponentBuild `yaml:"build" json:"build"`
	Run   ComponentRun   `yaml:"run" json:"run"`
}

type ComponentBuild struct {
	Context    string `yaml:"context" json:"context"`
	Dockerfile string `yaml:"dockerfile" json:"dockerfile"`
}

type ComponentRun struct {
	Command              []string          `yaml:"command" json:"command,omitempty"`
	InternalPort         int               `yaml:"internal_port" json:"internal_port,omitempty"`
	Scheme               string            `yaml:"scheme" json:"scheme,omitempty"`
	Environment          map[string]string `yaml:"environment" json:"environment,omitempty"`
	BaseEnvironment      map[string]string `yaml:"base_environment" json:"base_environment,omitempty"`
	CandidateEnvironment map[string]string `yaml:"candidate_environment" json:"candidate_environment,omitempty"`
	Readiness            Readiness         `yaml:"readiness" json:"readiness"`
}

type Readiness struct {
	Type           string   `yaml:"type" json:"type"`
	Path           string   `yaml:"path" json:"path,omitempty"`
	ExpectedStatus int      `yaml:"expected_status" json:"expected_status,omitempty"`
	Command        []string `yaml:"command" json:"command,omitempty"`
	TimeoutSeconds int      `yaml:"timeout_seconds" json:"timeout_seconds"`
}

type Lifecycle struct {
	Bootstrap     []Hook `yaml:"bootstrap" json:"bootstrap"`
	Transition    []Hook `yaml:"transition" json:"transition"`
	CandidateOnly []Hook `yaml:"candidate_only" json:"candidate_only"`
	Rollback      []Hook `yaml:"rollback" json:"rollback"`
}

type Hook struct {
	Name             string            `yaml:"name" json:"name"`
	Revision         string            `yaml:"revision" json:"revision"`
	Runner           HookRunner        `yaml:"runner" json:"runner"`
	Command          []string          `yaml:"command" json:"command"`
	Environment      map[string]string `yaml:"environment" json:"environment,omitempty"`
	WorkingDirectory string            `yaml:"working_directory" json:"working_directory,omitempty"`
	TimeoutSeconds   int               `yaml:"timeout_seconds" json:"timeout_seconds"`
}

type HookRunner struct {
	Type      string `yaml:"type" json:"type"`
	Component string `yaml:"component" json:"component,omitempty"`
}

type Workloads struct {
	Defaults         WorkloadDefaults `yaml:"defaults" json:"defaults"`
	Baseline         []Scenario       `yaml:"baseline" json:"baseline"`
	Controls         Controls         `yaml:"controls" json:"controls"`
	Coexistence      Coexistence      `yaml:"coexistence" json:"coexistence"`
	CandidateTraffic []Scenario       `yaml:"candidate_traffic" json:"candidate_traffic"`
	Rollback         ScenarioGroup    `yaml:"rollback" json:"rollback"`
}

type WorkloadDefaults struct {
	CWDRevision    string `yaml:"cwd_revision" json:"cwd_revision"`
	TimeoutSeconds int    `yaml:"timeout_seconds" json:"timeout_seconds"`
}

type Controls struct {
	BaseAfterTransition        ScenarioGroup `yaml:"base_after_transition" json:"base_after_transition"`
	CandidateBeforeCoexistence ScenarioGroup `yaml:"candidate_before_coexistence" json:"candidate_before_coexistence"`
	BaseWithCandidateRunning   ScenarioGroup `yaml:"base_with_candidate_running" json:"base_with_candidate_running"`
}

type Coexistence struct {
	BaseToCandidate []Scenario `yaml:"base_to_candidate" json:"base_to_candidate"`
	CandidateToBase []Scenario `yaml:"candidate_to_base" json:"candidate_to_base"`
	Alternating     []Scenario `yaml:"alternating" json:"alternating"`
}

type ScenarioGroup struct {
	ReuseBaseline bool       `yaml:"reuse_baseline" json:"reuse_baseline"`
	Scenarios     []Scenario `yaml:"scenarios" json:"scenarios"`
}

type Scenario struct {
	Name         string `yaml:"name" json:"name"`
	MutatesState bool   `yaml:"mutates_state" json:"mutates_state,omitempty"`
	Repeat       int    `yaml:"repeat" json:"repeat"`
	Steps        []Step `yaml:"steps" json:"steps"`
}

type Step struct {
	Name             string            `yaml:"name" json:"name"`
	Target           string            `yaml:"target" json:"target,omitempty"`
	Command          []string          `yaml:"command" json:"command"`
	CWDRevision      string            `yaml:"cwd_revision" json:"cwd_revision,omitempty"`
	WorkingDirectory string            `yaml:"working_directory" json:"working_directory,omitempty"`
	Environment      map[string]string `yaml:"environment" json:"environment,omitempty"`
	TimeoutSeconds   int               `yaml:"timeout_seconds" json:"timeout_seconds"`
}

func (c Config) Component(name string) (Component, bool) {
	for _, component := range c.Release.Components {
		if component.Name == name {
			return component, true
		}
	}
	return Component{}, false
}
