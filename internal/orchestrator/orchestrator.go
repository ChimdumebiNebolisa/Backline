package orchestrator

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/artifacts"
	"github.com/ChimdumebiNebolisa/Backline/internal/cleanup"
	"github.com/ChimdumebiNebolisa/Backline/internal/components"
	"github.com/ChimdumebiNebolisa/Backline/internal/compose"
	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	"github.com/ChimdumebiNebolisa/Backline/internal/dockerops"
	"github.com/ChimdumebiNebolisa/Backline/internal/hooks"
	"github.com/ChimdumebiNebolisa/Backline/internal/model"
	"github.com/ChimdumebiNebolisa/Backline/internal/preflight"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
	"github.com/ChimdumebiNebolisa/Backline/internal/redact"
	"github.com/ChimdumebiNebolisa/Backline/internal/verdicts"
	"github.com/ChimdumebiNebolisa/Backline/internal/workloads"
)

type Options struct {
	Preparation        *preflight.Preparation
	PreflightCleanup   func() error
	KeepOnFailure      bool
	JSONOutput         string
	JUnitOutput        string
	Verbose            bool
	Out                io.Writer
	Version            string
	AllowUnsafeCompose bool
	AllowRemoteDocker  bool
}

type Orchestrator struct {
	runner     *processrun.Runner
	docker     *dockerops.Client
	compose    *compose.Manager
	components *components.Manager
}

func New() *Orchestrator {
	runner := processrun.NewRunner()
	docker := dockerops.New(runner)
	return &Orchestrator{runner: runner, docker: docker, compose: compose.New(runner, docker), components: components.New(docker)}
}

type execution struct {
	owner         *Orchestrator
	options       Options
	preparation   *preflight.Preparation
	redactor      *redact.Redactor
	session       *artifacts.Session
	registry      *cleanup.Registry
	hooks         *hooks.Executor
	workloads     *workloads.Executor
	evidence      verdicts.Evidence
	stages        []model.StageResult
	steps         []model.StepResult
	controls      map[string]model.StageOutcome
	operational   []model.OperationalError
	imageRefs     map[string]map[string]string
	imageIDs      map[string]string
	endpoints     map[string]string
	composeInput  compose.StartInput
	shared        compose.Environment
	base          []components.Instance
	candidate     []components.Instance
	sharedDir     string
	scenarioRoot  string
	cleanupResult model.CleanupResult
	startedAt     time.Time
	machine       *StateMachine
}

