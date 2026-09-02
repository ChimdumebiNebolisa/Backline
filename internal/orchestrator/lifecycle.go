package orchestrator

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/cleanup"
	"github.com/ChimdumebiNebolisa/Backline/internal/components"
	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	"github.com/ChimdumebiNebolisa/Backline/internal/dockerops"
	"github.com/ChimdumebiNebolisa/Backline/internal/hooks"
	"github.com/ChimdumebiNebolisa/Backline/internal/model"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
	"github.com/ChimdumebiNebolisa/Backline/internal/reporting"
	"github.com/ChimdumebiNebolisa/Backline/internal/verdicts"
	"github.com/ChimdumebiNebolisa/Backline/internal/workloads"
)

func (e *execution) startRole(ctx context.Context, role string) ([]components.Instance, error) {
	started := time.Now()
	sha := e.preparation.BaseSHA
	if role == "candidate" {
		sha = e.preparation.CandidateSHA
	}
	instances, err := e.owner.components.StartAll(ctx, components.StartInput{
		RunID: e.session.RunID, Role: role, SHA: sha,
		Components: e.preparation.Config.Release.Components,
		Images:     e.imageRefs[role], Networks: e.shared.Networks,
		MaxBytes: e.preparation.Config.Artifacts.MaxLogBytesPerCommand,
	})
	e.verbosef("component lifecycle: %s role returned %d container(s)", role, len(instances))
	for _, instance := range instances {
		e.registerContainer(instance.Container)
		if instance.Endpoint != nil {
			e.endpoints[instance.Role+"."+instance.Component.Name] = instance.Endpoint.URL()
		}
	}
	reason := model.ReasonBaseStartupFailed
	stage := "base_startup"
	if role == "candidate" {
		reason = model.ReasonCandidateStartupFailed
		stage = "candidate_startup"
	} else if e.machine.Current() == StateRollback {
		reason = model.ReasonBaseRestartFailed
		stage = "base_restart"
	}
	if err != nil {
		if ctx.Err() != nil {
			e.addUncertainty(model.ReasonInterrupted, true, true)
			e.addStage(stage, model.StageIncomplete, model.ReasonInterrupted, "component startup canceled", started)
			return instances, err
		}
		stageOutcome := model.StageFail
		stageReason := reason
		if components.IsOperational(err) {
			stageOutcome = model.StageError
			stageReason = model.ReasonOrchestrationError
			e.addOperational(stage, err)
		}
		firstLog := ""
		firstTruncated := false
		for _, instance := range instances {
			logs := e.owner.docker.Logs(ctx, instance.Container, e.preparation.Config.Artifacts.MaxLogBytesPerCommand)
			logPath, logTruncated := e.storeLog(filepath.ToSlash(filepath.Join("logs", stage, instance.Component.Name+".log")), append(logs.Stdout, logs.Stderr...))
			logTruncated = logTruncated || logs.Truncated
			if firstLog == "" {
				firstLog, firstTruncated = logPath, logTruncated
			}
			e.addStageWithLog(stage+"/"+instance.Component.Name, stageOutcome, stageReason, err.Error(), logPath, logTruncated, started)
		}
		e.addStageWithLog(stage, stageOutcome, stageReason, err.Error(), firstLog, firstTruncated, started)
		e.captureInspect(ctx, instances, stage)
		return instances, err
	}
	e.addStage(stage, model.StagePass, model.ReasonNone, "", started)
	eventType := "BASE_READY"
	if role == "candidate" {
		eventType = "CANDIDATE_READY"
	}
	e.emit(eventType, stage, map[string]any{"containers": containerIDs(instances)})
	return instances, nil
}

