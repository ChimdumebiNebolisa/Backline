package components

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	"github.com/ChimdumebiNebolisa/Backline/internal/dockerops"
)

type Manager struct {
	docker *dockerops.Client
}

type OperationalError struct {
	err error
}

func (e *OperationalError) Error() string { return e.err.Error() }
func (e *OperationalError) Unwrap() error { return e.err }

func IsOperational(err error) bool {
	var operational *OperationalError
	return errors.As(err, &operational)
}

func operational(err error) error {
	if err == nil {
		return nil
	}
	return &OperationalError{err: err}
}

type ComponentFailure struct {
	Role      string
	Component string
	err       error
}

func (e *ComponentFailure) Error() string { return e.err.Error() }
func (e *ComponentFailure) Unwrap() error { return e.err }

func AsComponentFailure(err error) (*ComponentFailure, bool) {
	var failure *ComponentFailure
	ok := errors.As(err, &failure)
	return failure, ok
}

func New(docker *dockerops.Client) *Manager {
	return &Manager{docker: docker}
}

type Endpoint struct {
	Role      string
	Component string
	Host      string
	Port      int
	Scheme    string
}

func (e Endpoint) URL() string {
	return e.Scheme + "://" + net.JoinHostPort(e.Host, strconv.Itoa(e.Port))
}

type Instance struct {
	Role         string
	SHA          string
	Component    config.Component
	Container    string
	ImageID      string
	Endpoint     *Endpoint
	NetworkAlias string
}

type StartInput struct {
	RunID      string
	Role       string
	SHA        string
	Components []config.Component
	Images     map[string]string
	Networks   []string
	MaxBytes   int64
}

func (m *Manager) StartAll(ctx context.Context, input StartInput) ([]Instance, error) {
	instances := make([]Instance, 0, len(input.Components))
	for _, component := range input.Components {
		image := input.Images[component.Name]
		if image == "" {
			return instances, operational(fmt.Errorf("missing %s image for component %s", input.Role, component.Name))
		}
		name := containerName(input.RunID, input.Role, component.Name)
		alias := input.Role + "-" + component.Name
		environment := RoleEnvironment(component, input.Role)
		environment["BACKLINE_RUN_ID"] = input.RunID
		environment["BACKLINE_REVISION_ROLE"] = input.Role
		environment["BACKLINE_REVISION_SHA"] = input.SHA
		environment["BACKLINE_COMPONENT"] = component.Name
		container, result, err := m.docker.Create(ctx, dockerops.CreateInput{
			Name:         name,
			Image:        image,
			Command:      component.Run.Command,
			Environment:  environment,
			Labels:       map[string]string{dockerops.LabelRunID: input.RunID, dockerops.LabelOwned: "true", dockerops.LabelRole: input.Role, dockerops.LabelComponent: component.Name, dockerops.LabelRevision: input.SHA},
			InternalPort: component.Run.InternalPort,
			MaxBytes:     input.MaxBytes,
		})
		if err != nil {
			return instances, operational(err)
		}
		if result.ExitCode != 0 {
			return instances, operational(fmt.Errorf("create %s component %s exited %d: %s", input.Role, component.Name, result.ExitCode, strings.TrimSpace(string(result.Stderr))))
		}
		instance := Instance{Role: input.Role, SHA: input.SHA, Component: component, Container: container, ImageID: image, NetworkAlias: alias}
		instances = append(instances, instance)
		for _, network := range input.Networks {
			if err := m.docker.ConnectNetwork(ctx, network, alias, container); err != nil {
				return instances, operational(err)
			}
		}
		if err := m.docker.Start(ctx, container); err != nil {
			return instances, operational(err)
		}
		if component.Run.InternalPort > 0 {
			host, port, err := m.docker.HostEndpoint(ctx, container, component.Run.InternalPort)
			if err != nil {
				return instances, operational(err)
			}
			instances[len(instances)-1].Endpoint = &Endpoint{Role: input.Role, Component: component.Name, Host: host, Port: port, Scheme: component.Run.Scheme}
		}
		if err := m.WaitReady(ctx, instances[len(instances)-1]); err != nil {
			return instances, fmt.Errorf("%s component %s readiness: %w", input.Role, component.Name, err)
		}
	}
	return instances, nil
}

func (m *Manager) WaitReady(ctx context.Context, instance Instance) error {
	deadline := time.Now().Add(time.Duration(instance.Component.Run.Readiness.TimeoutSeconds) * time.Second)
	var last error
	for time.Now().Before(deadline) {
		inspect, err := m.docker.Inspect(ctx, instance.Container)
		if err != nil {
			return operational(err)
		}
		if !inspect.State.Running {
			return fmt.Errorf("container exited with code %d", inspect.State.ExitCode)
		}
		last = m.probe(ctx, instance, inspect)
		if last == nil {
			return nil
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(500 * time.Millisecond):
		}
	}
	return fmt.Errorf("timed out: %w", last)
}

func (m *Manager) Check(ctx context.Context, instances []Instance) error {
	for _, instance := range instances {
		inspect, err := m.docker.Inspect(ctx, instance.Container)
		if err != nil {
			return operational(err)
		}
		if !inspect.State.Running {
			return &ComponentFailure{Role: instance.Role, Component: instance.Component.Name, err: fmt.Errorf("%s.%s exited with code %d", instance.Role, instance.Component.Name, inspect.State.ExitCode)}
		}
		if err := m.probe(ctx, instance, inspect); err != nil {
			if IsOperational(err) {
				return err
			}
			return &ComponentFailure{Role: instance.Role, Component: instance.Component.Name, err: fmt.Errorf("%s.%s lost readiness: %w", instance.Role, instance.Component.Name, err)}
		}
	}
	return nil
}

