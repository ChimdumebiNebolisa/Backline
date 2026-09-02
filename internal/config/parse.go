package config

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"regexp"
	"strings"

	"gopkg.in/yaml.v3"
)

var substitutionPattern = regexp.MustCompile(`\$\{([A-Za-z_][A-Za-z0-9_]*)\}`)

type ParseResult struct {
	Config           Config
	ResolvedYAML     []byte
	RegisteredValues []string
}

func Parse(data []byte, environment map[string]string) (ParseResult, error) {
	return ParseWithOptions(data, environment, ParseOptions{})
}

type ParseOptions struct {
	BaseRef string
}

func ParseWithOptions(data []byte, environment map[string]string, options ParseOptions) (ParseResult, error) {
	var document yaml.Node
	decoder := yaml.NewDecoder(bytes.NewReader(data))
	if err := decoder.Decode(&document); err != nil {
		return ParseResult{}, fmt.Errorf("parse YAML: %w", err)
	}
	if len(document.Content) == 0 {
		return ParseResult{}, fmt.Errorf("configuration is empty")
	}
	var trailing yaml.Node
	if err := decoder.Decode(&trailing); err != nil && !errors.Is(err, io.EOF) {
		return ParseResult{}, fmt.Errorf("parse trailing YAML: %w", err)
	}
	if len(trailing.Content) != 0 {
		return ParseResult{}, fmt.Errorf("configuration must contain one YAML document")
	}
	if err := rejectDuplicateKeys(&document, "configuration"); err != nil {
		return ParseResult{}, err
	}

	registered := make([]string, 0)
	if err := substituteNode(&document, environment, &registered); err != nil {
		return ParseResult{}, err
	}
	resolved, err := yaml.Marshal(document.Content[0])
	if err != nil {
		return ParseResult{}, fmt.Errorf("encode substituted YAML: %w", err)
	}

	config := defaultConfig()
	strict := yaml.NewDecoder(bytes.NewReader(resolved))
	strict.KnownFields(true)
	if err := strict.Decode(&config); err != nil {
		return ParseResult{}, fmt.Errorf("decode configuration: %w", err)
	}
	if options.BaseRef != "" {
		config.Revisions.Base = options.BaseRef
	}
	applyNestedDefaults(&config)
	if err := Validate(config); err != nil {
		return ParseResult{}, err
	}
	effective, err := yaml.Marshal(config)
	if err != nil {
		return ParseResult{}, fmt.Errorf("encode effective configuration: %w", err)
	}
	return ParseResult{Config: config, ResolvedYAML: effective, RegisteredValues: uniqueNonempty(registered)}, nil
}

func defaultConfig() Config {
	requireCleanCheckout := true
	return Config{
		Revisions: Revisions{RequireCleanCheckout: &requireCleanCheckout},
		Artifacts: Artifacts{
			Directory:             ".backline/runs",
			MaxLogBytesPerCommand: 1_048_576,
		},
		SharedEnvironment: SharedEnvironment{
			StartupTimeoutSeconds:  120,
			ShutdownTimeoutSeconds: 30,
		},
		Workloads: Workloads{Defaults: WorkloadDefaults{CWDRevision: "candidate", TimeoutSeconds: 60}},
	}
}

func applyNestedDefaults(config *Config) {
	for index := range config.Release.Components {
		component := &config.Release.Components[index]
		if component.Run.Scheme == "" {
			component.Run.Scheme = "http"
		}
		if component.Run.Readiness.TimeoutSeconds == 0 {
			component.Run.Readiness.TimeoutSeconds = 90
		}
	}
	for _, hooks := range [][]Hook{config.Lifecycle.Bootstrap, config.Lifecycle.Transition, config.Lifecycle.CandidateOnly, config.Lifecycle.Rollback} {
		for index := range hooks {
			if hooks[index].TimeoutSeconds == 0 {
				hooks[index].TimeoutSeconds = 60
			}
		}
	}
	groups := [][]Scenario{
		config.Workloads.Baseline,
		config.Workloads.Controls.BaseAfterTransition.Scenarios,
		config.Workloads.Controls.CandidateBeforeCoexistence.Scenarios,
		config.Workloads.Controls.BaseWithCandidateRunning.Scenarios,
		config.Workloads.Coexistence.BaseToCandidate,
		config.Workloads.Coexistence.CandidateToBase,
		config.Workloads.Coexistence.Alternating,
		config.Workloads.CandidateTraffic,
		config.Workloads.Rollback.Scenarios,
	}
	for _, scenarios := range groups {
		for scenarioIndex := range scenarios {
			scenario := &scenarios[scenarioIndex]
			if scenario.Repeat == 0 {
				scenario.Repeat = 1
			}
			for stepIndex := range scenario.Steps {
				step := &scenario.Steps[stepIndex]
				if step.CWDRevision == "" {
					step.CWDRevision = config.Workloads.Defaults.CWDRevision
				}
				if step.TimeoutSeconds == 0 {
					step.TimeoutSeconds = config.Workloads.Defaults.TimeoutSeconds
				}
			}
		}
	}
}

func rejectDuplicateKeys(node *yaml.Node, path string) error {
	if node.Kind == yaml.MappingNode {
		seen := make(map[string]struct{}, len(node.Content)/2)
		for index := 0; index < len(node.Content); index += 2 {
			key := node.Content[index]
			if _, exists := seen[key.Value]; exists {
				return fmt.Errorf("duplicate YAML key %q at %s", key.Value, path)
			}
			seen[key.Value] = struct{}{}
			if err := rejectDuplicateKeys(node.Content[index+1], path+"."+key.Value); err != nil {
				return err
			}
		}
		return nil
	}
	for _, child := range node.Content {
		if err := rejectDuplicateKeys(child, path); err != nil {
			return err
		}
	}
	return nil
}

func substituteNode(node *yaml.Node, environment map[string]string, registered *[]string) error {
	if node.Kind == yaml.MappingNode {
		for index := 0; index < len(node.Content); index += 2 {
			if strings.Contains(node.Content[index].Value, "${") {
				return fmt.Errorf("environment substitution is not allowed in YAML keys")
			}
			if err := substituteNode(node.Content[index+1], environment, registered); err != nil {
				return err
			}
		}
		return nil
	}
	if node.Kind == yaml.ScalarNode && node.Tag == "!!str" {
		missing := ""
		replaced := substitutionPattern.ReplaceAllStringFunc(node.Value, func(match string) string {
			name := substitutionPattern.FindStringSubmatch(match)[1]
			value, ok := environment[name]
			if !ok {
				missing = name
				return match
			}
			if value != "" {
				*registered = append(*registered, value)
			}
			return value
		})
		if missing != "" {
			return fmt.Errorf("environment variable %s is required", missing)
		}
		if strings.Contains(replaced, "${") {
			return fmt.Errorf("unsupported environment expansion in %q; only ${NAME} is supported", node.Value)
		}
		node.Value = replaced
	}
	for _, child := range node.Content {
		if err := substituteNode(child, environment, registered); err != nil {
			return err
		}
	}
	return nil
}

func uniqueNonempty(values []string) []string {
	seen := make(map[string]struct{}, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		if value == "" {
			continue
		}
		if _, exists := seen[value]; exists {
			continue
		}
		seen[value] = struct{}{}
		result = append(result, value)
	}
	return result
}