func (e *execution) runHooks(ctx context.Context, stage string, definitions []config.Hook, failureReason model.ReasonCode, directRollback bool) bool {
	started := time.Now()
	if len(definitions) == 0 {
		e.addStage(stage, model.StagePass, model.ReasonNone, "", started)
		return true
	}
	for _, definition := range definitions {
		hookStarted := time.Now()
		worktree := e.preparation.CandidateWorktree
		if definition.Revision == "base" {
			worktree = e.preparation.BaseWorktree
		}
		environment := e.runEnvironment(stage)
		for name, value := range components.EndpointEnvironment(e.allInstances(), definition.Runner.Type == "component") {
			environment[name] = value
		}
		var component *config.Component
		image := ""
		if definition.Runner.Type == "component" {
			configured, exists := e.preparation.Config.Component(definition.Runner.Component)
			if !exists {
				e.addOperational(stage, fmt.Errorf("hook %s references missing component %s", definition.Name, definition.Runner.Component))
				return false
			}
			component = &configured
			image = e.imageRefs[definition.Revision][configured.Name]
			for name, value := range components.RoleEnvironment(configured, definition.Revision) {
				environment[name] = value
			}
		}
		environment["BACKLINE_HOOK"] = definition.Name
		environment["BACKLINE_HOOK_REVISION"] = definition.Revision
		result, err := e.hooks.Run(ctx, hooks.Input{
			RunID: e.session.RunID, Stage: stage, Hook: definition, Worktree: worktree,
			Image: image, Component: component, Networks: e.shared.Networks,
			Environment: environment, Passthrough: e.preparation.Config.Security.PassthroughEnvironment,
			SharedDir: e.sharedDir, ArtifactDir: e.session.Root,
			MaxBytes: e.preparation.Config.Artifacts.MaxLogBytesPerCommand,
		})
		e.verboseProcess(stage+"/"+definition.Name, result.Process)
		if result.Container != "" {
			e.registerContainer(result.Container)
		}
		logPath, logTruncated := e.storeLog(filepath.ToSlash(filepath.Join("logs", stage, sanitize(definition.Name)+".log")), append(result.Process.Stdout, result.Process.Stderr...))
		if result.Process.Canceled {
			e.addHookStage(stage, definition, result.Process, model.StageIncomplete, model.ReasonInterrupted, "hook canceled", logPath, logTruncated, hookStarted)
			e.addStage(stage, model.StageIncomplete, model.ReasonInterrupted, "hook canceled", started)
			return false
		}
		if err != nil || !result.Process.Launched || result.Process.Err != nil {
			message := errorMessage(err, result.Process.Err)
			e.addOperational(stage, errors.New(message))
			e.addHookStage(stage, definition, result.Process, model.StageError, model.ReasonCommandLaunchFailed, message, logPath, logTruncated, hookStarted)
			e.addStage(stage, model.StageError, model.ReasonCommandLaunchFailed, message, started)
			return false
		}
		if result.Process.ExitCode != 0 || result.Process.TimedOut {
			message := fmt.Sprintf("hook %s exited %d", definition.Name, result.Process.ExitCode)
			e.addHookStage(stage, definition, result.Process, model.StageFail, failureReason, message, logPath, logTruncated, hookStarted)
			e.addStage(stage, model.StageFail, failureReason, message, started)
			if directRollback {
				e.evidence.RollbackFailureReasons = append(e.evidence.RollbackFailureReasons, failureReason)
			}
			return false
		}
		e.addHookStage(stage, definition, result.Process, model.StagePass, model.ReasonNone, "", logPath, logTruncated, hookStarted)
	}
	e.addStage(stage, model.StagePass, model.ReasonNone, "", started)
	return true
}

func (e *execution) addHookStage(stage string, definition config.Hook, result processrun.Result, outcome model.StageOutcome, reason model.ReasonCode, message, logPath string, logTruncated bool, started time.Time) {
	finished := time.Now().UTC()
	reasons := []model.ReasonCode{reason}
	if reason == "" {
		reasons = nil
	}
	var exitCode *int
	if result.Launched {
		value := result.ExitCode
		exitCode = &value
	}
	e.stages = append(e.stages, model.StageResult{
		Name: stage + "/" + definition.Name, Outcome: outcome, ReasonCodes: reasons,
		StartedAt: started.UTC(), FinishedAt: finished, DurationMS: finished.Sub(started).Milliseconds(), Message: message,
		Revision: definition.Revision, Runner: definition.Runner.Type, Command: append([]string(nil), definition.Command...), ExitCode: exitCode, LogPath: logPath, Signal: result.Signal, TerminationAttempted: result.TerminationAttempted, TerminationSucceeded: result.TerminationSucceeded, Truncated: result.Truncated || logTruncated,
	})
}

type stageExecution struct {
	Passed             bool
	ProjectFailure     bool
	OperationalFailure bool
	Incomplete         bool
}

