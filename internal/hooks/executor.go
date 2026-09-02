package hooks

import (
	"context"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	"github.com/ChimdumebiNebolisa/Backline/internal/dockerops"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

type Executor struct {
	runner *processrun.Runner
	docker *dockerops.Client
}

func New(runner *processrun.Runner, docker *dockerops.Client) *Executor {
	return &Executor{runner: runner, docker: docker}
}

type Input struct {
	RunID       string
	Stage       string
	Hook        config.Hook
	Worktree    string
	Image       string
	Component   *config.Component
	Networks    []string
	Environment map[string]string
	Passthrough []string
	SharedDir   string
	ArtifactDir string
	MaxBytes    int64
}

type Result struct {
	Process   processrun.Result
	Container string
}

func (e *Executor) Run(ctx context.Context, input Input) (Result, error) {
	workingDirectory := input.Worktree
	if input.Hook.WorkingDirectory != "" {
		workingDirectory = filepath.Join(input.Worktree, filepath.FromSlash(input.Hook.WorkingDirectory))
	}
	environment := merge(input.Environment, input.Hook.Environment)
	if input.Hook.Runner.Type == "host" {
		result := e.runner.Run(ctx, processrun.Command{
			Name:     input.Hook.Command[0],
			Args:     input.Hook.Command[1:],
			Dir:      workingDirectory,
			Env:      processrun.MinimalEnvironment(input.Passthrough, environment, nil),
			Timeout:  time.Duration(input.Hook.TimeoutSeconds) * time.Second,
			MaxBytes: input.MaxBytes,
		})
		return Result{Process: result}, nil
	}
	if input.Component == nil || input.Image == "" {
		return Result{}, fmt.Errorf("component hook %s is missing its component image", input.Hook.Name)
	}
	name := strings.ToLower(input.RunID + "-hook-" + sanitize(input.Stage) + "-" + sanitize(input.Hook.Name))
	containerEnvironment := merge(environment, map[string]string{
		"BACKLINE_SHARED_DIR":   "/backline/shared",
		"BACKLINE_ARTIFACT_DIR": "/backline/artifacts",
	})
	container, createResult, err := e.docker.Create(ctx, dockerops.CreateInput{
		Name:        name,
		Image:       input.Image,
		Command:     input.Hook.Command,
		Environment: containerEnvironment,
		Labels: map[string]string{
			dockerops.LabelRunID:     input.RunID,
			dockerops.LabelOwned:     "true",
			dockerops.LabelRole:      input.Hook.Revision,
			dockerops.LabelComponent: input.Component.Name,
		},
		Mounts: []dockerops.Mount{
			{Source: input.SharedDir, Target: "/backline/shared"},
			{Source: input.ArtifactDir, Target: "/backline/artifacts", ReadOnly: true},
		},
		WorkingDir: input.Hook.WorkingDirectory,
		MaxBytes:   input.MaxBytes,
	})
	if err != nil || createResult.ExitCode != 0 {
		if err == nil {
			err = fmt.Errorf("create component hook container exited %d: %s", createResult.ExitCode, strings.TrimSpace(string(createResult.Stderr)))
		}
		return Result{Process: createResult}, err
	}
	for _, network := range input.Networks {
		if err := e.docker.ConnectNetwork(ctx, network, "", container); err != nil {
			return Result{Process: createResult, Container: container}, err
		}
	}
	result := e.docker.StartAttached(ctx, container, time.Duration(input.Hook.TimeoutSeconds)*time.Second, input.MaxBytes)
	return Result{Process: result, Container: container}, nil
}

func merge(first, second map[string]string) map[string]string {
	result := make(map[string]string, len(first)+len(second))
	for name, value := range first {
		result[name] = value
	}
	for name, value := range second {
		result[name] = value
	}
	return result
}

func sanitize(value string) string {
	value = strings.ToLower(value)
	value = strings.Map(func(character rune) rune {
		if character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '-' {
			return character
		}
		return '-'
	}, value)
	return strings.Trim(value, "-")
}
