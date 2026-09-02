package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func loadMinimal(t *testing.T) []byte {
	t.Helper()
	data, err := os.ReadFile(filepath.Join("testdata", "minimal.yml"))
	if err != nil {
		t.Fatal(err)
	}
	return data
}

func TestParseAppliesDefaultsAndSubstitutes(t *testing.T) {
	result, err := Parse(loadMinimal(t), map[string]string{"DATABASE_URL": "postgres://secret@example/app"})
	if err != nil {
		t.Fatal(err)
	}
	if result.Config.Artifacts.Directory != ".backline/runs" || result.Config.Artifacts.MaxLogBytesPerCommand != 1_048_576 {
		t.Fatalf("artifact defaults = %#v", result.Config.Artifacts)
	}
	component := result.Config.Release.Components[0]
	if component.Run.Readiness.TimeoutSeconds != 90 || component.Run.Environment["DATABASE_URL"] != "postgres://secret@example/app" {
		t.Fatalf("component = %#v", component)
	}
	if result.Config.Workloads.Baseline[0].Repeat != 1 || result.Config.Workloads.Baseline[0].Steps[0].TimeoutSeconds != 60 {
		t.Fatalf("workload defaults not applied")
	}
	if len(result.RegisteredValues) != 1 || result.RegisteredValues[0] != "postgres://secret@example/app" {
		t.Fatalf("registered = %#v", result.RegisteredValues)
	}
	effective := string(result.ResolvedYAML)
	for _, expected := range []string{"require_clean_checkout: true", "startup_timeout_seconds: 120", "shutdown_timeout_seconds: 30", "max_log_bytes_per_command: 1048576", "repeat: 1"} {
		if !strings.Contains(effective, expected) {
			t.Fatalf("effective configuration missing %q:\n%s", expected, effective)
		}
	}
}

func TestParseRejectsBoundsAndMissingControls(t *testing.T) {
	base := string(loadMinimal(t))
	for name, data := range map[string]string{
		"repeat":  strings.Replace(base, "name: base is healthy", "name: base is healthy\n      repeat: 101", 1),
		"timeout": strings.Replace(base, "workloads:\n", "workloads:\n  defaults:\n    cwd_revision: candidate\n    timeout_seconds: 3601\n", 1),
		"control": strings.Replace(base, "    base_after_transition:\n      reuse_baseline: true\n", "", 1),
	} {
		t.Run(name, func(t *testing.T) {
			if _, err := Parse([]byte(data), map[string]string{"DATABASE_URL": "value"}); err == nil {
				t.Fatal("expected validation error")
			}
		})
	}
}

func TestParseRejectsSharedServiceStableAliasCollision(t *testing.T) {
	configuration := strings.Replace(string(loadMinimal(t)), "services: [postgres]", "services: [base-api]", 1)
	if _, err := Parse([]byte(configuration), map[string]string{"DATABASE_URL": "value"}); err == nil || !strings.Contains(err.Error(), "stable release alias") {
		t.Fatalf("error=%v", err)
	}
}

func TestParseRejectsDuplicateUnknownAndUnsupportedExpansion(t *testing.T) {
	for name, replacement := range map[string]string{
		"duplicate": "version: 1\nversion: 1\n",
		"unknown":   strings.Replace(string(loadMinimal(t)), "version: 1", "version: 1\nunknown: true", 1),
		"expansion": strings.Replace(string(loadMinimal(t)), "${DATABASE_URL}", "${DATABASE_URL:-unsafe}", 1),
	} {
		t.Run(name, func(t *testing.T) {
			_, err := Parse([]byte(replacement), map[string]string{"DATABASE_URL": "value"})
			if err == nil {
				t.Fatal("expected error")
			}
		})
	}
}

func TestParseRejectsMalformedTrailingDocument(t *testing.T) {
	data := append(loadMinimal(t), []byte("\n---\ninvalid: [\n")...)
	if _, err := Parse(data, map[string]string{"DATABASE_URL": "value"}); err == nil || !strings.Contains(err.Error(), "trailing YAML") {
		t.Fatalf("error=%v", err)
	}
}

func TestDirectionValidationIsPerScenario(t *testing.T) {
	data := strings.Replace(string(loadMinimal(t)), "target: candidate.api\n            command: [\"python\", \"scripts/backline/read.py\"]", "target: base.api\n            command: [\"python\", \"scripts/backline/read.py\"]", 1)
	_, err := Parse([]byte(data), map[string]string{"DATABASE_URL": "value"})
	if err == nil || !strings.Contains(err.Error(), "followed later by candidate") {
		t.Fatalf("error = %v", err)
	}
}

func TestReservedEnvironmentRejected(t *testing.T) {
	data := strings.Replace(string(loadMinimal(t)), "DATABASE_URL: ${DATABASE_URL}", "BACKLINE_RUN_ID: bad", 1)
	_, err := Parse([]byte(data), map[string]string{})
	if err == nil || !strings.Contains(err.Error(), "reserved") {
		t.Fatalf("error = %v", err)
	}
}

func TestEnvironmentFileAndProcessPrecedence(t *testing.T) {
	fileValues, registered, err := ParseEnvironmentFile([]byte("TOKEN='file-secret'\nEMPTY=\n"))
	if err != nil {
		t.Fatal(err)
	}
	merged := MergeEnvironment(fileValues, map[string]string{"TOKEN": "process-secret"})
	if merged["TOKEN"] != "process-secret" || len(registered) != 1 || registered[0] != "file-secret" {
		t.Fatalf("merged=%#v registered=%#v", merged, registered)
	}
}

func TestComposeSafety(t *testing.T) {
	safe := []byte(`{"services":{"postgres":{"healthcheck":{"test":["CMD","true"]}}},"volumes":{"data":{}},"networks":{"default":{}}}`)
	if err := ValidateComposeJSON(safe, t.TempDir(), []string{"postgres"}, false); err != nil {
		t.Fatal(err)
	}
	unsafe := []byte(`{"services":{"postgres":{"healthcheck":{"test":["CMD","true"]},"privileged":true}}}`)
	if err := ValidateComposeJSON(unsafe, t.TempDir(), []string{"postgres"}, false); err == nil {
		t.Fatal("expected unsafe Compose rejection")
	}
	if err := ValidateComposeJSON(unsafe, t.TempDir(), []string{"postgres"}, true); err != nil {
		t.Fatalf("unsafe override rejected: %v", err)
	}
	fixedPort := []byte(`{"services":{"postgres":{"healthcheck":{"test":["CMD","true"]},"ports":[{"target":5432,"published":"5432","host_ip":"127.0.0.1"}]}}}`)
	if err := ValidateComposeJSON(fixedPort, t.TempDir(), []string{"postgres"}, true); err != nil {
		t.Fatalf("fixed port override rejected: %v", err)
	}
	external := []byte(`{"name":"demo","services":{"postgres":{"healthcheck":{"test":["CMD","true"]}}},"networks":{"shared":{"name":"shared","external":true}}}`)
	if err := ValidateComposeJSON(external, t.TempDir(), []string{"postgres"}, false); err == nil {
		t.Fatal("expected external network rejection")
	}
	if err := ValidateComposeJSON(external, t.TempDir(), []string{"postgres"}, true); err != nil {
		t.Fatalf("external network override rejected: %v", err)
	}
	global := []byte(`{"name":"demo","services":{"postgres":{"healthcheck":{"test":["CMD","true"]}}},"networks":{"shared":{"name":"global-shared"}}}`)
	if err := ValidateComposeJSON(global, t.TempDir(), []string{"postgres"}, true); err == nil {
		t.Fatal("explicit global name must remain blocked")
	}
}