func (e *execution) runScenarioStage(ctx context.Context, stage string, scenarios []config.Scenario, classification string) stageExecution {
	started := time.Now()
	execution := stageExecution{Passed: true}
	for _, scenario := range scenarios {
		if ctx.Err() != nil {
			execution.Passed = false
			execution.Incomplete = true
			break
		}
		if err := e.sharedHealth(ctx); err != nil {
			e.addOperational(stage, err)
			execution.Passed = false
			execution.OperationalFailure = true
			break
		}
		result := e.workloads.RunScenario(ctx, workloads.ScenarioInput{
			RunID: e.session.RunID, Stage: stage, Scenario: scenario,
			Instances: e.allInstances(), BaseWorktree: e.preparation.BaseWorktree,
			CandidateWorktree: e.preparation.CandidateWorktree, SharedDir: e.sharedDir,
			ScenarioRoot: e.scenarioRoot, BaseSHA: e.preparation.BaseSHA,
			CandidateSHA: e.preparation.CandidateSHA, ConfigSHA256: e.preparation.ConfigSHA256,
			ArtifactDir: e.session.Root, Passthrough: e.preparation.Config.Security.PassthroughEnvironment,
			MaxBytes:       e.preparation.Config.Artifacts.MaxLogBytesPerCommand,
			ObserveProcess: e.verboseProcess,
		})
		for _, err := range result.OperationalErrors {
			e.addOperational(stage, err)
			execution.OperationalFailure = true
		}
		e.steps = append(e.steps, result.Steps...)
		if stage == "candidate_traffic" && scenario.MutatesState && result.Began {
			e.evidence.CandidateMutationRan = true
		}
		if result.Outcome != model.StagePass {
			execution.Passed = false
			if result.Outcome == model.StageFail {
				execution.ProjectFailure = true
				if classification == "mixed" {
					e.evidence.MixedFailureReasons = append(e.evidence.MixedFailureReasons, stageReason(stage))
				} else if classification == "rollback" {
					e.evidence.RollbackFailureReasons = append(e.evidence.RollbackFailureReasons, stageReason(stage))
				}
			}
			for _, step := range result.Steps {
				if step.Outcome == model.StageError {
					e.addOperational(stage, errors.New(step.Message))
					execution.OperationalFailure = true
				}
			}
			if result.Outcome == model.StageIncomplete {
				execution.Incomplete = true
				break
			}
		}
		if err := e.owner.components.Check(ctx, e.allInstances()); err != nil {
			execution.Passed = false
			e.captureInspect(ctx, e.allInstances(), stage)
			if components.IsOperational(err) {
				e.addOperational(stage, err)
				execution.OperationalFailure = true
			} else {
				execution.ProjectFailure = true
				if classification == "mixed" {
					e.evidence.MixedFailureReasons = append(e.evidence.MixedFailureReasons, model.ReasonRequiredComponentExited)
				} else if classification == "rollback" {
					e.evidence.RollbackFailureReasons = append(e.evidence.RollbackFailureReasons, model.ReasonRequiredComponentExited)
				} else if failure, ok := components.AsComponentFailure(err); ok && stage == "candidate_control" && failure.Role == "base" {
					e.evidence.MixedFailureReasons = append(e.evidence.MixedFailureReasons, model.ReasonBaseLostReadiness)
				}
			}
			break
		}
	}
	reason := model.ReasonNone
	outcome := model.StagePass
	if execution.ProjectFailure {
		outcome = model.StageFail
		reason = stageReason(stage)
	} else if execution.OperationalFailure {
		outcome = model.StageError
		reason = model.ReasonOrchestrationError
	} else if execution.Incomplete {
		outcome = model.StageIncomplete
		reason = model.ReasonInterrupted
	}
	e.addStage(stage, outcome, reason, "", started)
	return execution
}

func (e *execution) checkBase(ctx context.Context, stage string) (bool, bool) {
	started := time.Now()
	if err := e.sharedHealth(ctx); err != nil {
		e.addOperational(stage, err)
		e.addStage(stage, model.StageError, model.ReasonSharedEnvironmentFailed, "", started)
		return false, true
	}
	if err := e.owner.components.Check(ctx, e.base); err != nil {
		e.captureInspect(ctx, e.base, stage)
		if components.IsOperational(err) {
			e.addOperational(stage, err)
			e.addStage(stage, model.StageError, model.ReasonOrchestrationError, err.Error(), started)
			return false, true
		}
		e.addStage(stage, model.StageFail, model.ReasonBaseLostReadiness, err.Error(), started)
		return false, false
	}
	e.addStage(stage, model.StagePass, model.ReasonNone, "", started)
	return true, false
}

