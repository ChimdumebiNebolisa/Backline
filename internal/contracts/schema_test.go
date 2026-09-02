package contracts

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"testing"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/artifacts"
	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	"github.com/ChimdumebiNebolisa/Backline/internal/model"
)

func TestSummarySchemaTracksArtifactContract(t *testing.T) {
	schema := loadSchema(t, "summary-v1.schema.json")
	root := artifactMap(t, fixtureSummary())
	assertTopLevelContract(t, schema, root)
	definitions := schema["$defs"].(map[string]any)
	assertDefinitionContract(t, definitions["stage"].(map[string]any), root["stages"].([]any)[0].(map[string]any))
	assertDefinitionContract(t, definitions["step"].(map[string]any), root["scenarios"].([]any)[0].(map[string]any))
	reasonDefinition := definitions["reason_code"].(map[string]any)
	actual := stringValues(reasonDefinition["enum"].([]any))
	expected := make([]string, 0)
	for _, reason := range model.AllReasonCodes() {
		expected = append(expected, string(reason))
	}
	sort.Strings(actual)
	sort.Strings(expected)
	if !equalStrings(actual, expected) {
		t.Fatalf("schema reason codes = %v, model = %v", actual, expected)
	}
}

func assertDefinitionContract(t *testing.T, definition, artifact map[string]any) {
	t.Helper()
	properties := definition["properties"].(map[string]any)
	for _, required := range stringValues(definition["required"].([]any)) {
		if _, exists := artifact[required]; !exists {
			t.Errorf("artifact definition missing required field %q", required)
		}
	}
	for name := range artifact {
		if _, exists := properties[name]; !exists {
			t.Errorf("artifact definition field %q is absent from schema", name)
		}
	}
}

func TestPRDConfigurationExamplesParse(t *testing.T) {
	_, source, _, _ := runtime.Caller(0)
	contents, err := os.ReadFile(filepath.Join(filepath.Dir(source), "..", "..", "PRD.md"))
	if err != nil {
		t.Fatal(err)
	}
	prd := strings.ReplaceAll(string(contents), "\r\n", "\n")
	for _, heading := range []string{"### 14.1 Minimal example", "### 14.2 Complete example"} {
		start := strings.Index(prd, heading)
		if start < 0 {
			t.Fatalf("missing %s", heading)
		}
		fence := strings.Index(prd[start:], "```yaml\n")
		if fence < 0 {
			t.Fatalf("missing YAML fence after %s", heading)
		}
		bodyStart := start + fence + len("```yaml\n")
		bodyEnd := strings.Index(prd[bodyStart:], "\n```")
		if bodyEnd < 0 {
			t.Fatalf("unterminated YAML fence after %s", heading)
		}
		if _, err := config.Parse([]byte(prd[bodyStart:bodyStart+bodyEnd]), map[string]string{}); err != nil {
			t.Fatalf("%s does not parse: %v", heading, err)
		}
	}
}

func TestEventSchemaTracksEventArtifact(t *testing.T) {
	schema := loadSchema(t, "event-v1.schema.json")
	event := artifacts.Event{Timestamp: time.Now().UTC(), Type: "RUN_CREATED", Stage: "preflight", RunID: "bl-test", Details: map[string]any{}}
	encoded, err := json.Marshal(event)
	if err != nil {
		t.Fatal(err)
	}
	var root map[string]any
	if err := json.Unmarshal(encoded, &root); err != nil {
		t.Fatal(err)
	}
	assertTopLevelContract(t, schema, root)
}

func assertTopLevelContract(t *testing.T, schema, artifact map[string]any) {
	t.Helper()
	properties := schema["properties"].(map[string]any)
	for _, required := range stringValues(schema["required"].([]any)) {
		if _, exists := artifact[required]; !exists {
			t.Errorf("artifact missing required field %q", required)
		}
	}
	for name := range artifact {
		if _, exists := properties[name]; !exists {
			t.Errorf("artifact field %q is absent from schema", name)
		}
	}
}

func artifactMap(t *testing.T, value any) map[string]any {
	t.Helper()
	encoded, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	var result map[string]any
	if err := json.Unmarshal(encoded, &result); err != nil {
		t.Fatal(err)
	}
	return result
}

func loadSchema(t *testing.T, name string) map[string]any {
	t.Helper()
	_, source, _, _ := runtime.Caller(0)
	contents, err := os.ReadFile(filepath.Join(filepath.Dir(source), "..", "..", "schemas", name))
	if err != nil {
		t.Fatal(err)
	}
	var result map[string]any
	if err := json.Unmarshal(contents, &result); err != nil {
		t.Fatalf("invalid %s: %v", name, err)
	}
	return result
}

func stringValues(values []any) []string {
	result := make([]string, len(values))
	for index, value := range values {
		result[index] = value.(string)
	}
	return result
}

func equalStrings(left, right []string) bool {
	if len(left) != len(right) {
		return false
	}
	for index := range left {
		if left[index] != right[index] {
			return false
		}
	}
	return true
}

func fixtureSummary() model.Summary {
	now := time.Now().UTC()
	return model.Summary{
		SchemaVersion: 1, RunID: "bl-test", Status: model.OverallPass, ExitCode: model.ExitPass,
		StartedAt: now, FinishedAt: now, Config: model.ConfigIdentity{}, Base: model.RevisionIdentity{}, Candidate: model.RevisionIdentity{},
		Environment:     model.EnvironmentIdentity{SelectedServices: []string{}, Networks: []string{}, SharedServiceImages: map[string]string{}},
		ComponentImages: map[string]string{}, ComponentEndpoints: map[string]string{}, RollbackMode: model.RollbackRaw,
		Verdicts:          map[string]model.Verdict{"mixed_version": {Status: model.VerdictPass, ReasonCodes: []model.ReasonCode{model.ReasonNone}}, "rollback": {Status: model.VerdictPass, ReasonCodes: []model.ReasonCode{model.ReasonNone}}},
		Controls:          map[string]model.StageOutcome{"base_after_transition": model.StagePass, "candidate_before_coexistence": model.StagePass, "base_with_candidate_running": model.StagePass},
		Stages:            []model.StageResult{{Name: "baseline", Outcome: model.StagePass, StartedAt: now, FinishedAt: now}},
		Scenarios:         []model.StepResult{{Stage: "baseline", Scenario: "smoke", Step: "run", Outcome: model.StagePass, ReasonCode: model.ReasonNone, StartedAt: now, FinishedAt: now}},
		OperationalErrors: []model.OperationalError{}, Artifacts: map[string]string{},
		Cleanup: model.CleanupResult{Status: model.StagePass, ReasonCodes: []model.ReasonCode{model.ReasonNone}}, Warnings: []string{},
	}
}
