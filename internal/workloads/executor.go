package workloads

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/components"
	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	"github.com/ChimdumebiNebolisa/Backline/internal/model"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

type LogWriter interface {
	WriteLog(string, []byte) (string, bool, error)
	Emit(string, string, map[string]any) error
}

type Executor struct {
	runner *processrun.Runner
	logs   LogWriter
	mu     sync.Mutex
	next   int
}

func New(runner *processrun.Runner, logs LogWriter) *Executor {
	return &Executor{runner: runner, logs: logs}
}

type ScenarioInput struct {
	RunID             string
	Stage             string
	Scenario          config.Scenario
	Instances         []components.Instance
	BaseWorktree      string
	CandidateWorktree string
	SharedDir         string
	ScenarioRoot      string
	BaseSHA           string
	CandidateSHA      string
	ConfigSHA256      string
	ArtifactDir       string
	Passthrough       []string
	MaxBytes          int64
	ObserveProcess    func(string, processrun.Result)
}

type ScenarioResult struct {
	Name              string
	Outcome           model.StageOutcome
	Steps             []model.StepResult
	OperationalErrors []error
	Began             bool
	Complete          bool
}

func (e *Executor) RunScenario(ctx context.Context, input ScenarioInput) ScenarioResult {
	if ctx.Err() != nil {
		return ScenarioResult{Name: input.Scenario.Name, Outcome: model.StageIncomplete}
	}
	directoryName := sanitize(input.Scenario.Name)
	e.mu.Lock()
	e.next++
	sequence := e.next
	e.mu.Unlock()
	scenarioDirectory := filepath.Join(input.ScenarioRoot, sanitize(input.Stage), fmt.Sprintf("%03d-%s", sequence, directoryName))
	if err := os.MkdirAll(scenarioDirectory, 0o700); err != nil {
		now := time.Now().UTC()
		return ScenarioResult{Name: input.Scenario.Name, Outcome: model.StageError, Steps: []model.StepResult{{Stage: input.Stage, Scenario: input.Scenario.Name, Outcome: model.StageError, ReasonCode: model.ReasonOrchestrationError, StartedAt: now, FinishedAt: now, Message: err.Error()}}}
	}
	result := ScenarioResult{Name: input.Scenario.Name, Outcome: model.StagePass}
	for repeat := 1; repeat <= input.Scenario.Repeat; repeat++ {
		for _, step := range input.Scenario.Steps {
			if ctx.Err() != nil {
				result.Outcome = model.StageIncomplete
				return result
			}
			stepResult, eventErr := e.runStep(ctx, input, step, scenarioDirectory, repeat)
			if eventErr != nil {
				result.OperationalErrors = append(result.OperationalErrors, eventErr)
			}
			if stepResult.ExitCode != nil {
				result.Began = true
			}
			result.Steps = append(result.Steps, stepResult)
			if stepResult.Outcome != model.StagePass {
				if err := e.emitFailure(input.Stage, stepResult); err != nil {
					result.OperationalErrors = append(result.OperationalErrors, err)
				}
				result.Outcome = stepResult.Outcome
				return result
			}
		}
	}
	result.Complete = true
	return result
}