func (e *execution) runRollback(ctx context.Context) {
	e.captureComponentLogs(ctx, e.candidate, "candidate-before-rollback")
	stopStarted := time.Now()
	if err := e.owner.components.StopAll(ctx, e.candidate, e.preparation.Config.SharedEnvironment.ShutdownTimeoutSeconds); err != nil {
		e.addOperational("candidate_stop", err)
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonOrchestrationError)
		e.addStage("candidate_stop", model.StageError, model.ReasonOrchestrationError, err.Error(), stopStarted)
		return
	}
	e.candidate = nil
	e.addStage("candidate_stop", model.StagePass, model.ReasonNone, "", stopStarted)
	e.emit("CANDIDATE_STOPPED", "rollback", map[string]any{})
	e.emit("ROLLBACK_MODE_SELECTED", "rollback", map[string]any{"mode": e.evidence.RollbackMode})
	rollbackHooksPassed := e.runHooks(ctx, "rollback_hooks", e.preparation.Config.Lifecycle.Rollback, model.ReasonRollbackHookFailed, true)
	if rollbackHooksPassed {
		e.evidence.RollbackHooksCompleted = true
	} else if ctx.Err() != nil {
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonInterrupted)
		return
	} else if len(e.evidence.RollbackFailureReasons) == 0 {
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonCommandLaunchFailed)
		return
	}
	freshBase, err := e.startRole(ctx, "base")
	e.base = freshBase
	if err != nil {
		if components.IsOperational(err) {
			e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonOrchestrationError)
		} else {
			e.evidence.RollbackFailureReasons = append(e.evidence.RollbackFailureReasons, model.ReasonBaseRestartFailed)
		}
		return
	}
	e.evidence.FreshBaseStarted = true
	e.emit("BASE_RESTARTED", "rollback", map[string]any{"containers": containerIDs(freshBase)})
	rollbackScenarios := workloads.GroupScenarios(e.preparation.Config.Workloads.Rollback, e.preparation.Config.Workloads.Baseline, "base")
	rollback := e.runScenarioStage(ctx, "rollback", rollbackScenarios, "rollback")
	e.evidence.RollbackChecksCompleted = rollback.Passed
	if rollback.Incomplete {
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonInterrupted)
		return
	}
	if rollback.OperationalFailure && !rollback.ProjectFailure {
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonOrchestrationError)
	}
}

func (e *execution) sharedHealth(ctx context.Context) error {
	for _, container := range e.shared.ContainerIDs {
		inspect, err := e.owner.docker.Inspect(ctx, container)
		if err != nil {
			return fmt.Errorf("inspect shared service %s: %w", container, err)
		}
		if !inspect.State.Running || inspect.State.Health == nil || inspect.State.Health.Status != "healthy" {
			return fmt.Errorf("shared service %s lost health", container)
		}
	}
	return nil
}

func (e *execution) allInstances() []components.Instance {
	instances := make([]components.Instance, 0, len(e.base)+len(e.candidate))
	instances = append(instances, e.base...)
	instances = append(instances, e.candidate...)
	return instances
}

func (e *execution) runEnvironment(stage string) map[string]string {
	return map[string]string{
		"BACKLINE_RUN_ID":        e.session.RunID,
		"BACKLINE_STAGE":         stage,
		"BACKLINE_BASE_SHA":      e.preparation.BaseSHA,
		"BACKLINE_CANDIDATE_SHA": e.preparation.CandidateSHA,
		"BACKLINE_CONFIG_SHA256": e.preparation.ConfigSHA256,
		"BACKLINE_SHARED_DIR":    e.sharedDir,
		"BACKLINE_ARTIFACT_DIR":  e.session.Root,
	}
}

func (e *execution) addStage(name string, outcome model.StageOutcome, reason model.ReasonCode, message string, started time.Time) {
	e.addStageWithLog(name, outcome, reason, message, "", false, started)
}

func (e *execution) addStageWithLog(name string, outcome model.StageOutcome, reason model.ReasonCode, message, logPath string, truncated bool, started time.Time) {
	finished := time.Now().UTC()
	reasons := []model.ReasonCode{reason}
	if reason == "" {
		reasons = nil
	}
	e.stages = append(e.stages, model.StageResult{Name: name, Outcome: outcome, ReasonCodes: reasons, StartedAt: started.UTC(), FinishedAt: finished, DurationMS: finished.Sub(started).Milliseconds(), Message: message, LogPath: logPath, Truncated: truncated})
}

func (e *execution) addOperational(stage string, err error) {
	if err == nil {
		return
	}
	entry := model.OperationalError{Stage: stage, ReasonCode: model.ReasonOrchestrationError, Message: err.Error()}
	e.operational = append(e.operational, entry)
	e.evidence.OperationalErrors = append(e.evidence.OperationalErrors, entry)
}

func (e *execution) emit(eventType, stage string, details map[string]any) {
	if err := e.session.Emit(eventType, stage, details); err != nil {
		e.addOperational("artifacts", err)
	}
}

func (e *execution) verbosef(format string, values ...any) {
	if !e.options.Verbose {
		return
	}
	fmt.Fprintln(e.options.Out, e.redactor.String(fmt.Sprintf(format, values...)))
}

func (e *execution) verboseProcess(label string, result processrun.Result) {
	if !e.options.Verbose {
		return
	}
	e.verbosef("process %s: launched=%t exit=%d duration=%s", label, result.Launched, result.ExitCode, result.Duration.Round(time.Millisecond))
	if len(result.Stdout) > 0 {
		fmt.Fprintf(e.options.Out, "stdout:\n%s\n", e.redactor.String(strings.TrimSpace(string(result.Stdout))))
	}
	if len(result.Stderr) > 0 {
		fmt.Fprintf(e.options.Out, "stderr:\n%s\n", e.redactor.String(strings.TrimSpace(string(result.Stderr))))
	}
}