func (m *Manager) probe(ctx context.Context, instance Instance, inspect dockerops.Inspect) error {
	readiness := instance.Component.Run.Readiness
	switch readiness.Type {
	case "http":
		if instance.Endpoint == nil {
			return errors.New("HTTP readiness has no endpoint")
		}
		client := &http.Client{Timeout: 2 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
		requestURL := strings.TrimRight(instance.Endpoint.URL(), "/") + readiness.Path
		request, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL, nil)
		if err != nil {
			return err
		}
		response, err := client.Do(request)
		if err != nil {
			return err
		}
		_ = response.Body.Close()
		expected := readiness.ExpectedStatus
		if expected != 0 && response.StatusCode != expected {
			return fmt.Errorf("HTTP status %d, expected %d", response.StatusCode, expected)
		}
		if expected == 0 && (response.StatusCode < 200 || response.StatusCode >= 300) {
			return fmt.Errorf("HTTP status %d", response.StatusCode)
		}
		return nil
	case "tcp":
		if instance.Endpoint == nil {
			return errors.New("TCP readiness has no endpoint")
		}
		connection, err := net.DialTimeout("tcp", net.JoinHostPort(instance.Endpoint.Host, strconv.Itoa(instance.Endpoint.Port)), 2*time.Second)
		if err != nil {
			return err
		}
		return connection.Close()
	case "docker-health":
		if inspect.State.Health == nil || inspect.State.Health.Status != "healthy" {
			status := "missing"
			if inspect.State.Health != nil {
				status = inspect.State.Health.Status
			}
			return fmt.Errorf("Docker health is %s", status)
		}
		return nil
	case "command":
		result := m.docker.Exec(ctx, instance.Container, readiness.Command, 10*time.Second, 64*1024)
		if result.Err != nil {
			return operational(result.Err)
		}
		if result.ExitCode != 0 {
			return fmt.Errorf("readiness command exited %d", result.ExitCode)
		}
		return nil
	default:
		return fmt.Errorf("unsupported readiness type %q", readiness.Type)
	}
}

func (m *Manager) StopAll(ctx context.Context, instances []Instance, timeoutSeconds int) error {
	var failures []error
	for index := len(instances) - 1; index >= 0; index-- {
		instance := instances[index]
		if err := m.docker.Stop(ctx, instance.Container, timeoutSeconds); err != nil && !strings.Contains(strings.ToLower(err.Error()), "not running") {
			failures = append(failures, err)
		}
		if err := m.docker.RemoveContainer(ctx, instance.Container); err != nil && !strings.Contains(strings.ToLower(err.Error()), "no such") {
			failures = append(failures, err)
		}
	}
	return errors.Join(failures...)
}

func RoleEnvironment(component config.Component, role string) map[string]string {
	merged := make(map[string]string, len(component.Run.Environment)+len(component.Run.BaseEnvironment)+len(component.Run.CandidateEnvironment))
	for name, value := range component.Run.Environment {
		merged[name] = value
	}
	override := component.Run.CandidateEnvironment
	if role == "base" {
		override = component.Run.BaseEnvironment
	}
	for name, value := range override {
		merged[name] = value
	}
	return merged
}

func EndpointEnvironment(instances []Instance, network bool) map[string]string {
	values := make(map[string]string)
	for _, instance := range instances {
		if instance.Component.Run.InternalPort == 0 {
			continue
		}
		prefix := "BACKLINE_" + normalize(instance.Role) + "_" + normalize(instance.Component.Name)
		host := instance.NetworkAlias
		port := instance.Component.Run.InternalPort
		if !network && instance.Endpoint != nil {
			host, port = instance.Endpoint.Host, instance.Endpoint.Port
		}
		values[prefix+"_HOST"] = host
		values[prefix+"_PORT"] = strconv.Itoa(port)
		values[prefix+"_URL"] = instance.Component.Run.Scheme + "://" + net.JoinHostPort(host, strconv.Itoa(port))
	}
	return values
}

func TargetEnvironment(instance Instance, network bool) map[string]string {
	values := EndpointEnvironment([]Instance{instance}, network)
	prefix := "BACKLINE_" + normalize(instance.Role) + "_" + normalize(instance.Component.Name)
	values["BACKLINE_TARGET_ROLE"] = instance.Role
	values["BACKLINE_TARGET_COMPONENT"] = instance.Component.Name
	values["BACKLINE_TARGET_HOST"] = values[prefix+"_HOST"]
	values["BACKLINE_TARGET_PORT"] = values[prefix+"_PORT"]
	values["BACKLINE_TARGET_URL"] = values[prefix+"_URL"]
	return values
}

func Find(instances []Instance, role, component string) (Instance, bool) {
	for _, instance := range instances {
		if instance.Role == role && instance.Component.Name == component {
			return instance, true
		}
	}
	return Instance{}, false
}

func containerName(runID, role, component string) string {
	name := strings.ToLower(runID + "-" + role + "-" + component)
	name = strings.ReplaceAll(name, "_", "-")
	if len(name) > 120 {
		name = name[:120]
	}
	return name
}

func normalize(value string) string {
	return strings.ToUpper(strings.ReplaceAll(value, "-", "_"))
}

func ParseTarget(target string) (role, component string, err error) {
	parts := strings.Split(target, ".")
	if len(parts) != 2 {
		return "", "", fmt.Errorf("invalid target %q", target)
	}
	return parts[0], parts[1], nil
}

func ValidateEndpointURL(value string) error {
	parsed, err := url.Parse(value)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		return fmt.Errorf("invalid endpoint URL %q", value)
	}
	return nil
}
