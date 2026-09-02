package dockerops

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"sort"
	"strconv"
	"strings"
	"time"

	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

const (
	LabelRunID     = "dev.backline.run_id"
	LabelOwned     = "dev.backline.owned"
	LabelRole      = "dev.backline.role"
	LabelComponent = "dev.backline.component"
	LabelRevision  = "dev.backline.revision_sha"
)

type CommandRunner interface {
	Run(context.Context, processrun.Command) processrun.Result
	Output(context.Context, string, string, ...string) ([]byte, error)
}

type Client struct {
	runner CommandRunner
}

func New(runner CommandRunner) *Client {
	if runner == nil {
		runner = processrun.NewRunner()
	}
	return &Client{runner: runner}
}

type BuildInput struct {
	Context    string
	Dockerfile string
	Tag        string
	Labels     map[string]string
	Timeout    time.Duration
	MaxBytes   int64
}

func (c *Client) Build(ctx context.Context, input BuildInput) (string, processrun.Result, error) {
	args := []string{"build", "--file", input.Dockerfile, "--tag", input.Tag}
	for _, pair := range sortedPairs(input.Labels) {
		args = append(args, "--label", pair)
	}
	args = append(args, input.Context)
	result := c.runner.Run(ctx, processrun.Command{Name: "docker", Args: args, Timeout: input.Timeout, MaxBytes: input.MaxBytes})
	if result.Err != nil {
		return "", result, result.Err
	}
	if result.ExitCode != 0 {
		return "", result, nil
	}
	imageID, err := c.ImageID(ctx, input.Tag)
	return imageID, result, err
}

func (c *Client) ImageID(ctx context.Context, reference string) (string, error) {
	output, err := c.runner.Output(ctx, "", "docker", "image", "inspect", reference, "--format", "{{.Id}}")
	if err != nil {
		return "", err
	}
	id := strings.TrimSpace(string(output))
	if id == "" {
		return "", fmt.Errorf("Docker image inspect returned an empty ID for %s", reference)
	}
	return id, nil
}

type Mount struct {
	Source   string
	Target   string
	ReadOnly bool
}

type CreateInput struct {
	Name         string
	Image        string
	Command      []string
	Environment  map[string]string
	Labels       map[string]string
	InternalPort int
	Mounts       []Mount
	WorkingDir   string
	MaxBytes     int64
}

func (c *Client) Create(ctx context.Context, input CreateInput) (string, processrun.Result, error) {
	args := []string{"create", "--name", input.Name}
	for _, pair := range sortedPairs(input.Labels) {
		args = append(args, "--label", pair)
	}
	for _, pair := range sortedPairs(input.Environment) {
		args = append(args, "--env", pair)
	}
	if input.InternalPort > 0 {
		args = append(args, "--publish", fmt.Sprintf("127.0.0.1::%d", input.InternalPort))
	}
	for _, mount := range input.Mounts {
		value := fmt.Sprintf("type=bind,src=%s,dst=%s", mount.Source, mount.Target)
		if mount.ReadOnly {
			value += ",readonly"
		}
		args = append(args, "--mount", value)
	}
	if input.WorkingDir != "" {
		args = append(args, "--workdir", input.WorkingDir)
	}
	if len(input.Command) > 0 {
		args = append(args, "--entrypoint", input.Command[0])
	}
	args = append(args, input.Image)
	if len(input.Command) > 1 {
		args = append(args, input.Command[1:]...)
	}
	result := c.runner.Run(ctx, processrun.Command{Name: "docker", Args: args, Timeout: time.Minute, MaxBytes: input.MaxBytes})
	if result.Err != nil {
		return "", result, result.Err
	}
	if result.ExitCode != 0 {
		return "", result, nil
	}
	container := strings.TrimSpace(string(result.Stdout))
	if container == "" || strings.ContainsAny(container, "\r\n") {
		return "", result, errors.New("Docker create did not return exactly one container ID")
	}
	return container, result, nil
}

func (c *Client) ConnectNetwork(ctx context.Context, network, alias, container string) error {
	args := []string{"network", "connect"}
	if alias != "" {
		args = append(args, "--alias", alias)
	}
	args = append(args, network, container)
	result := c.runner.Run(ctx, processrun.Command{Name: "docker", Args: args, Timeout: time.Minute})
	return commandError("connect container network", result)
}

func (c *Client) Start(ctx context.Context, container string) error {
	result := c.runner.Run(ctx, processrun.Command{Name: "docker", Args: []string{"start", container}, Timeout: time.Minute})
	return commandError("start container", result)
}

func (c *Client) StartAttached(ctx context.Context, container string, timeout time.Duration, maxBytes int64) processrun.Result {
	return c.runner.Run(ctx, processrun.Command{Name: "docker", Args: []string{"start", "--attach", container}, Timeout: timeout, MaxBytes: maxBytes})
}

func (c *Client) Exec(ctx context.Context, container string, command []string, timeout time.Duration, maxBytes int64) processrun.Result {
	args := append([]string{"exec", container}, command...)
	return c.runner.Run(ctx, processrun.Command{Name: "docker", Args: args, Timeout: timeout, MaxBytes: maxBytes})
}

func (c *Client) Stop(ctx context.Context, container string, timeoutSeconds int) error {
	result := c.runner.Run(ctx, processrun.Command{Name: "docker", Args: []string{"stop", "--time", strconv.Itoa(timeoutSeconds), container}, Timeout: time.Duration(timeoutSeconds+30) * time.Second})
	return commandError("stop container", result)
}