func (e *execution) addUncertainty(reason model.ReasonCode, mixed, rollback bool) {
	if mixed {
		e.evidence.MixedUncertaintyReasons = append(e.evidence.MixedUncertaintyReasons, reason)
	}
	if rollback {
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, reason)
	}
}

func (e *execution) registerContainer(id string) {
	_ = e.registry.Register(cleanup.Resource{
		Kind: "container", ID: id, ManualCommand: "docker rm --force --volumes " + id,
		Remove: func(ctx context.Context) error {
			err := e.owner.docker.RemoveContainer(ctx, id)
			if err != nil && strings.Contains(strings.ToLower(err.Error()), "no such") {
				return nil
			}
			return err
		},
		Exists: func(ctx context.Context) (bool, error) { return e.owner.docker.Exists(ctx, "container", id) },
	})
}

func (e *execution) registerRunLabeledResources() {
	label := dockerops.LabelRunID + "=" + e.session.RunID
	for _, kind := range []string{"container", "image"} {
		resourceKind := kind
		_ = e.registry.Register(cleanup.Resource{
			Kind: "docker-" + resourceKind + "-label", ID: label,
			ManualCommand: manualLabeledCleanup(resourceKind, label),
			Remove: func(ctx context.Context) error {
				ids, err := e.owner.docker.ListIDs(ctx, resourceKind, label)
				if err != nil {
					return err
				}
				var failures []error
				for _, id := range ids {
					if resourceKind == "container" {
						failures = appendIf(failures, e.owner.docker.RemoveContainer(ctx, id))
					} else {
						failures = appendIf(failures, e.owner.docker.RemoveImage(ctx, id))
					}
				}
				return errors.Join(failures...)
			},
			Exists: func(ctx context.Context) (bool, error) {
				ids, err := e.owner.docker.ListIDs(ctx, resourceKind, label)
				return len(ids) > 0, err
			},
		})
	}
}

func manualLabeledCleanup(kind, label string) string {
	list := "docker " + kind + " ls --quiet --filter \"label=" + label + "\""
	remove := "docker " + kind + " rm --force"
	if kind == "container" {
		remove = "docker rm --force --volumes"
	}
	if filepath.Separator == '\\' {
		return "$backlineIds = " + list + "; if ($backlineIds) { " + remove + " $backlineIds }"
	}
	return "backline_ids=$(" + list + "); [ -z \"$backline_ids\" ] || " + remove + " $backline_ids"
}

func (e *execution) registerImage(id string) {
	_ = e.registry.Register(cleanup.Resource{
		Kind: "image", ID: id, ManualCommand: "docker image rm --force " + id,
		Remove: func(ctx context.Context) error {
			err := e.owner.docker.RemoveImage(ctx, id)
			if err != nil && (strings.Contains(strings.ToLower(err.Error()), "no such") || strings.Contains(strings.ToLower(err.Error()), "not found")) {
				return nil
			}
			return err
		},
		Exists: func(ctx context.Context) (bool, error) { return e.owner.docker.Exists(ctx, "image", id) },
	})
}

func (e *execution) registerCompose() {
	project := e.composeInput.Project
	imageLabel := dockerops.LabelRunID + "=" + e.session.RunID
	manual := fmt.Sprintf("docker compose --project-name %s --file %q --file %q down --volumes --remove-orphans", project, e.composeInput.ComposePath, e.shared.OverridePath)
	if filepath.Separator == '\\' {
		manual += fmt.Sprintf("; $backlineImageIds = docker image ls --quiet --filter %q; if ($backlineImageIds) { docker image rm --force $backlineImageIds }", "label="+imageLabel)
	} else {
		manual += fmt.Sprintf("; backline_image_ids=$(docker image ls --quiet --filter %q); [ -z \"$backline_image_ids\" ] || docker image rm --force $backline_image_ids", "label="+imageLabel)
	}
	_ = e.registry.Register(cleanup.Resource{
		Kind: "compose", ID: project,
		ManualCommand: manual,
		Remove: func(ctx context.Context) error {
			var failures []error
			failures = appendIf(failures, e.owner.compose.Stop(ctx, e.composeInput, e.shared.OverridePath, time.Duration(e.preparation.Config.SharedEnvironment.ShutdownTimeoutSeconds)*time.Second))
			images, err := e.owner.docker.ListIDs(ctx, "image", imageLabel)
			failures = appendIf(failures, err)
			for _, image := range images {
				failures = appendIf(failures, e.owner.docker.RemoveImage(ctx, image))
			}
			return errors.Join(failures...)
		},
		Exists: func(ctx context.Context) (bool, error) {
			for _, kind := range []string{"container", "network", "volume"} {
				ids, err := e.owner.docker.ListIDs(ctx, kind, "com.docker.compose.project="+project)
				if err != nil {
					return false, err
				}
				if len(ids) > 0 {
					return true, nil
				}
			}
			images, err := e.owner.docker.ListIDs(ctx, "image", imageLabel)
			if err != nil || len(images) > 0 {
				return len(images) > 0, err
			}
			return false, nil
		},
	})
}

