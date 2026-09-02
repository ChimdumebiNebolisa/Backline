package compose

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/dockerops"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

type CommandRunner interface {
	Run(context.Context, processrun.Command) processrun.Result
	Output(context.Context, string, string, ...string) ([]byte, error)
}

type Manager struct {
	runner CommandRunner
	docker *dockerops.Client
}

func New(runner CommandRunner, docker *dockerops.Client) *Manager {
	if runner == nil {
		runner = processrun.NewRunner()
	}
	if docker == nil {
		docker = dockerops.New(runner)
	}
	return &Manager{runner: runner, docker: docker}
}

type StartInput struct {
	RunID          string
	Project        string
	Worktree       string
	ComposePath    string
	ComposeJSON    []byte
	EnvFile        string
	Services       []string
	TemporaryRoot  string
	StartupTimeout time.Duration
	MaxBytes       int64
}

type Environment struct {
	Project         string
	OverridePath    string
	ContainerIDs    map[string]string
	Networks        []string
	Volumes         []string
	ServiceImageIDs map[string]string
	OwnedImageIDs   []string
}

func (m *Manager) Start(ctx context.Context, input StartInput) (Environment, processrun.Result, error) {
	environment := Environment{Project: input.Project}
	overrideContents, err := BuildOverride(input.ComposeJSON, input.Services, input.RunID)
	if err != nil {
		return environment, processrun.Result{}, err
	}
	overridePath := filepath.Join(input.TemporaryRoot, "compose.override.json")
	if err := os.WriteFile(overridePath, overrideContents, 0o600); err != nil {
		return environment, processrun.Result{}, fmt.Errorf("write Compose override: %w", err)
	}
	environment.OverridePath = overridePath
	args := m.baseArgs(input.Project, input.ComposePath, overridePath, input.EnvFile)
	args = append(args, "up", "--detach", "--build")
	args = append(args, input.Services...)
	result := m.runner.Run(ctx, processrun.Command{Name: "docker", Args: args, Dir: input.Worktree, Timeout: input.StartupTimeout, MaxBytes: input.MaxBytes})
	if result.Err != nil || result.ExitCode != 0 {
		return environment, result, result.Err
	}
	containers := make(map[string]string, len(input.Services))
	for _, service := range input.Services {
		output, err := m.runner.Output(ctx, input.Worktree, "docker", append(m.baseArgs(input.Project, input.ComposePath, overridePath, input.EnvFile), "ps", "--quiet", service)...)
		if err != nil {
			return environment, result, err
		}
		containerID := strings.TrimSpace(string(output))
		if containerID == "" || strings.Contains(containerID, "\n") {
			return environment, result, fmt.Errorf("Compose service %s did not produce exactly one container", service)
		}
		containers[service] = containerID
	}
	environment.ContainerIDs = containers
	if err := m.waitHealthy(ctx, containers, input.StartupTimeout); err != nil {
		return environment, result, err
	}
	networks, err := m.docker.ListIDs(ctx, "network", "com.docker.compose.project="+input.Project)
	if err != nil {
		return environment, result, err
	}
	environment.Networks = networks
	volumes, err := m.docker.ListIDs(ctx, "volume", "com.docker.compose.project="+input.Project)
	if err != nil {
		return environment, result, err
	}
	environment.Volumes = volumes
	serviceImages := make(map[string]string, len(containers))
	attachedNetworks := append([]string(nil), networks...)
	for service, containerID := range containers {
		inspect, err := m.docker.Inspect(ctx, containerID)
		if err != nil {
			return environment, result, err
		}
		serviceImages[service] = inspect.Image
		for network := range inspect.NetworkSettings.Networks {
			attachedNetworks = append(attachedNetworks, network)
		}
	}
	environment.Networks = unique(attachedNetworks)
	environment.ServiceImageIDs = serviceImages
	ownedImages, err := m.docker.ListIDs(ctx, "image", dockerops.LabelRunID+"="+input.RunID)
	if err != nil {
		return environment, result, err
	}
	environment.OwnedImageIDs = unique(ownedImages)
	return environment, result, nil
}

