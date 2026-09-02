package orchestrator

import (
	"bytes"
	"strings"
	"testing"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
	"github.com/ChimdumebiNebolisa/Backline/internal/redact"
)

func TestLifecycleStateMachineAcceptsCompletePath(t *testing.T) {
	machine := NewStateMachine()
	for _, next := range []LifecycleState{
		StateBuild, StateShared, StateBootstrap, StateBaseStartup, StateBaseline,
		StateTransition, StateBaseControl, StateCandidateStartup, StateCandidateControl,
		StateMixedControl, StateCoexistence, StateCandidateOnly, StateCandidateTraffic,
		StateRollback, StateReporting, StateCleanup, StateComplete,
	} {
		if err := machine.Advance(next); err != nil {
			t.Fatalf("advance to %s: %v", next, err)
		}
	}
	if machine.Current() != StateComplete {
		t.Fatalf("current = %s", machine.Current())
	}
}

func TestLifecycleStateMachineAllowsFailureToReporting(t *testing.T) {
	machine := NewStateMachine()
	if err := machine.Advance(StateBuild); err != nil {
		t.Fatal(err)
	}
	if err := machine.Advance(StateReporting); err != nil {
		t.Fatal(err)
	}
	if err := machine.Advance(StateComplete); err == nil {
		t.Fatal("expected cleanup bypass rejection")
	}
}

func TestLifecycleStateMachineRejectsDependencySkip(t *testing.T) {
	machine := NewStateMachine()
	if err := machine.Advance(StateCandidateStartup); err == nil {
		t.Fatal("expected invalid dependency transition")
	}
}

func TestCoexistenceRequiresEveryControl(t *testing.T) {
	if !coexistenceEligible(true, true, true, true) {
		t.Fatal("all controls should permit coexistence")
	}
	for _, inputs := range [][4]bool{{false, true, true, true}, {true, false, true, true}, {true, true, false, true}, {true, true, true, false}} {
		if coexistenceEligible(inputs[0], inputs[1], inputs[2], inputs[3]) {
			t.Fatalf("coexistence incorrectly permitted for %v", inputs)
		}
	}
}

func TestControlOutcomeKeepsOperationalErrorsSeparate(t *testing.T) {
	if got := executionOutcome(stageExecution{OperationalFailure: true}); got != model.StageError {
		t.Fatalf("operational outcome=%s", got)
	}
	if got := executionOutcome(stageExecution{ProjectFailure: true, OperationalFailure: true}); got != model.StageFail {
		t.Fatalf("direct project outcome=%s", got)
	}
	if got := executionOutcome(stageExecution{Incomplete: true}); got != model.StageIncomplete {
		t.Fatalf("interrupted outcome=%s", got)
	}
}

func TestVerboseProcessOutputIsRedacted(t *testing.T) {
	var output bytes.Buffer
	execution := execution{options: Options{Verbose: true, Out: &output}, redactor: redact.New("super-secret")}
	execution.verboseProcess("candidate_traffic/test", processrun.Result{Launched: true, ExitCode: 1, Stdout: []byte("token=super-secret"), Stderr: []byte("bearer another-secret")})
	actual := output.String()
	if strings.Contains(actual, "super-secret") || strings.Contains(actual, "another-secret") || strings.Count(actual, redact.Marker) < 2 {
		t.Fatalf("verbose output was not redacted: %q", actual)
	}
}

func TestRunLabelCleanupCommandIsResourceSpecific(t *testing.T) {
	for _, kind := range []string{"container", "image"} {
		command := manualLabeledCleanup(kind, "dev.backline.run_id=bl-test")
		if !strings.Contains(command, "label=dev.backline.run_id=bl-test") || !strings.Contains(command, "docker "+kind+" ls") {
			t.Fatalf("%s cleanup command=%q", kind, command)
		}
	}
}