func (e *execution) finish(ctx context.Context, runErr error) (model.Summary, error) {
	if err := e.machine.Advance(StateReporting); err != nil {
		e.evidence.InternalError = true
		runErr = errors.Join(runErr, err)
	}
	if runErr != nil {
		if errors.Is(runErr, context.Canceled) || errors.Is(ctx.Err(), context.Canceled) {
			e.addUncertainty(model.ReasonInterrupted, true, true)
			e.addOperational("interrupted", runErr)
		} else {
			e.addOperational("orchestration", runErr)
			e.addUncertainty(model.ReasonOrchestrationError, true, true)
		}
	}
	e.completeSkippedStages()
	e.captureComponentLogs(context.Background(), e.base, "base-final")
	e.captureComponentLogs(context.Background(), e.candidate, "candidate-final")
	e.captureSharedLogs(context.Background())
	initial := verdicts.Classify(e.evidence)
	retain := e.options.KeepOnFailure && initial.Overall != model.OverallPass
	if err := e.machine.Advance(StateCleanup); err != nil {
		e.evidence.InternalError = true
		runErr = errors.Join(runErr, err)
	}
	cleanupStarted := time.Now()
	cleanupTimeout := 5*time.Minute + time.Duration(e.preparation.Config.SharedEnvironment.ShutdownTimeoutSeconds)*time.Second
	cleanupContext, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
	defer cancel()
	e.cleanupResult = e.registry.Cleanup(cleanupContext, retain, func(message string) { e.verbosef("cleanup: %s", message) })
	if retain {
		e.cleanupResult.Remaining = append(e.cleanupResult.Remaining, e.preflightManualResources()...)
	} else if err := e.options.PreflightCleanup(); err != nil {
		e.cleanupResult.Status = model.StageError
		e.cleanupResult.ReasonCodes = []model.ReasonCode{model.ReasonCleanupFailed}
		e.cleanupResult.Remaining = append(e.cleanupResult.Remaining, "preflight cleanup failed: "+err.Error())
		e.cleanupResult.Remaining = append(e.cleanupResult.Remaining, e.preflightManualResources()...)
	}
	e.cleanupResult.DurationMS = time.Since(cleanupStarted).Milliseconds()
	e.evidence.CleanupFailed = e.cleanupResult.Status == model.StageError
	classified := verdicts.Classify(e.evidence)
	finished := time.Now().UTC()
	summary := model.Summary{
		SchemaVersion: 1, RunID: e.session.RunID, Status: classified.Overall, ExitCode: classified.ExitCode,
		StartedAt: e.startedAt, FinishedAt: finished,
		Config:    model.ConfigIdentity{Path: e.preparation.ConfigPath, SourceSHA: e.preparation.CandidateSHA, SHA256: e.preparation.ConfigSHA256},
		Base:      model.RevisionIdentity{Ref: e.preparation.BaseRef, SHA: e.preparation.BaseSHA},
		Candidate: model.RevisionIdentity{Ref: e.preparation.CandidateRef, SHA: e.preparation.CandidateSHA},
		Environment: model.EnvironmentIdentity{
			EnvFileDisplay: e.preparation.EnvFileDisplay, EnvFileExternal: e.preparation.EnvFileExternal, EnvFileSHA256: e.preparation.EnvFileSHA256,
			DockerContext: e.preparation.DockerContext, DockerVersion: e.preparation.DockerVersion, ComposeVersion: e.preparation.ComposeVersion,
			SelectedServices: nonnilStrings(e.preparation.Config.SharedEnvironment.Services), Networks: nonnilStrings(e.shared.Networks),
			SharedServiceImages: nonnilMap(e.shared.ServiceImageIDs),
		},
		ComponentImages: nonnilMap(e.imageIDs), ComponentEndpoints: nonnilMap(e.endpoints), RollbackMode: e.evidence.RollbackMode,
		Verdicts: map[string]model.Verdict{"mixed_version": classified.Mixed, "rollback": classified.Rollback},
		Controls: e.controls, Stages: nonnilStages(e.stages), Scenarios: nonnilSteps(e.steps),
		OperationalErrors: nonnilOperational(e.operational),
		Artifacts: map[string]string{
			"report": filepath.Join(e.session.Root, "report.md"), "summary": filepath.Join(e.session.Root, "summary.json"),
			"events": filepath.Join(e.session.Root, "events.jsonl"), "resolved_config": filepath.Join(e.session.Root, "resolved-config.redacted.yml"),
		},
		Cleanup:  e.cleanupResult,
		Warnings: runWarnings(e.options),
	}
	if err := e.session.Emit("VERDICT_FINALIZED", "reporting", map[string]any{"mixed": classified.Mixed.Status, "rollback": classified.Rollback.Status, "rollback_mode": e.evidence.RollbackMode}); err != nil {
		e.addOperational("artifacts", err)
	}
	if err := e.session.Emit("CLEANUP_COMPLETED", "cleanup", map[string]any{"status": e.cleanupResult.Status, "remaining": e.cleanupResult.Remaining}); err != nil {
		e.addOperational("artifacts", err)
	}
	e.refreshSummary(&summary)
	report := reporting.Markdown(summary, e.options.Version)
	if err := e.session.WriteText("report.md", report); err != nil {
		return e.artifactFailure(&summary, err)
	}
	if e.options.JUnitOutput != "" {
		junit, err := reporting.JUnit(summary)
		if err != nil {
			return e.artifactFailure(&summary, err)
		}
		if err := e.session.WriteText("junit.xml", string(junit)); err != nil {
			return e.artifactFailure(&summary, err)
		}
		if err := e.session.WriteExternal(e.options.JUnitOutput, junit); err != nil {
			return e.artifactFailure(&summary, err)
		}
		summary.Artifacts["junit"] = filepath.Join(e.session.Root, "junit.xml")
	}
	if githubSummary := os.Getenv("GITHUB_STEP_SUMMARY"); githubSummary != "" {
		if err := e.session.AppendExternal(githubSummary, []byte(report+"\n")); err != nil {
			e.addOperational("reporting", fmt.Errorf("append GitHub Step Summary: %w", err))
			e.refreshSummary(&summary)
			report = reporting.Markdown(summary, e.options.Version)
			if writeErr := e.session.WriteText("report.md", report); writeErr != nil {
				return e.artifactFailure(&summary, errors.Join(err, writeErr))
			}
		}
	}
	if err := e.session.WriteSummary(summary); err != nil {
		return e.artifactFailure(&summary, err)
	}
	if e.options.JSONOutput != "" {
		if err := e.session.CopySummary(e.options.JSONOutput); err != nil {
			return e.artifactFailure(&summary, err)
		}
	}
	if err := e.session.Close(); err != nil {
		return e.artifactFailure(&summary, err)
	}
	if err := e.machine.Advance(StateComplete); err != nil {
		e.evidence.InternalError = true
		e.refreshSummary(&summary)
		return summary, err
	}
	fmt.Fprint(e.options.Out, e.redactor.String(reporting.Terminal(summary)))
	return summary, runErr
}

