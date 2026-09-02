package config

import (
	"fmt"
	"net/url"
	"regexp"
	"sort"
	"strings"
)

var (
	namePattern = regexp.MustCompile(`^[a-z][a-z0-9-]*$`)
	envPattern  = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*$`)
)

type validationErrors []string

func (v validationErrors) Error() string {
	return "configuration invalid: " + strings.Join(v, "; ")
}

func Validate(config Config) error {
	var problems validationErrors
	if config.Version != 1 {
		problems = append(problems, "version must be 1")
	}
	if config.Revisions.Base == "" {
		problems = append(problems, "revisions.base is required unless --base-ref or supported CI metadata supplies it")
	}
	if config.Artifacts.Directory == "" {
		problems = append(problems, "artifacts.directory is required")
	}
	if config.Artifacts.MaxLogBytesPerCommand < 4096 || config.Artifacts.MaxLogBytesPerCommand > 104_857_600 {
		problems = append(problems, "artifacts.max_log_bytes_per_command must be between 4096 and 104857600")
	}
	checkTimeout(&problems, "shared_environment.startup_timeout_seconds", config.SharedEnvironment.StartupTimeoutSeconds, 3600)
	checkTimeout(&problems, "shared_environment.shutdown_timeout_seconds", config.SharedEnvironment.ShutdownTimeoutSeconds, 3600)
	if config.SharedEnvironment.ComposeFile == "" {
		problems = append(problems, "shared_environment.compose_file is required")
	}
	checkUniqueNames(&problems, "shared_environment.services", config.SharedEnvironment.Services, false)
	if len(config.SharedEnvironment.Services) == 0 {
		problems = append(problems, "shared_environment.services requires at least one service")
	}

	components := make(map[string]Component)
	normalized := make(map[string]string)
	addressable := 0
	for index, component := range config.Release.Components {
		path := fmt.Sprintf("release.components[%d]", index)
		if !namePattern.MatchString(component.Name) {
			problems = append(problems, path+".name must match [a-z][a-z0-9-]*")
		}
		if _, exists := components[component.Name]; exists {
			problems = append(problems, "duplicate component name "+component.Name)
		}
		components[component.Name] = component
		envName := normalizeName(component.Name)
		if previous, exists := normalized[envName]; exists && previous != component.Name {
			problems = append(problems, fmt.Sprintf("component names %s and %s normalize to the same environment name", previous, component.Name))
		}
		normalized[envName] = component.Name
		if component.Build.Context == "" || component.Build.Dockerfile == "" {
			problems = append(problems, path+" requires build.context and build.dockerfile")
		}
		if component.Run.InternalPort < 0 || component.Run.InternalPort > 65535 {
			problems = append(problems, path+".run.internal_port must be between 1 and 65535 when present")
		}
		if component.Run.InternalPort > 0 {
			addressable++
			if parsed, err := url.Parse(component.Run.Scheme + "://example.invalid"); err != nil || parsed.Scheme == "" {
				problems = append(problems, path+".run.scheme is invalid")
			}
		}
		validateReadiness(&problems, path+".run.readiness", component)
		validateOptionalCommand(&problems, path+".run.command", component.Run.Command)
		validateEnvironment(&problems, path+".run.environment", component.Run.Environment)
		validateEnvironment(&problems, path+".run.base_environment", component.Run.BaseEnvironment)
		validateEnvironment(&problems, path+".run.candidate_environment", component.Run.CandidateEnvironment)
	}
	if len(config.Release.Components) == 0 {
		problems = append(problems, "release.components requires at least one component")
	}
	if addressable == 0 {
		problems = append(problems, "at least one release component must define run.internal_port")
	}
	for _, service := range config.SharedEnvironment.Services {
		if _, collides := components[service]; collides {
			problems = append(problems, "shared service name collides with release component "+service)
		}
		for component := range components {
			if service == "base-"+component || service == "candidate-"+component {
				problems = append(problems, fmt.Sprintf("shared service %s collides with a stable release alias", service))
			}
		}
	}

	validateEnvironmentNames(&problems, "security.passthrough_environment", config.Security.PassthroughEnvironment)
	validateEnvironmentNames(&problems, "security.redact_environment", config.Security.RedactEnvironment)
	validateHooks(&problems, "lifecycle.bootstrap", config.Lifecycle.Bootstrap, "base", components)
	validateHooks(&problems, "lifecycle.transition", config.Lifecycle.Transition, "candidate", components)
	validateHooks(&problems, "lifecycle.candidate_only", config.Lifecycle.CandidateOnly, "candidate", components)
	validateHooks(&problems, "lifecycle.rollback", config.Lifecycle.Rollback, "either", components)

	if config.Workloads.Defaults.CWDRevision != "base" && config.Workloads.Defaults.CWDRevision != "candidate" {
		problems = append(problems, "workloads.defaults.cwd_revision must be base or candidate")
	}
	checkTimeout(&problems, "workloads.defaults.timeout_seconds", config.Workloads.Defaults.TimeoutSeconds, 3600)
	validateScenarioList(&problems, "workloads.baseline", config.Workloads.Baseline, components, roleRuleBase)
	if len(config.Workloads.Baseline) == 0 {
		problems = append(problems, "workloads.baseline requires at least one scenario")
	}
	validateScenarioGroup(&problems, "workloads.controls.base_after_transition", config.Workloads.Controls.BaseAfterTransition, components, roleRuleBase)
	validateScenarioGroup(&problems, "workloads.controls.candidate_before_coexistence", config.Workloads.Controls.CandidateBeforeCoexistence, components, roleRuleCandidate)
	validateScenarioGroup(&problems, "workloads.controls.base_with_candidate_running", config.Workloads.Controls.BaseWithCandidateRunning, components, roleRuleBase)
	validateScenarioList(&problems, "workloads.coexistence.base_to_candidate", config.Workloads.Coexistence.BaseToCandidate, components, roleRuleBaseToCandidate)
	validateScenarioList(&problems, "workloads.coexistence.candidate_to_base", config.Workloads.Coexistence.CandidateToBase, components, roleRuleCandidateToBase)
	validateScenarioList(&problems, "workloads.coexistence.alternating", config.Workloads.Coexistence.Alternating, components, roleRuleAlternating)
	if len(config.Workloads.Coexistence.BaseToCandidate) == 0 || len(config.Workloads.Coexistence.CandidateToBase) == 0 {
		problems = append(problems, "both directional coexistence groups require at least one scenario")
	}
	validateScenarioList(&problems, "workloads.candidate_traffic", config.Workloads.CandidateTraffic, components, roleRuleCandidate)
	mutating := false
	for _, scenario := range config.Workloads.CandidateTraffic {
		mutating = mutating || scenario.MutatesState
	}
	if len(config.Workloads.CandidateTraffic) == 0 || !mutating {
		problems = append(problems, "candidate_traffic requires at least one scenario with mutates_state: true")
	}
	validateScenarioGroup(&problems, "workloads.rollback", config.Workloads.Rollback, components, roleRuleBase)

	if len(problems) == 0 {
		return nil
	}
	sort.Strings(problems)
	return problems
}

type roleRule int

const (
	roleRuleAny roleRule = iota
	roleRuleBase
	roleRuleCandidate
	roleRuleBaseToCandidate
	roleRuleCandidateToBase
	roleRuleAlternating
)

func validateReadiness(problems *validationErrors, path string, component Component) {
	readiness := component.Run.Readiness
	checkTimeout(problems, path+".timeout_seconds", readiness.TimeoutSeconds, 3600)
	switch readiness.Type {
	case "http":
		if component.Run.InternalPort == 0 || readiness.Path == "" || !strings.HasPrefix(readiness.Path, "/") {
			*problems = append(*problems, path+" http readiness requires an addressable component and absolute path")
		}
		if readiness.ExpectedStatus != 0 && (readiness.ExpectedStatus < 100 || readiness.ExpectedStatus > 599) {
			*problems = append(*problems, path+".expected_status must be a valid HTTP status")
		}
	case "tcp":
		if component.Run.InternalPort == 0 {
			*problems = append(*problems, path+" tcp readiness requires an addressable component")
		}
	case "docker-health":
	case "command":
		validateCommand(problems, path+".command", readiness.Command)
	default:
		*problems = append(*problems, path+".type must be http, tcp, docker-health, or command")
	}
}

func validateHooks(problems *validationErrors, path string, hooks []Hook, requiredRevision string, components map[string]Component) {
	names := make(map[string]struct{})
	for index, hook := range hooks {
		hookPath := fmt.Sprintf("%s[%d]", path, index)
		if hook.Name == "" {
			*problems = append(*problems, hookPath+".name is required")
		}
		if _, exists := names[hook.Name]; exists {
			*problems = append(*problems, path+" contains duplicate hook "+hook.Name)
		}
		names[hook.Name] = struct{}{}
		if requiredRevision != "either" && hook.Revision != requiredRevision {
			*problems = append(*problems, hookPath+".revision must be "+requiredRevision)
		} else if requiredRevision == "either" && hook.Revision != "base" && hook.Revision != "candidate" {
			*problems = append(*problems, hookPath+".revision must be base or candidate")
		}
		switch hook.Runner.Type {
		case "host":
			if hook.Runner.Component != "" {
				*problems = append(*problems, hookPath+" host runner must not name a component")
			}
		case "component":
			if _, exists := components[hook.Runner.Component]; !exists {
				*problems = append(*problems, hookPath+" references unknown component "+hook.Runner.Component)
			}
		default:
			*problems = append(*problems, hookPath+".runner.type must be host or component")
		}
		validateCommand(problems, hookPath+".command", hook.Command)
		validateEnvironment(problems, hookPath+".environment", hook.Environment)
		checkTimeout(problems, hookPath+".timeout_seconds", hook.TimeoutSeconds, 3600)
	}
}

func validateScenarioGroup(problems *validationErrors, path string, group ScenarioGroup, components map[string]Component, rule roleRule) {
	if group.ReuseBaseline == (len(group.Scenarios) > 0) {
		*problems = append(*problems, path+" must select exactly one of reuse_baseline or scenarios")
	}
	validateScenarioList(problems, path+".scenarios", group.Scenarios, components, rule)
}

func validateScenarioList(problems *validationErrors, path string, scenarios []Scenario, components map[string]Component, rule roleRule) {
	names := make(map[string]struct{})
	for scenarioIndex, scenario := range scenarios {
		scenarioPath := fmt.Sprintf("%s[%d]", path, scenarioIndex)
		if scenario.Name == "" {
			*problems = append(*problems, scenarioPath+".name is required")
		}
		if _, exists := names[scenario.Name]; exists {
			*problems = append(*problems, path+" contains duplicate scenario "+scenario.Name)
		}
		names[scenario.Name] = struct{}{}
		if scenario.Repeat < 1 || scenario.Repeat > 100 {
			*problems = append(*problems, scenarioPath+".repeat must be between 1 and 100")
		}
		if len(scenario.Steps) == 0 {
			*problems = append(*problems, scenarioPath+" requires at least one step")
		}
		stepNames := make(map[string]struct{})
		roles := make([]string, 0, len(scenario.Steps))
		for stepIndex, step := range scenario.Steps {
			stepPath := fmt.Sprintf("%s.steps[%d]", scenarioPath, stepIndex)
			if step.Name == "" {
				*problems = append(*problems, stepPath+".name is required")
			}
			if _, exists := stepNames[step.Name]; exists {
				*problems = append(*problems, scenarioPath+" contains duplicate step "+step.Name)
			}
			stepNames[step.Name] = struct{}{}
			validateCommand(problems, stepPath+".command", step.Command)
			validateEnvironment(problems, stepPath+".environment", step.Environment)
			checkTimeout(problems, stepPath+".timeout_seconds", step.TimeoutSeconds, 3600)
			if step.CWDRevision != "base" && step.CWDRevision != "candidate" {
				*problems = append(*problems, stepPath+".cwd_revision must be base or candidate")
			}
			if step.Target == "" {
				continue
			}
			role, component, ok := parseTarget(step.Target)
			if !ok {
				*problems = append(*problems, stepPath+".target must use <base|candidate>.<component>")
				continue
			}
			definition, exists := components[component]
			if !exists || definition.Run.InternalPort == 0 {
				*problems = append(*problems, stepPath+".target references an unknown or headless component")
			}
			roles = append(roles, role)
			if (rule == roleRuleBase && role != "base") || (rule == roleRuleCandidate && role != "candidate") {
				*problems = append(*problems, stepPath+" targets the wrong revision role")
			}
		}
		if rule == roleRuleBaseToCandidate && !orderedRoles(roles, "base", "candidate") {
			*problems = append(*problems, scenarioPath+" requires an explicit base target followed later by candidate")
		}
		if rule == roleRuleCandidateToBase && !orderedRoles(roles, "candidate", "base") {
			*problems = append(*problems, scenarioPath+" requires an explicit candidate target followed later by base")
		}
		if rule == roleRuleAlternating && !(contains(roles, "base") && contains(roles, "candidate")) {
			*problems = append(*problems, scenarioPath+" requires explicit base and candidate targets")
		}
	}
}

func validateCommand(problems *validationErrors, path string, command []string) {
	if len(command) == 0 || command[0] == "" {
		*problems = append(*problems, path+" must be a nonempty argument array")
	}
}

func validateOptionalCommand(problems *validationErrors, path string, command []string) {
	if len(command) > 0 {
		validateCommand(problems, path, command)
	}
}

func validateEnvironment(problems *validationErrors, path string, environment map[string]string) {
	for name := range environment {
		if !envPattern.MatchString(name) {
			*problems = append(*problems, path+" contains invalid variable "+name)
		}
		if strings.HasPrefix(strings.ToUpper(name), "BACKLINE_") {
			*problems = append(*problems, path+" cannot set reserved variable "+name)
		}
	}
}

func validateEnvironmentNames(problems *validationErrors, path string, names []string) {
	seen := make(map[string]struct{})
	for _, name := range names {
		canonical := strings.ToUpper(name)
		if !envPattern.MatchString(name) || strings.HasPrefix(canonical, "BACKLINE_") {
			*problems = append(*problems, path+" contains invalid or reserved variable "+name)
		}
		if _, exists := seen[canonical]; exists {
			*problems = append(*problems, path+" contains duplicate variable "+name)
		}
		seen[canonical] = struct{}{}
	}
}

func checkUniqueNames(problems *validationErrors, path string, names []string, requirePattern bool) {
	seen := make(map[string]struct{})
	for _, name := range names {
		if name == "" || (requirePattern && !namePattern.MatchString(name)) {
			*problems = append(*problems, path+" contains an invalid name")
		}
		if _, exists := seen[name]; exists {
			*problems = append(*problems, path+" contains duplicate name "+name)
		}
		seen[name] = struct{}{}
	}
}

func checkTimeout(problems *validationErrors, path string, value, maximum int) {
	if value < 1 || value > maximum {
		*problems = append(*problems, fmt.Sprintf("%s must be between 1 and %d", path, maximum))
	}
}

func parseTarget(target string) (string, string, bool) {
	parts := strings.Split(target, ".")
	if len(parts) != 2 || (parts[0] != "base" && parts[0] != "candidate") || parts[1] == "" {
		return "", "", false
	}
	return parts[0], parts[1], true
}

func orderedRoles(roles []string, first, second string) bool {
	seenFirst := false
	for _, role := range roles {
		if role == first {
			seenFirst = true
		}
		if role == second && seenFirst {
			return true
		}
	}
	return false
}

func contains(values []string, wanted string) bool {
	for _, value := range values {
		if value == wanted {
			return true
		}
	}
	return false
}

func normalizeName(name string) string {
	return strings.ToUpper(strings.ReplaceAll(name, "-", "_"))
}
