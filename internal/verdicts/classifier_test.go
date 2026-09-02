package verdicts

import (
	"testing"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
)

func completeEvidence() Evidence {
	return Evidence{
		BaselinePassed: true, TransitionPassed: true, BaseAfterTransitionPassed: true,
		CandidateStarted: true, CandidateControlPassed: true, BaseWithCandidatePassed: true,
		CoexistenceCompleted: true, CandidateOnlyCompleted: true, CandidateTrafficCompleted: true,
		CandidateMutationRan: true, RollbackHooksCompleted: true, FreshBaseStarted: true,
		RollbackChecksCompleted: true, RollbackMode: model.RollbackRaw,
	}
}

func TestCompleteEvidencePasses(t *testing.T) {
	result := Classify(completeEvidence())
	if result.Mixed.Status != model.VerdictPass || result.Rollback.Status != model.VerdictPass || result.ExitCode != model.ExitPass {
		t.Fatalf("result=%#v", result)
	}
}

func TestDirectFailurePrecedesOperationalAndIncompleteEvidence(t *testing.T) {
	evidence := Evidence{
		MixedFailureReasons: []model.ReasonCode{model.ReasonCoexistenceScenarioFailed},
		OperationalErrors:   []model.OperationalError{{ReasonCode: model.ReasonOrchestrationError, Message: "daemon lost"}},
		RollbackMode:        model.RollbackRaw,
	}
	result := Classify(evidence)
	if result.Mixed.Status != model.VerdictFail || result.ExitCode != model.ExitCompatibilityFail {
		t.Fatalf("result=%#v", result)
	}
}

func TestCandidateControlFailureIsInconclusive(t *testing.T) {
	evidence := completeEvidence()
	evidence.CandidateControlPassed = false
	evidence.CoexistenceCompleted = false
	evidence.CandidateTrafficCompleted = false
	evidence.RollbackChecksCompleted = false
	evidence.MixedUncertaintyReasons = []model.ReasonCode{model.ReasonCandidateControlFailed}
	evidence.RollbackUncertaintyReasons = []model.ReasonCode{model.ReasonCandidateControlFailed}
	result := Classify(evidence)
	if result.Mixed.Status != model.VerdictInconclusive || result.Rollback.Status != model.VerdictInconclusive || result.ExitCode != model.ExitInconclusive {
		t.Fatalf("result=%#v", result)
	}
}

func TestPreparedRollbackWording(t *testing.T) {
	evidence := completeEvidence()
	evidence.RollbackMode = model.RollbackPrepared
	result := Classify(evidence)
	if result.Rollback.Status != model.VerdictPass || result.Rollback.Message == "" || result.Rollback.Message == "The base revision passed the configured rollback checks directly against state left by candidate cutover and traffic." {
		t.Fatalf("rollback=%#v", result.Rollback)
	}
}

func TestExitCodePrecedence(t *testing.T) {
	for _, test := range []struct {
		name     string
		mutate   func(*Evidence)
		expected model.ExitCode
	}{
		{name: "inconclusive", mutate: func(e *Evidence) {
			e.CandidateTrafficCompleted = false
			e.RollbackUncertaintyReasons = []model.ReasonCode{model.ReasonCandidateTrafficIncomplete}
		}, expected: model.ExitInconclusive},
		{name: "cleanup", mutate: func(e *Evidence) { e.CleanupFailed = true }, expected: model.ExitCleanupFailed},
		{name: "operational over cleanup", mutate: func(e *Evidence) {
			e.CleanupFailed = true
			e.OperationalErrors = []model.OperationalError{{ReasonCode: model.ReasonOrchestrationError, Message: "daemon"}}
		}, expected: model.ExitOrchestrationError},
		{name: "internal over operational", mutate: func(e *Evidence) {
			e.InternalError = true
			e.OperationalErrors = []model.OperationalError{{ReasonCode: model.ReasonOrchestrationError, Message: "daemon"}}
		}, expected: model.ExitInternalError},
		{name: "compatibility over internal", mutate: func(e *Evidence) {
			e.InternalError = true
			e.RollbackFailureReasons = []model.ReasonCode{model.ReasonRollbackScenarioFailed}
		}, expected: model.ExitCompatibilityFail},
	} {
		t.Run(test.name, func(t *testing.T) {
			evidence := completeEvidence()
			test.mutate(&evidence)
			if result := Classify(evidence); result.ExitCode != test.expected {
				t.Fatalf("result=%#v, want exit %d", result, test.expected)
			}
		})
	}
}

func TestDirectRollbackFailureSurvivesIncompleteTraffic(t *testing.T) {
	evidence := completeEvidence()
	evidence.CandidateTrafficCompleted = false
	evidence.RollbackUncertaintyReasons = []model.ReasonCode{model.ReasonCandidateTrafficIncomplete}
	evidence.RollbackFailureReasons = []model.ReasonCode{model.ReasonRollbackScenarioFailed}
	result := Classify(evidence)
	if result.Rollback.Status != model.VerdictFail || result.ExitCode != model.ExitCompatibilityFail {
		t.Fatalf("result=%#v", result)
	}
}

func TestDirectRollbackFailureBeforeCandidateMutationRemainsFailure(t *testing.T) {
	evidence := completeEvidence()
	evidence.CandidateOnlyCompleted = false
	evidence.CandidateTrafficCompleted = false
	evidence.CandidateMutationRan = false
	evidence.RollbackUncertaintyReasons = []model.ReasonCode{model.ReasonCandidateOnlyHookFailed}
	evidence.RollbackFailureReasons = []model.ReasonCode{model.ReasonRollbackScenarioFailed}
	result := Classify(evidence)
	if result.Rollback.Status != model.VerdictFail || result.ExitCode != model.ExitCompatibilityFail {
		t.Fatalf("result=%#v", result)
	}
}

func TestMissingCandidateMutationIsInconclusive(t *testing.T) {
	evidence := completeEvidence()
	evidence.CandidateMutationRan = false
	evidence.RollbackUncertaintyReasons = []model.ReasonCode{model.ReasonNoCandidateMutationScenarioRan}
	result := Classify(evidence)
	if result.Rollback.Status != model.VerdictInconclusive || result.Rollback.ReasonCodes[0] != model.ReasonNoCandidateMutationScenarioRan {
		t.Fatalf("result=%#v", result)
	}
}

func TestInterruptionKeepsUnfinishedVerdictsInconclusive(t *testing.T) {
	evidence := Evidence{
		MixedUncertaintyReasons:    []model.ReasonCode{model.ReasonInterrupted},
		RollbackUncertaintyReasons: []model.ReasonCode{model.ReasonInterrupted},
		OperationalErrors:          []model.OperationalError{{Stage: "interrupted", ReasonCode: model.ReasonOrchestrationError, Message: "context canceled"}},
		RollbackMode:               model.RollbackRaw,
	}
	result := Classify(evidence)
	if result.Mixed.Status != model.VerdictInconclusive || result.Rollback.Status != model.VerdictInconclusive || result.ExitCode != model.ExitOrchestrationError {
		t.Fatalf("result=%#v", result)
	}
	if result.Mixed.ReasonCodes[0] != model.ReasonInterrupted || result.Rollback.ReasonCodes[0] != model.ReasonInterrupted {
		t.Fatalf("interruption reasons lost: %#v", result)
	}
}