func (e *execution) preflightManualResources() []string {
	repository := e.preparation.Repository.Root
	baseCommand := fmt.Sprintf("git -C %q worktree remove --force %q", repository, e.preparation.BaseWorktree)
	candidateCommand := fmt.Sprintf("git -C %q worktree remove --force %q", repository, e.preparation.CandidateWorktree)
	pathCommand := fmt.Sprintf("Remove-Item -LiteralPath %q -Recurse -Force", e.preparation.TemporaryRoot)
	if filepath.Separator != '\\' {
		pathCommand = fmt.Sprintf("rm -rf -- %q", e.preparation.TemporaryRoot)
	}
	return []string{
		"worktree:" + e.preparation.BaseWorktree + " | " + baseCommand,
		"worktree:" + e.preparation.CandidateWorktree + " | " + candidateCommand,
		"path:" + e.preparation.TemporaryRoot + " | " + pathCommand,
	}
}

func (e *execution) refreshSummary(summary *model.Summary) {
	classified := verdicts.Classify(e.evidence)
	summary.Status = classified.Overall
	summary.ExitCode = classified.ExitCode
	summary.Verdicts = map[string]model.Verdict{"mixed_version": classified.Mixed, "rollback": classified.Rollback}
	summary.OperationalErrors = nonnilOperational(e.operational)
}

func (e *execution) artifactFailure(summary *model.Summary, err error) (model.Summary, error) {
	e.addOperational("artifacts", err)
	e.refreshSummary(summary)
	_ = e.session.WriteText("report.md", reporting.Markdown(*summary, e.options.Version))
	_ = e.session.WriteSummary(*summary)
	_ = e.session.Close()
	return *summary, err
}

