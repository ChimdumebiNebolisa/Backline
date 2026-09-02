package app

import (
	"context"
	"errors"
	"fmt"
	"io"

	"github.com/ChimdumebiNebolisa/Backline/internal/cli"
	"github.com/ChimdumebiNebolisa/Backline/internal/model"
	"github.com/ChimdumebiNebolisa/Backline/internal/orchestrator"
	"github.com/ChimdumebiNebolisa/Backline/internal/preflight"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

type Service struct {
	out          io.Writer
	preflight    *preflight.Service
	orchestrator *orchestrator.Orchestrator
	version      string
}

func New(out io.Writer, version string) *Service {
	runner := processrun.NewRunner()
	return &Service{out: out, preflight: preflight.New(runner), orchestrator: orchestrator.New(), version: version}
}

func (s *Service) Verify(ctx context.Context, options cli.VerifyOptions) (int, error) {
	s.printUnsafeWarnings(options.AllowUnsafeCompose, options.AllowRemoteDocker)
	preparation, cleanup, err := s.preflight.Prepare(ctx, preflight.Options{
		ConfigPath: options.ConfigPath, BaseRef: options.BaseRef, CandidateRef: options.CandidateRef,
		EnvFile: options.EnvFile, ArtifactDir: options.ArtifactDir,
		AllowUnsafeCompose: options.AllowUnsafeCompose, AllowRemoteDocker: options.AllowRemoteDocker,
		CheckPrerequisites: true, Progress: verboseWriter(options.Verbose, s.out),
	})
	if err != nil {
		return doctorExit(err), err
	}
	summary, runErr := s.orchestrator.Run(ctx, orchestrator.Options{
		Preparation: preparation, PreflightCleanup: cleanup,
		KeepOnFailure: options.KeepOnFailure, JSONOutput: options.JSONOutput, JUnitOutput: options.JUnitOutput,
		Verbose: options.Verbose, Out: s.out, Version: s.version,
		AllowUnsafeCompose: options.AllowUnsafeCompose, AllowRemoteDocker: options.AllowRemoteDocker,
	})
	if summary.ExitCode == 0 && runErr != nil {
		return int(model.ExitInternalError), runErr
	}
	return int(summary.ExitCode), runErr
}

func (s *Service) Validate(ctx context.Context, options cli.ValidateOptions) (int, error) {
	s.printUnsafeWarnings(options.AllowUnsafeCompose, false)
	preparation, cleanup, err := s.preflight.Prepare(ctx, preflight.Options{
		ConfigPath:         options.ConfigPath,
		BaseRef:            options.BaseRef,
		CandidateRef:       options.CandidateRef,
		EnvFile:            options.EnvFile,
		AllowUnsafeCompose: options.AllowUnsafeCompose,
		Progress:           verboseWriter(options.Verbose, s.out),
	})
	if err != nil {
		return int(model.ExitConfigInvalid), err
	}
	if err := cleanup(); err != nil {
		return int(model.ExitConfigInvalid), fmt.Errorf("configuration valid but temporary worktree cleanup failed: %w", err)
	}
	fmt.Fprintf(s.out, "Configuration valid\nCandidate  %s  %s\nBase       %s  %s\n", preparation.CandidateRef, shortSHA(preparation.CandidateSHA), preparation.BaseRef, shortSHA(preparation.BaseSHA))
	return int(model.ExitPass), nil
}

func (s *Service) Doctor(ctx context.Context, options cli.DoctorOptions) (int, error) {
	s.printUnsafeWarnings(options.AllowUnsafeCompose, options.AllowRemoteDocker)
	preparation, cleanup, err := s.preflight.Prepare(ctx, preflight.Options{
		ConfigPath:         options.ConfigPath,
		BaseRef:            options.BaseRef,
		CandidateRef:       options.CandidateRef,
		EnvFile:            options.EnvFile,
		ArtifactDir:        options.ArtifactDir,
		AllowUnsafeCompose: options.AllowUnsafeCompose,
		AllowRemoteDocker:  options.AllowRemoteDocker,
		CheckPrerequisites: true,
		Progress:           verboseWriter(options.Verbose, s.out),
	})
	if err != nil {
		return doctorExit(err), err
	}
	if err := cleanup(); err != nil {
		return int(model.ExitPrerequisite), fmt.Errorf("prerequisite checks passed but temporary worktree cleanup failed: %w", err)
	}
	fmt.Fprintf(s.out, "Backline doctor\n  PASS  Git repository and revisions\n  PASS  committed configuration\n  PASS  Docker and Compose v2\n  PASS  Compose isolation policy\n  PASS  host workload executables\n  PASS  artifact directory\n\nCandidate  %s\nBase       %s\n", shortSHA(preparation.CandidateSHA), shortSHA(preparation.BaseSHA))
	return int(model.ExitPass), nil
}

func verboseWriter(enabled bool, destination io.Writer) io.Writer {
	if enabled {
		return destination
	}
	return nil
}

func doctorExit(err error) int {
	var preflightError *preflight.Error
	if errors.As(err, &preflightError) && preflightError.Kind == preflight.ConfigurationError {
		return int(model.ExitConfigInvalid)
	}
	return int(model.ExitPrerequisite)
}

func shortSHA(sha string) string {
	if len(sha) <= 7 {
		return sha
	}
	return sha[:7]
}

func (s *Service) printUnsafeWarnings(unsafeCompose, remoteDocker bool) {
	if unsafeCompose {
		fmt.Fprintln(s.out, "WARNING: --allow-unsafe-compose relaxes isolation validation for trusted project code; cleanup ownership is unchanged.")
	}
	if remoteDocker {
		fmt.Fprintln(s.out, "WARNING: --allow-remote-docker permits execution on the selected trusted remote Docker endpoint.")
	}
}
