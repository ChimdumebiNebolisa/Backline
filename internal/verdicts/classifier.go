package verdicts

import (
	"github.com/ChimdumebiNebolisa/Backline/internal/model"
)

type Evidence struct {
	BaselinePassed             bool
	TransitionPassed           bool
	BaseAfterTransitionPassed  bool
	CandidateStarted           bool
	CandidateControlPassed     bool
	BaseWithCandidatePassed    bool
	CoexistenceCompleted       bool
	CandidateOnlyCompleted     bool
	CandidateTrafficCompleted  bool
	CandidateMutationRan       bool
	RollbackHooksCompleted     bool
	FreshBaseStarted           bool
	RollbackChecksCompleted    bool
	MixedFailureReasons        []model.ReasonCode
	RollbackFailureReasons     []model.ReasonCode
	MixedUncertaintyReasons    []model.ReasonCode
	RollbackUncertaintyReasons []model.ReasonCode
	OperationalErrors          []model.OperationalError
	InternalError              bool
	CleanupFailed              bool
	RollbackMode               model.RollbackMode
}

type Result struct {
	Mixed    model.Verdict
	Rollback model.Verdict
	Overall  model.OverallStatus
	ExitCode model.ExitCode
}

func Classify(evidence Evidence) Result {
	mixed := classifyMixed(evidence)
	rollback := classifyRollback(evidence)
	overall, exitCode := overall(mixed, rollback, evidence)
	return Result{Mixed: mixed, Rollback: rollback, Overall: overall, ExitCode: exitCode}
}

func classifyMixed(evidence Evidence) model.Verdict {
	if reasons := uniqueReasons(evidence.MixedFailureReasons); len(reasons) > 0 {
		return model.Verdict{Status: model.VerdictFail, ReasonCodes: reasons, Message: "A configured mixed-version control or scenario detected incompatible behavior."}
	}
	if evidence.BaselinePassed && evidence.TransitionPassed && evidence.BaseAfterTransitionPassed && evidence.CandidateStarted && evidence.CandidateControlPassed && evidence.BaseWithCandidatePassed && evidence.CoexistenceCompleted {
		return model.Verdict{Status: model.VerdictPass, ReasonCodes: []model.ReasonCode{model.ReasonNone}, Message: "No incompatibility detected in the configured mixed-version controls and scenarios."}
	}
	reasons := uniqueReasons(evidence.MixedUncertaintyReasons)
	if len(reasons) == 0 {
		reasons = []model.ReasonCode{model.ReasonOrchestrationError}
	}
	return model.Verdict{Status: model.VerdictInconclusive, ReasonCodes: reasons, Message: "Required mixed-version evidence was not established."}
}

func classifyRollback(evidence Evidence) model.Verdict {
	if reasons := uniqueReasons(evidence.RollbackFailureReasons); len(reasons) > 0 {
		message := "The base revision failed against state left by candidate cutover and traffic."
		if evidence.RollbackMode == model.RollbackPrepared {
			message = "The configured rollback procedure failed after candidate cutover and traffic."
		}
		return model.Verdict{Status: model.VerdictFail, ReasonCodes: reasons, Message: message}
	}
	if evidence.CandidateStarted && evidence.CandidateControlPassed && evidence.CandidateOnlyCompleted && evidence.CandidateTrafficCompleted && evidence.CandidateMutationRan && evidence.RollbackHooksCompleted && evidence.FreshBaseStarted && evidence.RollbackChecksCompleted {
		message := "The base revision passed the configured rollback checks directly against state left by candidate cutover and traffic."
		if evidence.RollbackMode == model.RollbackPrepared {
			message = "The base revision passed the configured rollback checks after candidate cutover, traffic, and the configured rollback preparation."
		}
		return model.Verdict{Status: model.VerdictPass, ReasonCodes: []model.ReasonCode{model.ReasonNone}, Message: message}
	}
	reasons := uniqueReasons(evidence.RollbackUncertaintyReasons)
	if len(reasons) == 0 {
		reasons = []model.ReasonCode{model.ReasonOrchestrationError}
	}
	return model.Verdict{Status: model.VerdictInconclusive, ReasonCodes: reasons, Message: "Required candidate and rollback evidence was not established."}
}

func overall(mixed, rollback model.Verdict, evidence Evidence) (model.OverallStatus, model.ExitCode) {
	if mixed.Status == model.VerdictFail || rollback.Status == model.VerdictFail {
		return model.OverallFail, model.ExitCompatibilityFail
	}
	if evidence.InternalError {
		return model.OverallError, model.ExitInternalError
	}
	if len(evidence.OperationalErrors) > 0 {
		return model.OverallError, model.ExitOrchestrationError
	}
	if evidence.CleanupFailed {
		return model.OverallError, model.ExitCleanupFailed
	}
	if mixed.Status == model.VerdictInconclusive || rollback.Status == model.VerdictInconclusive {
		return model.OverallInconclusive, model.ExitInconclusive
	}
	return model.OverallPass, model.ExitPass
}

func uniqueReasons(reasons []model.ReasonCode) []model.ReasonCode {
	seen := make(map[model.ReasonCode]struct{}, len(reasons))
	result := make([]model.ReasonCode, 0, len(reasons))
	for _, reason := range reasons {
		if reason == "" || reason == model.ReasonNone {
			continue
		}
		if _, exists := seen[reason]; exists {
			continue
		}
		seen[reason] = struct{}{}
		result = append(result, reason)
	}
	return result
}