func (c *Client) RemoveContainer(ctx context.Context, container string) error {
	result := c.runner.Run(ctx, processrun.Command{Name: "docker", Args: []string{"rm", "--force", "--volumes", container}, Timeout: time.Minute})
	return commandError("remove container", result)
}

func (c *Client) RemoveImage(ctx context.Context, image string) error {
	result := c.runner.Run(ctx, processrun.Command{Name: "docker", Args: []string{"image", "rm", "--force", image}, Timeout: 2 * time.Minute})
	return commandError("remove image", result)
}

func (c *Client) Logs(ctx context.Context, container string, maxBytes int64) processrun.Result {
	return c.runner.Run(ctx, processrun.Command{Name: "docker", Args: []string{"logs", container}, Timeout: time.Minute, MaxBytes: maxBytes})
}

type Inspect struct {
	ID     string `json:"Id"`
	Name   string `json:"Name"`
	Image  string `json:"Image"`
	Config struct {
		Labels map[string]string `json:"Labels"`
	} `json:"Config"`
	State struct {
		Status   string `json:"Status"`
		Running  bool   `json:"Running"`
		ExitCode int    `json:"ExitCode"`
		Health   *struct {
			Status string `json:"Status"`
		} `json:"Health"`
	} `json:"State"`
	NetworkSettings struct {
		Ports map[string][]struct {
			HostIP   string `json:"HostIp"`
			HostPort string `json:"HostPort"`
		} `json:"Ports"`
		Networks map[string]json.RawMessage `json:"Networks"`
	} `json:"NetworkSettings"`
}

func (c *Client) Inspect(ctx context.Context, container string) (Inspect, error) {
	output, err := c.runner.Output(ctx, "", "docker", "inspect", container)
	if err != nil {
		return Inspect{}, err
	}
	var values []Inspect
	if err := json.Unmarshal(output, &values); err != nil {
		return Inspect{}, fmt.Errorf("decode Docker inspect for %s: %w", container, err)
	}
	if len(values) != 1 {
		return Inspect{}, fmt.Errorf("Docker inspect for %s returned %d records, expected one", container, len(values))
	}
	return values[0], nil
}

func (c *Client) HostEndpoint(ctx context.Context, container string, internalPort int) (string, int, error) {
	inspect, err := c.Inspect(ctx, container)
	if err != nil {
		return "", 0, err
	}
	bindings := inspect.NetworkSettings.Ports[fmt.Sprintf("%d/tcp", internalPort)]
	if len(bindings) != 1 || bindings[0].HostIP != "127.0.0.1" {
		return "", 0, fmt.Errorf("container %s does not have exactly one loopback mapping for %d/tcp", container, internalPort)
	}
	port, err := strconv.Atoi(bindings[0].HostPort)
	if err != nil || port < 1 || port > 65535 {
		return "", 0, fmt.Errorf("container %s has invalid host port %q", container, bindings[0].HostPort)
	}
	return "127.0.0.1", port, nil
}

func (c *Client) Exists(ctx context.Context, kind, id string) (bool, error) {
	args := []string{kind, "inspect", id}
	if kind == "container" {
		args = []string{"container", "inspect", id}
	}
	result := c.runner.Run(ctx, processrun.Command{Name: "docker", Args: args, Timeout: 30 * time.Second})
	if result.Err != nil {
		return false, result.Err
	}
	if result.ExitCode == 0 {
		return true, nil
	}
	combined := strings.ToLower(string(result.Stderr) + string(result.Stdout))
	if strings.Contains(combined, "no such") || strings.Contains(combined, "not found") {
		return false, nil
	}
	return false, fmt.Errorf("inspect Docker %s %s exited %d: %s", kind, id, result.ExitCode, strings.TrimSpace(combined))
}

func (c *Client) ListIDs(ctx context.Context, kind, label string) ([]string, error) {
	var args []string
	switch kind {
	case "container":
		args = []string{"container", "ls", "--all", "--quiet", "--filter", "label=" + label}
	case "image":
		args = []string{"image", "ls", "--quiet", "--filter", "label=" + label}
	case "network", "volume":
		args = []string{kind, "ls", "--quiet", "--filter", "label=" + label}
	default:
		return nil, fmt.Errorf("unsupported Docker resource kind %q", kind)
	}
	output, err := c.runner.Output(ctx, "", "docker", args...)
	if err != nil {
		return nil, err
	}
	return strings.Fields(string(output)), nil
}

func sortedPairs(values map[string]string) []string {
	names := make([]string, 0, len(values))
	for name := range values {
		names = append(names, name)
	}
	sort.Strings(names)
	result := make([]string, 0, len(names))
	for _, name := range names {
		result = append(result, name+"="+values[name])
	}
	return result
}

func commandError(action string, result processrun.Result) error {
	if result.Err != nil {
		return fmt.Errorf("%s: %w", action, result.Err)
	}
	if result.ExitCode != 0 {
		message := strings.TrimSpace(string(result.Stderr))
		if message == "" {
			message = strings.TrimSpace(string(result.Stdout))
		}
		return fmt.Errorf("%s exited %d: %s", action, result.ExitCode, message)
	}
	return nil
}

func ParseAddress(value string) (string, int, error) {
	host, portValue, err := net.SplitHostPort(value)
	if err != nil {
		return "", 0, err
	}
	port, err := strconv.Atoi(portValue)
	return host, port, err
}