func (m *Manager) Stop(ctx context.Context, input StartInput, overridePath string, shutdownTimeout time.Duration) error {
	args := m.baseArgs(input.Project, input.ComposePath, overridePath, input.EnvFile)
	args = append(args, "down", "--volumes", "--remove-orphans", "--timeout", fmt.Sprintf("%.0f", shutdownTimeout.Seconds()))
	result := m.runner.Run(ctx, processrun.Command{Name: "docker", Args: args, Dir: input.Worktree, Timeout: shutdownTimeout + time.Minute, MaxBytes: input.MaxBytes})
	if result.Err != nil {
		return result.Err
	}
	if result.ExitCode != 0 {
		return fmt.Errorf("docker compose down exited %d: %s", result.ExitCode, strings.TrimSpace(string(result.Stderr)))
	}
	return nil
}

func (m *Manager) baseArgs(project, composePath, overridePath, envFile string) []string {
	args := []string{"compose", "--project-name", project}
	if envFile != "" {
		args = append(args, "--env-file", envFile)
	}
	args = append(args, "--file", composePath, "--file", overridePath)
	return args
}

func (m *Manager) waitHealthy(ctx context.Context, containers map[string]string, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	last := make(map[string]string)
	for time.Now().Before(deadline) {
		allHealthy := true
		for service, container := range containers {
			inspect, err := m.docker.Inspect(ctx, container)
			if err != nil {
				return err
			}
			if !inspect.State.Running {
				return fmt.Errorf("shared service %s exited with code %d", service, inspect.State.ExitCode)
			}
			status := "missing"
			if inspect.State.Health != nil {
				status = inspect.State.Health.Status
			}
			last[service] = status
			allHealthy = allHealthy && status == "healthy"
		}
		if allHealthy {
			return nil
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(500 * time.Millisecond):
		}
	}
	return fmt.Errorf("shared services did not become healthy before timeout: %v", last)
}

func BuildOverride(normalized []byte, services []string, runID string) ([]byte, error) {
	var document struct {
		Services map[string]struct {
			Build any `json:"build"`
		} `json:"services"`
		Networks map[string]any `json:"networks"`
		Volumes  map[string]any `json:"volumes"`
	}
	if err := json.Unmarshal(normalized, &document); err != nil {
		return nil, fmt.Errorf("decode normalized Compose configuration for override: %w", err)
	}
	labels := map[string]string{dockerops.LabelRunID: runID, dockerops.LabelOwned: "true"}
	overrideServices := make(map[string]any, len(services))
	for _, service := range services {
		definition, exists := document.Services[service]
		if !exists {
			return nil, fmt.Errorf("selected service %q missing while building Compose override", service)
		}
		override := map[string]any{"labels": labels}
		if definition.Build != nil {
			override["build"] = map[string]any{"labels": labels}
		}
		overrideServices[service] = override
	}
	overrideNetworks := make(map[string]any, len(document.Networks)+1)
	for name := range document.Networks {
		if !externalComposeResource(document.Networks[name]) {
			overrideNetworks[name] = map[string]any{"labels": labels}
		}
	}
	if _, exists := overrideNetworks["default"]; !exists {
		overrideNetworks["default"] = map[string]any{"labels": labels}
	}
	overrideVolumes := make(map[string]any, len(document.Volumes))
	for name := range document.Volumes {
		if !externalComposeResource(document.Volumes[name]) {
			overrideVolumes[name] = map[string]any{"labels": labels}
		}
	}
	override := map[string]any{"services": overrideServices, "networks": overrideNetworks}
	if len(overrideVolumes) > 0 {
		override["volumes"] = overrideVolumes
	}
	encoded, err := json.MarshalIndent(override, "", "  ")
	if err != nil {
		return nil, err
	}
	return append(encoded, '\n'), nil
}

func unique(values []string) []string {
	seen := make(map[string]struct{}, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		if value == "" {
			continue
		}
		if _, exists := seen[value]; exists {
			continue
		}
		seen[value] = struct{}{}
		result = append(result, value)
	}
	sort.Strings(result)
	return result
}

func externalComposeResource(value any) bool {
	resource, ok := value.(map[string]any)
	if !ok {
		return false
	}
	switch external := resource["external"].(type) {
	case bool:
		return external
	case map[string]any:
		return len(external) > 0
	default:
		return external != nil
	}
}