func (o *Orchestrator) Run(ctx context.Context, options Options) (model.Summary, error) {
	if options.Preparation == nil || options.PreflightCleanup == nil {
		return model.Summary{}, errors.New("orchestration requires a preparation and cleanup function")
	}
	if options.Out == nil {
		options.Out = io.Discard
	}
	redactor := redact.New(options.Preparation.RegisteredSecrets...)
	session, err := artifacts.NewSession(options.Preparation.ArtifactRoot, "", options.Preparation.Config.Artifacts.MaxLogBytesPerCommand, redactor)
	if err != nil {
		_ = options.PreflightCleanup()
		return model.Summary{}, err
	}
	execution := &execution{
		owner:       o,
		options:     options,
		preparation: options.Preparation,
		redactor:    redactor,
		session:     session,
		registry:    cleanup.NewRegistry(session.RunID),
		controls: map[string]model.StageOutcome{
			"base_after_transition":        model.StageSkipped,
			"candidate_before_coexistence": model.StageSkipped,
			"base_with_candidate_running":  model.StageSkipped,
		},
		imageRefs: map[string]map[string]string{"base": {}, "candidate": {}},
		imageIDs:  map[string]string{},
		endpoints: map[string]string{},
		startedAt: time.Now().UTC(),
		machine:   NewStateMachine(),
	}
	execution.registerRunLabeledResources()
	execution.hooks = hooks.New(o.runner, o.docker)
	execution.workloads = workloads.New(o.runner, session)
	execution.evidence.RollbackMode = model.RollbackRaw
	if len(execution.preparation.Config.Lifecycle.Rollback) > 0 {
		execution.evidence.RollbackMode = model.RollbackPrepared
	}
	execution.evidence.CandidateOnlyCompleted = len(execution.preparation.Config.Lifecycle.CandidateOnly) == 0
	execution.evidence.RollbackHooksCompleted = len(execution.preparation.Config.Lifecycle.Rollback) == 0
	execution.sharedDir = filepath.Join(execution.preparation.TemporaryRoot, "shared")
	execution.scenarioRoot = filepath.Join(execution.preparation.TemporaryRoot, "scenarios")
	fmt.Fprintln(options.Out, "Backline verify")
	fmt.Fprintln(options.Out)
	if err := os.MkdirAll(execution.sharedDir, 0o700); err != nil {
		return execution.finish(ctx, fmt.Errorf("create shared handoff directory: %w", err))
	}
	if err := os.MkdirAll(execution.scenarioRoot, 0o700); err != nil {
		return execution.finish(ctx, fmt.Errorf("create scenario root: %w", err))
	}
	if err := session.Emit("RUN_CREATED", "preflight", map[string]any{"base_sha": execution.preparation.BaseSHA, "candidate_sha": execution.preparation.CandidateSHA}); err != nil {
		return execution.finish(ctx, err)
	}
	execution.emit("CONFIG_RESOLVED", "preflight", map[string]any{"path": execution.preparation.ConfigPath, "sha256": execution.preparation.ConfigSHA256})
	execution.emit("WORKTREE_CREATED", "preflight", map[string]any{"role": "base", "sha": execution.preparation.BaseSHA})
	execution.emit("WORKTREE_CREATED", "preflight", map[string]any{"role": "candidate", "sha": execution.preparation.CandidateSHA})
	if err := session.WriteYAML("resolved-config.redacted.yml", execution.preparation.ResolvedConfig); err != nil {
		return execution.finish(ctx, err)
	}
	if options.Verbose {
		fmt.Fprintf(options.Out, "Run %s: base %s, candidate %s\n", session.RunID, short(execution.preparation.BaseSHA), short(execution.preparation.CandidateSHA))
	}
	return execution.finish(ctx, execution.execute(ctx))
}

func (e *execution) execute(ctx context.Context) (runErr error) {
	defer func() {
		if recovered := recover(); recovered != nil {
			e.evidence.InternalError = true
			runErr = errors.New("unexpected internal Backline error")
		}
	}()
	e.runLifecycle(ctx)
	return ctx.Err()
}