func (e *execution) completeSkippedStages() {
	expected := []string{
		"build", "shared_environment", "bootstrap", "base_startup", "baseline", "transition",
		"base_after_transition_control", "candidate_startup", "candidate_control",
		"base_with_candidate_control", "coexistence", "candidate_only", "candidate_traffic",
		"rollback_hooks", "base_restart", "rollback",
	}
	existing := make(map[string]struct{}, len(e.stages))
	for _, stage := range e.stages {
		existing[stage.Name] = struct{}{}
	}
	reason := model.ReasonNone
	for _, reasons := range [][]model.ReasonCode{e.evidence.MixedUncertaintyReasons, e.evidence.RollbackUncertaintyReasons} {
		if len(reasons) > 0 {
			reason = reasons[0]
			break
		}
	}
	for _, name := range expected {
		if _, exists := existing[name]; exists {
			continue
		}
		now := time.Now().UTC()
		e.stages = append(e.stages, model.StageResult{
			Name: name, Outcome: model.StageSkipped, ReasonCodes: []model.ReasonCode{reason},
			StartedAt: now, FinishedAt: now, Message: "not reached because a required earlier stage did not complete",
		})
	}
}

func (e *execution) captureComponentLogs(ctx context.Context, instances []components.Instance, phase string) {
	for _, instance := range instances {
		logs := e.owner.docker.Logs(ctx, instance.Container, e.preparation.Config.Artifacts.MaxLogBytesPerCommand)
		if logs.Err != nil || !logs.Launched {
			continue
		}
		path := filepath.ToSlash(filepath.Join("logs", "components", sanitize(phase), instance.Role+"-"+instance.Component.Name+".log"))
		e.storeLog(path, append(logs.Stdout, logs.Stderr...))
	}
}

func (e *execution) captureSharedLogs(ctx context.Context) {
	for service, container := range e.shared.ContainerIDs {
		logs := e.owner.docker.Logs(ctx, container, e.preparation.Config.Artifacts.MaxLogBytesPerCommand)
		if logs.Err != nil || !logs.Launched {
			continue
		}
		path := filepath.ToSlash(filepath.Join("logs", "shared", sanitize(service)+".log"))
		e.storeLog(path, append(logs.Stdout, logs.Stderr...))
	}
}

func (e *execution) captureInspect(ctx context.Context, instances []components.Instance, phase string) {
	for _, instance := range instances {
		inspection, err := e.owner.docker.Inspect(ctx, instance.Container)
		if err != nil {
			continue
		}
		path := filepath.ToSlash(filepath.Join("logs", "diagnostics", sanitize(phase), instance.Role+"-"+instance.Component.Name+"-inspect.json"))
		if _, _, err := e.session.WriteJSONLog(path, inspection); err != nil {
			e.addOperational("artifacts", err)
		}
	}
}

func (e *execution) storeLog(relativePath string, contents []byte) (string, bool) {
	path, truncated, err := e.session.WriteLog(relativePath, contents)
	if err != nil {
		e.addOperational("artifacts", err)
		return "", false
	}
	return path, truncated
}

func stageReason(stage string) model.ReasonCode {
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

func sanitize(value string) string {
	return strings.Trim(strings.Map(func(character rune) rune {
		if character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' || character >= '0' && character <= '9' || character == '-' {
			return character
		}
		return '-'
	}, value), "-")
}

func errorMessage(values ...error) string {
	for _, value := range values {
		if value != nil {
			return value.Error()
		}
	}
	return "command could not be launched"
}

func containerIDs(instances []components.Instance) []string {
	result := make([]string, 0, len(instances))
	for _, instance := range instances {
		result = append(result, instance.Container)
	}
	return result
}

func appendIf(values []error, err error) []error {
	if err != nil && !strings.Contains(strings.ToLower(err.Error()), "no such") && !os.IsNotExist(err) {
		return append(values, err)
	}
	return values
}

func nonnilMap(values map[string]string) map[string]string {
	if values == nil {
		return map[string]string{}
	}
	return values
}

func nonnilStages(values []model.StageResult) []model.StageResult {
	if values == nil {
		return []model.StageResult{}
	}
	return values
}

func nonnilSteps(values []model.StepResult) []model.StepResult {
	if values == nil {
		return []model.StepResult{}
	}
	return values
}

func nonnilOperational(values []model.OperationalError) []model.OperationalError {
	if values == nil {
		return []model.OperationalError{}
	}
	return values
}

func nonnilStrings(values []string) []string {
	if values == nil {
		return []string{}
	}
	return append([]string(nil), values...)
}

func runWarnings(options Options) []string {
	warnings := make([]string, 0, 2)
	if options.AllowUnsafeCompose {
		warnings = append(warnings, "Unsafe Compose validation was explicitly relaxed for this run; cleanup ownership remained restricted to current-run resources.")
	}
	if options.AllowRemoteDocker {
		warnings = append(warnings, "A trusted remote Docker endpoint was explicitly permitted for this run.")
	}
	return warnings
}
