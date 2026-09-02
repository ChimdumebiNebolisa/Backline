package workloads

import (
	"context"
	"errors"
	"path/filepath"
	"testing"

	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

func TestRemapBaselineChangesTargetsWithoutMutatingSource(t *testing.T) {
	baseline := []config.Scenario{{Name: "control", Steps: []config.Step{{Name: "targeted", Target: "base.api", Command: []string{"check"}}, {Name: "untargeted", Command: []string{"check"}}}}}
	remapped := RemapBaseline(baseline, "candidate")
	if remapped[0].Steps[0].Target != "candidate.api" || remapped[0].Steps[1].Target != "" {
		t.Fatalf("remapped = %#v", remapped)
	}
	if baseline[0].Steps[0].Target != "base.api" {
		t.Fatalf("source mutated = %#v", baseline)
	}
}

func TestScenarioBeganRequiresLaunchedCommand(t *testing.T) {
	root := t.TempDir()
	executor := New(processrun.NewRunner(), nil)
	result := executor.RunScenario(context.Background(), ScenarioInput{
		Stage: "candidate_traffic", ScenarioRoot: filepath.Join(root, "scenarios"),
		BaseWorktree: root, CandidateWorktree: root,
		Scenario: config.Scenario{Name: "mutation", Repeat: 1, MutatesState: true, Steps: []config.Step{{Name: "missing", Command: []string{"backline-command-that-does-not-exist"}, CWDRevision: "candidate", TimeoutSeconds: 5}}},
	})
	if result.Began {
		t.Fatal("a command that could not be launched must not count as a mutation scenario that ran")
	}
}

func TestCanceledScenarioDoesNotLaunchWork(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	result := New(processrun.NewRunner(), nil).RunScenario(ctx, ScenarioInput{
		Stage: "coexistence", ScenarioRoot: t.TempDir(),
		Scenario: config.Scenario{Name: "canceled", Repeat: 1, Steps: []config.Step{{Name: "must-not-run", Command: []string{"go", "version"}}}},
	})
	if result.Outcome != "INCOMPLETE" || result.Began || len(result.Steps) != 0 {
		t.Fatalf("result=%+v", result)
	}
}

func TestScenarioRecordsEventWriteFailureWithoutDiscardingEvidence(t *testing.T) {
	root := t.TempDir()
	executor := New(processrun.NewRunner(), failingEventLog{})
	result := executor.RunScenario(context.Background(), ScenarioInput{
		Stage: "baseline", ScenarioRoot: filepath.Join(root, "scenarios"),
		BaseWorktree: root, CandidateWorktree: root, CandidateSHA: "candidate-sha", MaxBytes: 4096,
		Scenario: config.Scenario{Name: "smoke", Repeat: 1, Steps: []config.Step{{Name: "go", Command: []string{"go", "version"}, CWDRevision: "candidate", TimeoutSeconds: 30}}},
	})
	if result.Outcome != "PASS" || !result.Began || len(result.OperationalErrors) != 1 {
		t.Fatalf("result=%+v", result)
	}
	step := result.Steps[0]
	if step.StartedAt.IsZero() || step.FinishedAt.Before(step.StartedAt) || step.RevisionSHA != "candidate-sha" || step.WorkingDirectory != root {
		t.Fatalf("step diagnostics=%+v", step)
	}
}

type failingEventLog struct{}

func (failingEventLog) WriteLog(string, []byte) (string, bool, error) {
	return "logs/test.log", false, nil
}

func (failingEventLog) Emit(string, string, map[string]any) error {
	return errors.New("event artifact unavailable")
}