func (e *execution) runLifecycle(ctx context.Context) {
	e.enter(StateBuild)
	if !e.buildImages(ctx) {
		return
	}
	e.enter(StateShared)
	if !e.startSharedEnvironment(ctx) {
		return
	}
	e.enter(StateBootstrap)
	if !e.runHooks(ctx, "bootstrap", e.preparation.Config.Lifecycle.Bootstrap, model.ReasonBootstrapFailed, false) {
		reason := model.ReasonBootstrapFailed
		if ctx.Err() != nil {
			reason = model.ReasonInterrupted
		}
		e.addUncertainty(reason, true, true)
		return
	}
	e.enter(StateBaseStartup)
	base, err := e.startRole(ctx, "base")
	e.base = base
	if err != nil {
		e.addUncertainty(model.ReasonBaseStartupFailed, true, true)
		return
	}
	e.enter(StateBaseline)
	baseline := e.runScenarioStage(ctx, "baseline", e.preparation.Config.Workloads.Baseline, "precondition")
	e.evidence.BaselinePassed = baseline.Passed
	if !e.evidence.BaselinePassed {
		reason := model.ReasonBaselineFailed
		if baseline.Incomplete {
			reason = model.ReasonInterrupted
		}
		e.addUncertainty(reason, true, true)
		return
	}
	e.enter(StateTransition)
	e.emit("TRANSITION_STARTED", "transition", map[string]any{})
	if !e.runHooks(ctx, "transition", e.preparation.Config.Lifecycle.Transition, model.ReasonTransitionHookFailed, false) {
		reason := model.ReasonTransitionHookFailed
		if ctx.Err() != nil {
			reason = model.ReasonInterrupted
		}
		e.addUncertainty(reason, true, true)
		return
	}
	e.evidence.TransitionPassed = true
	e.enter(StateBaseControl)
	baseUsable, baseCheckOperational := e.checkBase(ctx, "base_after_transition")
	if baseCheckOperational {
		e.controls["base_after_transition"] = model.StageError
		e.addUncertainty(model.ReasonOrchestrationError, true, true)
		return
	}
	if baseUsable {
		scenarios := workloads.GroupScenarios(e.preparation.Config.Workloads.Controls.BaseAfterTransition, e.preparation.Config.Workloads.Baseline, "base")
		control := e.runScenarioStage(ctx, "base_after_transition_control", scenarios, "mixed")
		e.evidence.BaseAfterTransitionPassed = control.Passed
		e.controls["base_after_transition"] = executionOutcome(control)
		e.emit("BASE_CONTROL_COMPLETED", "base_after_transition_control", map[string]any{"outcome": e.controls["base_after_transition"]})
		if control.OperationalFailure && !control.ProjectFailure {
			e.addUncertainty(model.ReasonOrchestrationError, true, false)
		}
		if control.Incomplete {
			e.addUncertainty(model.ReasonInterrupted, true, true)
			return
		}
	} else {
		e.controls["base_after_transition"] = model.StageFail
		e.evidence.MixedFailureReasons = append(e.evidence.MixedFailureReasons, model.ReasonBaseLostReadiness)
	}
	e.enter(StateCandidateStartup)
	candidate, candidateErr := e.startRole(ctx, "candidate")
	e.candidate = candidate
	if candidateErr != nil {
		e.addUncertainty(model.ReasonCandidateStartupFailed, len(e.evidence.MixedFailureReasons) == 0, true)
		return
	}
	e.evidence.CandidateStarted = true
	e.enter(StateCandidateControl)
	candidateControl := workloads.GroupScenarios(e.preparation.Config.Workloads.Controls.CandidateBeforeCoexistence, e.preparation.Config.Workloads.Baseline, "candidate")
	candidateControlExecution := e.runScenarioStage(ctx, "candidate_control", candidateControl, "precondition")
	e.evidence.CandidateControlPassed = candidateControlExecution.Passed
	e.controls["candidate_before_coexistence"] = executionOutcome(candidateControlExecution)
	e.emit("CANDIDATE_CONTROL_COMPLETED", "candidate_control", map[string]any{"outcome": e.controls["candidate_before_coexistence"]})
	if !e.evidence.CandidateControlPassed {
		reason := model.ReasonCandidateControlFailed
		if candidateControlExecution.Incomplete {
			reason = model.ReasonInterrupted
		} else if candidateControlExecution.OperationalFailure && !candidateControlExecution.ProjectFailure {
			reason = model.ReasonOrchestrationError
		}
		e.addUncertainty(reason, len(e.evidence.MixedFailureReasons) == 0, true)
		return
	}
	e.enter(StateMixedControl)
	secondBaseUsable, secondBaseOperational := e.checkBase(ctx, "base_with_candidate")
	if secondBaseOperational {
		e.controls["base_with_candidate_running"] = model.StageError
		e.addUncertainty(model.ReasonOrchestrationError, len(e.evidence.MixedFailureReasons) == 0, true)
		return
	}
	if baseUsable && secondBaseUsable {
		baseControl := workloads.GroupScenarios(e.preparation.Config.Workloads.Controls.BaseWithCandidateRunning, e.preparation.Config.Workloads.Baseline, "base")
		baseControlExecution := e.runScenarioStage(ctx, "base_with_candidate_control", baseControl, "mixed")
		e.evidence.BaseWithCandidatePassed = baseControlExecution.Passed
		e.controls["base_with_candidate_running"] = executionOutcome(baseControlExecution)
		e.emit("BASE_WITH_CANDIDATE_CONTROL_COMPLETED", "base_with_candidate_control", map[string]any{"outcome": e.controls["base_with_candidate_running"]})
		if baseControlExecution.OperationalFailure && !baseControlExecution.ProjectFailure {
			e.addUncertainty(model.ReasonOrchestrationError, true, false)
		}
		if baseControlExecution.Incomplete {
			e.addUncertainty(model.ReasonInterrupted, len(e.evidence.MixedFailureReasons) == 0, true)
			return
		}
	} else {
		baseUsable = false
		e.controls["base_with_candidate_running"] = model.StageFail
		e.evidence.MixedFailureReasons = append(e.evidence.MixedFailureReasons, model.ReasonBaseLostReadiness)
	}
	e.enter(StateCoexistence)
	if coexistenceEligible(baseUsable, e.evidence.BaseAfterTransitionPassed, e.evidence.CandidateControlPassed, e.evidence.BaseWithCandidatePassed) {
		mixedScenarios := append([]config.Scenario(nil), e.preparation.Config.Workloads.Coexistence.BaseToCandidate...)
		mixedScenarios = append(mixedScenarios, e.preparation.Config.Workloads.Coexistence.CandidateToBase...)
		mixedScenarios = append(mixedScenarios, e.preparation.Config.Workloads.Coexistence.Alternating...)
		coexistence := e.runScenarioStage(ctx, "coexistence", mixedScenarios, "mixed")
		e.evidence.CoexistenceCompleted = coexistence.Passed
		if coexistence.OperationalFailure && !coexistence.ProjectFailure {
			e.addUncertainty(model.ReasonOrchestrationError, true, false)
		}
		if coexistence.Incomplete {
			e.addUncertainty(model.ReasonInterrupted, len(e.evidence.MixedFailureReasons) == 0, true)
			return
		}
	}
	e.captureComponentLogs(ctx, e.base, "base-before-candidate-only")
	baseStopStarted := time.Now()
	if err := e.owner.components.StopAll(ctx, e.base, e.preparation.Config.SharedEnvironment.ShutdownTimeoutSeconds); err != nil {
		e.addOperational("base_stop", err)
		e.addUncertainty(model.ReasonOrchestrationError, false, true)
		e.addStage("base_stop", model.StageError, model.ReasonOrchestrationError, err.Error(), baseStopStarted)
		return
	}
	e.base = nil
	e.addStage("base_stop", model.StagePass, model.ReasonNone, "", baseStopStarted)
	e.enter(StateCandidateOnly)
	if !e.runHooks(ctx, "candidate_only", e.preparation.Config.Lifecycle.CandidateOnly, model.ReasonCandidateOnlyHookFailed, false) {
		reason := model.ReasonCandidateOnlyHookFailed
		if ctx.Err() != nil {
			reason = model.ReasonInterrupted
		}
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, reason)
		if ctx.Err() != nil {
			return
		}
		e.enter(StateCandidateTraffic)
		e.addStage("candidate_traffic", model.StageSkipped, model.ReasonCandidateOnlyHookFailed, "candidate traffic skipped because candidate-only hooks did not complete", time.Now())
		e.enter(StateRollback)
		e.runRollback(ctx)
		return
	}
	candidateOnlyHealthStarted := time.Now()
	if err := e.owner.components.Check(ctx, e.candidate); err != nil {
		reason := model.ReasonRequiredComponentExited
		stageOutcome := model.StageFail
		if components.IsOperational(err) {
			reason = model.ReasonOrchestrationError
			stageOutcome = model.StageError
			e.addOperational("candidate_only", err)
		}
		e.addStage("candidate_only_health", stageOutcome, reason, err.Error(), candidateOnlyHealthStarted)
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, reason)
		e.enter(StateCandidateTraffic)
		e.addStage("candidate_traffic", model.StageSkipped, reason, "candidate traffic skipped because a required candidate component was not healthy", time.Now())
		e.enter(StateRollback)
		e.runRollback(ctx)
		return
	}
	e.addStage("candidate_only_health", model.StagePass, model.ReasonNone, "", candidateOnlyHealthStarted)
	e.evidence.CandidateOnlyCompleted = true
	e.enter(StateCandidateTraffic)
	candidateTraffic := e.runScenarioStage(ctx, "candidate_traffic", e.preparation.Config.Workloads.CandidateTraffic, "traffic")
	e.evidence.CandidateTrafficCompleted = candidateTraffic.Passed
	if candidateTraffic.Incomplete {
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonInterrupted)
		return
	}
	if !e.evidence.CandidateTrafficCompleted {
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonCandidateTrafficFailed, model.ReasonCandidateTrafficIncomplete)
	}
	if !e.evidence.CandidateMutationRan {
		e.evidence.RollbackUncertaintyReasons = append(e.evidence.RollbackUncertaintyReasons, model.ReasonNoCandidateMutationScenarioRan)
	}
	e.enter(StateRollback)
	e.runRollback(ctx)
}