func (e *Executor) runStep(ctx context.Context, input ScenarioInput, step config.Step, scenarioDirectory string, repeat int) (model.StepResult, error) {
	started := time.Now().UTC()
	var eventErr error
	if e.logs != nil {
		eventErr = e.logs.Emit("SCENARIO_STEP_STARTED", input.Stage, map[string]any{"scenario": input.Scenario.Name, "step": step.Name, "target": step.Target, "repeat": repeat})
	}
	reserved := map[string]string{
		"BACKLINE_RUN_ID":        input.RunID,
		"BACKLINE_STAGE":         input.Stage,
		"BACKLINE_BASE_SHA":      input.BaseSHA,
		"BACKLINE_CANDIDATE_SHA": input.CandidateSHA,
		"BACKLINE_CONFIG_SHA256": input.ConfigSHA256,
		"BACKLINE_SHARED_DIR":    input.SharedDir,
		"BACKLINE_ARTIFACT_DIR":  input.ArtifactDir,
		"BACKLINE_SCENARIO":      input.Scenario.Name,
		"BACKLINE_STEP":          step.Name,
		"BACKLINE_REPEAT_INDEX":  strconv.Itoa(repeat),
		"BACKLINE_SCENARIO_DIR":  scenarioDirectory,
	}
	for name, value := range components.EndpointEnvironment(input.Instances, false) {
		reserved[name] = value
	}
	workingDirectory, revisionSHA := stepWorkingDirectory(input, step)
	if step.Target != "" {
		role, component, err := components.ParseTarget(step.Target)
		if err != nil {
			return operationalStep(input, step, started, workingDirectory, revisionSHA, err), eventErr
		}
		instance, exists := components.Find(input.Instances, role, component)
		if !exists || instance.Endpoint == nil {
			return operationalStep(input, step, started, workingDirectory, revisionSHA, fmt.Errorf("target %s is not running and addressable", step.Target)), eventErr
		}
		for name, value := range components.TargetEnvironment(instance, false) {
			reserved[name] = value
		}
	}
	processResult := e.runner.Run(ctx, processrun.Command{
		Name:     step.Command[0],
		Args:     step.Command[1:],
		Dir:      workingDirectory,
		Env:      processrun.MinimalEnvironment(input.Passthrough, step.Environment, reserved),
		Timeout:  time.Duration(step.TimeoutSeconds) * time.Second,
		MaxBytes: input.MaxBytes,
	})
	if input.ObserveProcess != nil {
		input.ObserveProcess(input.Stage+"/"+input.Scenario.Name+"/"+step.Name, processResult)
	}
	combined := append(append([]byte(nil), processResult.Stdout...), processResult.Stderr...)
	logRelative := filepath.ToSlash(filepath.Join("logs", sanitize(input.Stage), directoryName(input.Scenario.Name), fmt.Sprintf("%03d-%s.log", repeat, directoryName(step.Name))))
	logPath := ""
	logTruncated := false
	if e.logs != nil {
		written, truncated, err := e.logs.WriteLog(logRelative, combined)
		if err != nil {
			return operationalStep(input, step, started, workingDirectory, revisionSHA, fmt.Errorf("write step log: %w", err)), eventErr
		}
		logPath = written
		logTruncated = truncated
	}
	finished := time.Now().UTC()
	stepResult := model.StepResult{Stage: input.Stage, Scenario: input.Scenario.Name, Step: step.Name, Target: step.Target, StartedAt: started, FinishedAt: finished, DurationMS: finished.Sub(started).Milliseconds(), LogPath: logPath, TimedOut: processResult.TimedOut, Signal: processResult.Signal, TerminationAttempted: processResult.TerminationAttempted, TerminationSucceeded: processResult.TerminationSucceeded, Truncated: processResult.Truncated || logTruncated, Command: append([]string(nil), step.Command...), CWDRevision: step.CWDRevision, RevisionSHA: revisionSHA, WorkingDirectory: workingDirectory}
	if processResult.Canceled {
		stepResult.Outcome = model.StageIncomplete
		stepResult.ReasonCode = model.ReasonInterrupted
		stepResult.Message = "command canceled"
		if processResult.Launched {
			stepResult.ExitCode = &processResult.ExitCode
		}
		return stepResult, eventErr
	}
	if !processResult.Launched || processResult.Err != nil {
		stepResult.Outcome = model.StageError
		stepResult.ReasonCode = model.ReasonCommandLaunchFailed
		if processResult.Err != nil {
			stepResult.Message = processResult.Err.Error()
		}
		return stepResult, eventErr
	}
	stepResult.ExitCode = &processResult.ExitCode
	if processResult.ExitCode != 0 || processResult.TimedOut {
		stepResult.Outcome = model.StageFail
		stepResult.ReasonCode = reasonForStage(input.Stage)
		stepResult.Message = fmt.Sprintf("command exited %d", processResult.ExitCode)
		if processResult.TimedOut {
			stepResult.Message = "command timed out"
		}
		return stepResult, eventErr
	}
	stepResult.Outcome = model.StagePass
	stepResult.ReasonCode = model.ReasonNone
	return stepResult, eventErr
}

