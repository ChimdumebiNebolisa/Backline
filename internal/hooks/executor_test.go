package hooks

import (
	"context"
	"errors"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	"github.com/ChimdumebiNebolisa/Backline/internal/dockerops"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

func TestComponentHookUsesOnlyRunOwnedSharedAndArtifactMounts(t *testing.T) {
	runner := &recordingRunner{}
	executor := New(processrun.NewRunner(), dockerops.New(runner))
	root := t.TempDir()
	shared := filepath.Join(root, "shared")
	artifacts := filepath.Join(root, "artifacts")
	worktree := filepath.Join(root, "candidate")
	component := config.Component{Name: "api"}
	result, err := executor.Run(context.Background(), Input{
		RunID: "bl-test", Stage: "transition", Worktree: worktree, Image: "candidate-api@sha256:test", Component: &component,
		Networks: []string{"network-one", "network-two"}, SharedDir: shared, ArtifactDir: artifacts, MaxBytes: 4096,
		Hook: config.Hook{Name: "migrate", Revision: "candidate", Runner: config.HookRunner{Type: "component", Component: "api"}, Command: []string{"/app/migrate"}, WorkingDirectory: "/app", TimeoutSeconds: 30},
	})
	if err != nil || result.Container != "hook-container" || result.Process.ExitCode != 0 {
		t.Fatalf("result=%+v err=%v", result, err)
	}
	create := strings.Join(runner.commands[0].Args, " ")
	for _, expected := range []string{
		"--name bl-test-hook-transition-migrate",
		"type=bind,src=" + shared + ",dst=/backline/shared",
		"type=bind,src=" + artifacts + ",dst=/backline/artifacts,readonly",
		"--workdir /app",
	} {
		if !strings.Contains(create, expected) {
			t.Fatalf("create args %q missing %q", create, expected)
		}
	}
	if strings.Contains(create, worktree) {
		t.Fatalf("component hook exposed its host worktree: %q", create)
	}
}

func TestComponentHookContainerCreationFailureIsOperational(t *testing.T) {
	runner := &recordingRunner{createExitCode: 125}
	executor := New(processrun.NewRunner(), dockerops.New(runner))
	component := config.Component{Name: "api"}
	result, err := executor.Run(context.Background(), Input{
		RunID: "bl-test", Stage: "transition", Worktree: t.TempDir(), Image: "candidate-api:test", Component: &component,
		SharedDir: t.TempDir(), ArtifactDir: t.TempDir(), MaxBytes: 4096,
		Hook: config.Hook{Name: "migrate", Revision: "candidate", Runner: config.HookRunner{Type: "component", Component: "api"}, Command: []string{"/app/migrate"}, TimeoutSeconds: 30},
	})
	if err == nil || result.Process.ExitCode != 125 || !strings.Contains(err.Error(), "create component hook container") {
		t.Fatalf("result=%+v err=%v", result, err)
	}
}

type recordingRunner struct {
	commands       []processrun.Command
	createExitCode int
}

func (r *recordingRunner) Run(_ context.Context, command processrun.Command) processrun.Result {
	r.commands = append(r.commands, command)
	result := processrun.Result{Launched: true, ExitCode: 0}
	if len(command.Args) > 0 && command.Args[0] == "create" {
		if r.createExitCode != 0 {
			result.ExitCode = r.createExitCode
			result.Stderr = []byte("Docker create failed")
			return result
		}
		result.Stdout = []byte("hook-container\n")
	}
	return result
}

func (*recordingRunner) Output(context.Context, string, string, ...string) ([]byte, error) {
	return nil, errors.New("unexpected output command")
}