func (e *execution) enter(state LifecycleState) {
	if err := e.machine.Advance(state); err != nil {
		panic(err)
	}
	label := operationLabel(state)
	e.emit("OPERATION_STARTED", strings.ToLower(string(state)), map[string]any{"operation": label})
	fmt.Fprintf(e.options.Out, "  %-28s %s\n", strings.ToLower(string(state)), label)
}

func operationLabel(state LifecycleState) string {
	labels := map[LifecycleState]string{
		StateBuild: "building base and candidate images", StateShared: "starting shared services",
		StateBootstrap: "running bootstrap hooks", StateBaseStartup: "starting base components",
		StateBaseline: "establishing baseline", StateTransition: "running candidate transition",
		StateBaseControl: "checking base after transition", StateCandidateStartup: "starting candidate components",
		StateCandidateControl: "checking candidate before coexistence", StateMixedControl: "checking original base with candidate running",
		StateCoexistence: "running mixed-version scenarios", StateCandidateOnly: "running candidate-only hooks",
		StateCandidateTraffic: "running candidate traffic", StateRollback: "verifying rollback",
	}
	if label := labels[state]; label != "" {
		return label
	}
	return strings.ToLower(string(state))
}

func (e *execution) buildImages(ctx context.Context) bool {
	started := time.Now()
	for _, role := range []string{"base", "candidate"} {
		worktree := e.preparation.BaseWorktree
		sha := e.preparation.BaseSHA
		if role == "candidate" {
			worktree, sha = e.preparation.CandidateWorktree, e.preparation.CandidateSHA
		}
		for _, component := range e.preparation.Config.Release.Components {
			componentStarted := time.Now()
			tag := strings.ToLower(fmt.Sprintf("backline/%s/%s-%s:%s", e.session.RunID, role, component.Name, short(sha)))
			e.emit("IMAGE_BUILD_STARTED", "build", map[string]any{"role": role, "component": component.Name, "sha": sha})
			imageID, result, err := e.owner.docker.Build(ctx, dockerops.BuildInput{
				Context:    filepath.Join(worktree, filepath.FromSlash(component.Build.Context)),
				Dockerfile: filepath.Join(worktree, filepath.FromSlash(component.Build.Dockerfile)),
				Tag:        tag,
				Labels:     map[string]string{dockerops.LabelRunID: e.session.RunID, dockerops.LabelOwned: "true", dockerops.LabelRole: role, dockerops.LabelComponent: component.Name, dockerops.LabelRevision: sha},
				Timeout:    30 * time.Minute,
				MaxBytes:   e.preparation.Config.Artifacts.MaxLogBytesPerCommand,
			})
			e.verboseProcess("build/"+role+"/"+component.Name, result)
			logPath, logTruncated := e.storeLog(filepath.ToSlash(filepath.Join("builds", role+"-"+component.Name+".log")), append(result.Stdout, result.Stderr...))
			logTruncated = logTruncated || result.Truncated
			if result.Canceled || ctx.Err() != nil {
				e.addUncertainty(model.ReasonInterrupted, true, true)
				e.addStage("build", model.StageIncomplete, model.ReasonInterrupted, "image build canceled", started)
				e.addStageWithLog("build/"+role+"/"+component.Name, model.StageIncomplete, model.ReasonInterrupted, "image build canceled", logPath, logTruncated, componentStarted)
				return false
			}
			if err != nil {
				e.addOperational("build", err)
				e.addUncertainty(model.ReasonOrchestrationError, true, true)
				e.addStage("build", model.StageError, model.ReasonOrchestrationError, err.Error(), started)
				e.addStageWithLog("build/"+role+"/"+component.Name, model.StageError, model.ReasonOrchestrationError, err.Error(), logPath, logTruncated, componentStarted)
				return false
			}
			if result.ExitCode != 0 {
				e.addUncertainty(model.ReasonBuildFailed, true, true)
				e.addStage("build", model.StageFail, model.ReasonBuildFailed, fmt.Sprintf("%s.%s image build failed", role, component.Name), started)
				e.addStageWithLog("build/"+role+"/"+component.Name, model.StageFail, model.ReasonBuildFailed, fmt.Sprintf("image build exited %d", result.ExitCode), logPath, logTruncated, componentStarted)
				return false
			}
			e.imageRefs[role][component.Name] = tag
			e.imageIDs[role+"."+component.Name] = imageID
			e.registerImage(imageID)
			e.addStageWithLog("build/"+role+"/"+component.Name, model.StagePass, model.ReasonNone, "", logPath, logTruncated, componentStarted)
			e.emit("IMAGE_BUILD_COMPLETED", "build", map[string]any{"role": role, "component": component.Name, "image_id": imageID})
		}
	}
	e.addStage("build", model.StagePass, model.ReasonNone, "", started)
	return true
}