func (e *Executor) emitFailure(stage string, result model.StepResult) error {
	if e.logs != nil {
		return e.logs.Emit("SCENARIO_STEP_FAILED", stage, map[string]any{"scenario": result.Scenario, "step": result.Step, "target": result.Target, "reason_code": result.ReasonCode, "log_path": result.LogPath})
	}
	return nil
}

func RemapBaseline(scenarios []config.Scenario, role string) []config.Scenario {
	result := make([]config.Scenario, len(scenarios))
	for scenarioIndex, scenario := range scenarios {
		result[scenarioIndex] = scenario
		result[scenarioIndex].Steps = make([]config.Step, len(scenario.Steps))
		for stepIndex, step := range scenario.Steps {
			result[scenarioIndex].Steps[stepIndex] = step
			if step.Target != "" {
				_, component, err := components.ParseTarget(step.Target)
				if err == nil {
					result[scenarioIndex].Steps[stepIndex].Target = role + "." + component
				}
			}
		}
	}
	return result
}

func GroupScenarios(group config.ScenarioGroup, baseline []config.Scenario, role string) []config.Scenario {
	if group.ReuseBaseline {
		return RemapBaseline(baseline, role)
	}
	return group.Scenarios
}

func reasonForStage(stage string) model.ReasonCode {
	switch stage {
	case "baseline":
		return model.ReasonBaselineFailed
	case "base_after_transition_control":
		return model.ReasonBaseAfterTransitionControlFailed
	case "candidate_control":
		return model.ReasonCandidateControlFailed
	case "base_with_candidate_control":
		return model.ReasonBaseWithCandidateControlFailed
	case "coexistence":
		return model.ReasonCoexistenceScenarioFailed
	case "candidate_traffic":
		return model.ReasonCandidateTrafficFailed
	case "rollback":
		return model.ReasonRollbackScenarioFailed
	default:
		return model.ReasonOrchestrationError
	}
}

func operationalStep(input ScenarioInput, step config.Step, started time.Time, workingDirectory, revisionSHA string, err error) model.StepResult {
	finished := time.Now().UTC()
	return model.StepResult{Stage: input.Stage, Scenario: input.Scenario.Name, Step: step.Name, Target: step.Target, Outcome: model.StageError, ReasonCode: model.ReasonCommandLaunchFailed, StartedAt: started, FinishedAt: finished, DurationMS: finished.Sub(started).Milliseconds(), Message: err.Error(), Command: append([]string(nil), step.Command...), CWDRevision: step.CWDRevision, RevisionSHA: revisionSHA, WorkingDirectory: workingDirectory}
}

func stepWorkingDirectory(input ScenarioInput, step config.Step) (string, string) {
	worktree, sha := input.CandidateWorktree, input.CandidateSHA
	if step.CWDRevision == "base" {
		worktree, sha = input.BaseWorktree, input.BaseSHA
	}
	if step.WorkingDirectory != "" {
		worktree = filepath.Join(worktree, filepath.FromSlash(step.WorkingDirectory))
	}
	return worktree, sha
}

func sanitize(value string) string {
	value = strings.ToLower(value)
	value = strings.Map(func(character rune) rune {
		if character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '-' {
			return character
		}
		return '-'
	}, value)
	value = strings.Trim(value, "-")
	if value == "" {
		return "scenario"
	}
	return value
}

func directoryName(value string) string { return sanitize(value) }