func (e *execution) startSharedEnvironment(ctx context.Context) bool {
	started := time.Now()
	e.composeInput = compose.StartInput{
		RunID: e.session.RunID, Project: "backline-" + e.session.RunID,
		Worktree: e.preparation.CandidateWorktree, ComposePath: e.preparation.ComposePath,
		ComposeJSON: e.preparation.ComposeJSON, EnvFile: e.preparation.EnvFilePath,
		Services: e.preparation.Config.SharedEnvironment.Services, TemporaryRoot: e.preparation.TemporaryRoot,
		StartupTimeout: time.Duration(e.preparation.Config.SharedEnvironment.StartupTimeoutSeconds) * time.Second,
		MaxBytes:       e.preparation.Config.Artifacts.MaxLogBytesPerCommand,
	}
	environment, result, err := e.owner.compose.Start(ctx, e.composeInput)
	e.verboseProcess("shared_environment", result)
	e.shared = environment
	logPath, logTruncated := e.storeLog("logs/shared-environment.log", append(result.Stdout, result.Stderr...))
	logTruncated = logTruncated || result.Truncated
	if environment.OverridePath != "" {
		e.registerCompose()
	}
	if result.Canceled || ctx.Err() != nil {
		e.addUncertainty(model.ReasonInterrupted, true, true)
		e.addStageWithLog("shared_environment", model.StageIncomplete, model.ReasonInterrupted, "shared environment startup canceled", logPath, logTruncated, started)
		return false
	}
	if err != nil || result.ExitCode != 0 {
		message := "shared environment failed"
		if err != nil {
			message = err.Error()
		}
		e.addOperational("shared_environment", errors.New(message))
		e.addUncertainty(model.ReasonSharedEnvironmentFailed, true, true)
		e.addStageWithLog("shared_environment", model.StageError, model.ReasonSharedEnvironmentFailed, message, logPath, logTruncated, started)
		return false
	}
	e.addStageWithLog("shared_environment", model.StagePass, model.ReasonNone, "", logPath, logTruncated, started)
	e.emit("SHARED_SERVICES_READY", "shared_environment", map[string]any{"networks": environment.Networks, "images": environment.ServiceImageIDs})
	return true
}

func short(value string) string {
	if len(value) <= 7 {
		return value
	}
	return value[:7]
}

func executionOutcome(execution stageExecution) model.StageOutcome {
	if execution.Passed {
		return model.StagePass
	}
	if execution.OperationalFailure && !execution.ProjectFailure {
		return model.StageError
	}
	if execution.Incomplete && !execution.ProjectFailure {
		return model.StageIncomplete
	}
	return model.StageFail
}

func coexistenceEligible(baseUsable, firstBaseControl, candidateControl, secondBaseControl bool) bool {
	return baseUsable && firstBaseControl && candidateControl && secondBaseControl
}
