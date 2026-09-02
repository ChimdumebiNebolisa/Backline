package preflight

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	"github.com/ChimdumebiNebolisa/Backline/internal/gitops"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
	"github.com/ChimdumebiNebolisa/Backline/internal/redact"
	"gopkg.in/yaml.v3"
)

var environmentReference = regexp.MustCompile(`\$\{([A-Za-z_][A-Za-z0-9_]*)\}`)

type ErrorKind int

const (
	ConfigurationError ErrorKind = iota + 1
	PrerequisiteError
	OperationalError
)

type Error struct {
	Kind ErrorKind
	Err  error
}

func (e *Error) Error() string { return e.Err.Error() }
func (e *Error) Unwrap() error { return e.Err }

type Options struct {
	StartDirectory     string
	ConfigPath         string
	BaseRef            string
	CandidateRef       string
	EnvFile            string
	ArtifactDir        string
	AllowUnsafeCompose bool
	AllowRemoteDocker  bool
	CheckPrerequisites bool
	Progress           io.Writer
}

type Preparation struct {
	Repository        *gitops.Repository
	Config            config.Config
	RawConfig         []byte
	ResolvedConfig    []byte
	RegisteredSecrets []string
	ConfigPath        string
	ConfigSHA256      string
	BaseRef           string
	BaseSHA           string
	CandidateRef      string
	CandidateSHA      string
	BaseWorktree      string
	CandidateWorktree string
	ComposePath       string
	ComposeJSON       []byte
	EnvFilePath       string
	EnvFileDisplay    string
	EnvFileExternal   bool
	EnvFileSHA256     string
	ArtifactRoot      string
	TemporaryRoot     string
	DockerContext     string
	DockerVersion     string
	ComposeVersion    string
}

type RuntimeInfo struct {
	DockerContext  string
	DockerVersion  string
	ComposeVersion string
}

type Service struct {
	runner gitops.CommandRunner
}

func New(runner gitops.CommandRunner) *Service {
	if runner == nil {
		runner = processrun.NewRunner()
	}
	return &Service{runner: runner}
}

func (s *Service) Prepare(ctx context.Context, options Options) (_ *Preparation, cleanup func() error, returnedErr error) {
	registeredSecrets := make([]string, 0)
	defer func() {
		if returnedErr != nil {
			returnedErr = redactPreflightError(returnedErr, registeredSecrets)
		}
	}()
	if options.StartDirectory == "" {
		workingDirectory, err := os.Getwd()
		if err != nil {
			return nil, nil, prerequisite(fmt.Errorf("determine working directory: %w", err))
		}
		options.StartDirectory = workingDirectory
	}
	if options.ConfigPath == "" {
		options.ConfigPath = "backline.yml"
	}
	if options.CandidateRef == "" {
		options.CandidateRef = "HEAD"
	}
	if _, err := exec.LookPath("git"); err != nil {
		return nil, nil, prerequisite(errors.New("Git is not installed or not on PATH"))
	}
	repository, err := gitops.Discover(ctx, options.StartDirectory, s.runner)
	if err != nil {
		return nil, nil, prerequisite(err)
	}
	progress(options.Progress, "Git repository: %s", repository.Root)
	candidateSHA, err := repository.Resolve(ctx, options.CandidateRef)
	if err != nil {
		return nil, nil, configuration(err)
	}
	progress(options.Progress, "Candidate revision: %s", candidateSHA)
	rawConfig, resolvedConfigPath, err := repository.ReadCommittedFile(ctx, candidateSHA, options.ConfigPath)
	if err != nil {
		return nil, nil, configuration(err)
	}
	progress(options.Progress, "Committed configuration: %s", resolvedConfigPath)

	processEnvironment := environmentMap(os.Environ())
	envPath, err := resolveEnvironmentFile(repository.Root, rawConfig, options.EnvFile, processEnvironment)
	if err != nil {
		return nil, nil, configuration(err)
	}
	fileEnvironment := map[string]string{}
	envDisplay := ""
	envExternal := false
	envDigest := ""
	if envPath != "" {
		contents, readErr := os.ReadFile(envPath)
		if readErr != nil {
			return nil, nil, configuration(fmt.Errorf("read environment file: %w", readErr))
		}
		fileEnvironment, registeredSecrets, err = config.ParseEnvironmentFile(contents)
		if err != nil {
			return nil, nil, configuration(err)
		}
		envDigest = digest(contents)
		relative, relErr := filepath.Rel(repository.Root, envPath)
		envExternal = relErr != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator))
		if envExternal {
			envDisplay = filepath.Base(envPath)
		} else {
			envDisplay = filepath.ToSlash(relative)
		}
	}
	mergedEnvironment := config.MergeEnvironment(fileEnvironment, processEnvironment)
	baseOverride := options.BaseRef
	if baseOverride == "" && strings.TrimSpace(os.Getenv("GITHUB_BASE_REF")) != "" {
		baseOverride = "origin/" + strings.TrimSpace(os.Getenv("GITHUB_BASE_REF"))
	}
	parsed, err := config.ParseWithOptions(rawConfig, mergedEnvironment, config.ParseOptions{BaseRef: baseOverride})
	if err != nil {
		return nil, nil, configuration(err)
	}
	registeredSecrets = append(registeredSecrets, parsed.RegisteredValues...)
	for _, name := range parsed.Config.Security.RedactEnvironment {
		if value := mergedEnvironment[name]; value != "" {
			registeredSecrets = append(registeredSecrets, value)
		}
	}
	registeredSecrets = unique(registeredSecrets)
	baseRef := parsed.Config.Revisions.Base
	baseSHA, err := repository.Resolve(ctx, baseRef)
	if err != nil {
		return nil, nil, configuration(err)
	}
	if baseSHA == candidateSHA {
		return nil, nil, configuration(errors.New("base and candidate refs resolve to the same commit"))
	}
	progress(options.Progress, "Base revision: %s", baseSHA)
	if parsed.Config.Revisions.CleanCheckoutRequired() {
		headSHA, headErr := repository.Resolve(ctx, "HEAD")
		if headErr == nil && headSHA == candidateSHA {
			dirty, dirtyErr := repository.TrackedDirty(ctx)
			if dirtyErr != nil {
				return nil, nil, operational(fmt.Errorf("check tracked checkout state: %w", dirtyErr))
			}
			if dirty {
				return nil, nil, configuration(errors.New("tracked checkout changes are not included in the committed candidate; commit or stash them, or explicitly disable revisions.require_clean_checkout"))
			}
		}
	}

	temporaryRoot, err := os.MkdirTemp("", "backline-preflight-")
	if err != nil {
		return nil, nil, operational(fmt.Errorf("create preflight directory: %w", err))
	}
	_ = os.Chmod(temporaryRoot, 0o700)
	baseWorktree := filepath.Join(temporaryRoot, "base")
	candidateWorktree := filepath.Join(temporaryRoot, "candidate")
	baseCreated := false
	candidateCreated := false
	cleanup = func() error {
		var cleanupErrors []error
		cleanupContext, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
		defer cancel()
		if candidateCreated {
			cleanupErrors = appendIf(cleanupErrors, repository.RemoveWorktree(cleanupContext, candidateWorktree))
		}
		if baseCreated {
			cleanupErrors = appendIf(cleanupErrors, repository.RemoveWorktree(cleanupContext, baseWorktree))
		}
		cleanupErrors = appendIf(cleanupErrors, os.RemoveAll(temporaryRoot))
		return errors.Join(cleanupErrors...)
	}
	defer func() {
		if recovered := recover(); recovered != nil {
			if cleanup != nil {
				_ = cleanup()
			}
			panic(recovered)
		}
		if returnedErr != nil {
			_ = cleanup()
		}
	}()
	if err := repository.CreateWorktree(ctx, baseSHA, baseWorktree); err != nil {
		return nil, cleanup, operational(err)
	}
	baseCreated = true
	progress(options.Progress, "Created detached base worktree: %s", baseWorktree)
	if err := repository.CreateWorktree(ctx, candidateSHA, candidateWorktree); err != nil {
		return nil, cleanup, operational(err)
	}
	candidateCreated = true
	progress(options.Progress, "Created detached candidate worktree: %s", candidateWorktree)
	if err := validateReferencedPaths(parsed.Config, baseWorktree, candidateWorktree); err != nil {
		return nil, cleanup, configuration(err)
	}
	composePath, err := config.ResolveWithin(candidateWorktree, parsed.Config.SharedEnvironment.ComposeFile)
	if err != nil {
		return nil, cleanup, configuration(fmt.Errorf("shared_environment.compose_file: %w", err))
	}
	if _, err := exec.LookPath("docker"); err != nil {
		return nil, cleanup, prerequisite(errors.New("Docker is not installed or not on PATH"))
	}
	composeJSON, err := s.normalizeCompose(ctx, candidateWorktree, composePath, envPath)
	if err != nil {
		return nil, cleanup, configuration(err)
	}
	if err := config.ValidateComposeJSON(composeJSON, candidateWorktree, parsed.Config.SharedEnvironment.Services, options.AllowUnsafeCompose); err != nil {
		return nil, cleanup, configuration(err)
	}
	progress(options.Progress, "Compose configuration normalized and isolation policy validated")
	var runtimeInfo RuntimeInfo
	if options.CheckPrerequisites {
		runtimeInfo, err = s.checkDocker(ctx, options.AllowRemoteDocker)
		if err != nil {
			return nil, cleanup, prerequisite(err)
		}
		if err := checkHostExecutables(parsed.Config); err != nil {
			return nil, cleanup, prerequisite(err)
		}
		progress(options.Progress, "Docker, Compose, and host executable prerequisites passed")
	}
	artifactRoot, err := resolveArtifactRoot(repository.Root, parsed.Config.Artifacts.Directory, options.ArtifactDir)
	if err != nil {
		return nil, cleanup, configuration(err)
	}
	if options.CheckPrerequisites {
		if err := checkWritableDirectory(artifactRoot); err != nil {
			return nil, cleanup, prerequisite(err)
		}
	}
	return &Preparation{
		Repository:        repository,
		Config:            parsed.Config,
		RawConfig:         rawConfig,
		ResolvedConfig:    parsed.ResolvedYAML,
		RegisteredSecrets: registeredSecrets,
		ConfigPath:        resolvedConfigPath,
		ConfigSHA256:      digest(rawConfig),
		BaseRef:           baseRef,
		BaseSHA:           baseSHA,
		CandidateRef:      options.CandidateRef,
		CandidateSHA:      candidateSHA,
		BaseWorktree:      baseWorktree,
		CandidateWorktree: candidateWorktree,
		ComposePath:       composePath,
		ComposeJSON:       composeJSON,
		EnvFilePath:       envPath,
		EnvFileDisplay:    envDisplay,
		EnvFileExternal:   envExternal,
		EnvFileSHA256:     envDigest,
		ArtifactRoot:      artifactRoot,
		TemporaryRoot:     temporaryRoot,
		DockerContext:     runtimeInfo.DockerContext,
		DockerVersion:     runtimeInfo.DockerVersion,
		ComposeVersion:    runtimeInfo.ComposeVersion,
	}, cleanup, nil
}

func progress(destination io.Writer, format string, values ...any) {
	if destination != nil {
		fmt.Fprintf(destination, format+"\n", values...)
	}
}

func (s *Service) normalizeCompose(ctx context.Context, worktree, composePath, envPath string) ([]byte, error) {
	args := []string{"compose"}
	if envPath != "" {
		args = append(args, "--env-file", envPath)
	}
	args = append(args, "-f", composePath, "config", "--format", "json")
	result := s.runner.Run(ctx, processrun.Command{Name: "docker", Args: args, Dir: worktree, Timeout: 2 * time.Minute})
	if result.Err != nil {
		return nil, fmt.Errorf("normalize Compose configuration: %w", result.Err)
	}
	if result.ExitCode != 0 {
		return nil, fmt.Errorf("normalize Compose configuration: %s", strings.TrimSpace(string(result.Stderr)))
	}
	return result.Stdout, nil
}

func (s *Service) checkDocker(ctx context.Context, allowRemote bool) (RuntimeInfo, error) {
	contextName, err := s.runner.Output(ctx, "", "docker", "context", "show")
	if err != nil {
		return RuntimeInfo{}, fmt.Errorf("inspect Docker context name: %w", err)
	}
	host := strings.TrimSpace(os.Getenv("DOCKER_HOST"))
	if host == "" {
		output, err := s.runner.Output(ctx, "", "docker", "context", "inspect", "--format", "{{json .Endpoints.docker.Host}}")
		if err != nil {
			return RuntimeInfo{}, fmt.Errorf("inspect Docker context: %w", err)
		}
		host = strings.TrimSpace(string(output))
		if unquoted, err := strconv.Unquote(host); err == nil {
			host = unquoted
		}
	}
	if !allowRemote && !localDockerHost(host) {
		return RuntimeInfo{}, fmt.Errorf("Docker endpoint %q is not local; pass --allow-remote-docker only for a trusted endpoint", host)
	}
	composeVersion, err := s.runner.Output(ctx, "", "docker", "compose", "version", "--short")
	if err != nil {
		return RuntimeInfo{}, fmt.Errorf("Compose v2 prerequisite failed: %w", err)
	}
	dockerVersion, err := s.runner.Output(ctx, "", "docker", "version", "--format", "{{.Server.Version}}")
	if err != nil {
		return RuntimeInfo{}, fmt.Errorf("Docker prerequisite failed: %w", err)
	}
	return RuntimeInfo{DockerContext: strings.TrimSpace(string(contextName)), DockerVersion: strings.TrimSpace(string(dockerVersion)), ComposeVersion: strings.TrimSpace(string(composeVersion))}, nil
}

func validateReferencedPaths(configuration config.Config, baseWorktree, candidateWorktree string) error {
	for _, component := range configuration.Release.Components {
		for role, root := range map[string]string{"base": baseWorktree, "candidate": candidateWorktree} {
			if _, err := config.ResolveWithin(root, component.Build.Context); err != nil {
				return fmt.Errorf("%s component %s build context: %w", role, component.Name, err)
			}
			if _, err := config.ResolveWithin(root, component.Build.Dockerfile); err != nil {
				return fmt.Errorf("%s component %s Dockerfile: %w", role, component.Name, err)
			}
		}
	}
	for _, item := range hookLists(configuration) {
		for _, hook := range item.hooks {
			if hook.Runner.Type != "host" {
				continue
			}
			root := candidateWorktree
			if hook.Revision == "base" {
				root = baseWorktree
			}
			workingDirectory, err := resolveWorkingDirectory(root, hook.WorkingDirectory)
			if err != nil {
				return fmt.Errorf("%s hook %s working_directory: %w", item.name, hook.Name, err)
			}
			if err := validateRelativeExecutable(workingDirectory, hook.Command[0]); err != nil {
				return fmt.Errorf("%s hook %s command: %w", item.name, hook.Name, err)
			}
		}
	}
	for _, group := range scenarioLists(configuration) {
		for _, scenario := range group.scenarios {
			for _, step := range scenario.Steps {
				root := candidateWorktree
				if step.CWDRevision == "base" {
					root = baseWorktree
				}
				workingDirectory, err := resolveWorkingDirectory(root, step.WorkingDirectory)
				if err != nil {
					return fmt.Errorf("%s scenario %s step %s working_directory: %w", group.name, scenario.Name, step.Name, err)
				}
				if err := validateRelativeExecutable(workingDirectory, step.Command[0]); err != nil {
					return fmt.Errorf("%s scenario %s step %s command: %w", group.name, scenario.Name, step.Name, err)
				}
			}
		}
	}
	return nil
}

func resolveWorkingDirectory(root, configured string) (string, error) {
	if configured == "" {
		return root, nil
	}
	return config.ResolveWithin(root, configured)
}

func validateRelativeExecutable(workingDirectory, executable string) error {
	if !strings.ContainsAny(executable, `/\\`) {
		return nil
	}
	resolved, err := config.ResolveWithin(workingDirectory, executable)
	if err != nil {
		return err
	}
	info, err := os.Stat(resolved)
	if err != nil {
		return err
	}
	if info.IsDir() {
		return fmt.Errorf("executable path %q is a directory", executable)
	}
	return nil
}

type namedHooks struct {
	name  string
	hooks []config.Hook
}

func hookLists(configuration config.Config) []namedHooks {
	return []namedHooks{
		{name: "bootstrap", hooks: configuration.Lifecycle.Bootstrap},
		{name: "transition", hooks: configuration.Lifecycle.Transition},
		{name: "candidate_only", hooks: configuration.Lifecycle.CandidateOnly},
		{name: "rollback", hooks: configuration.Lifecycle.Rollback},
	}
}

type namedScenarios struct {
	name      string
	scenarios []config.Scenario
}

func scenarioLists(configuration config.Config) []namedScenarios {
	return []namedScenarios{
		{name: "baseline", scenarios: configuration.Workloads.Baseline},
		{name: "base_after_transition", scenarios: configuration.Workloads.Controls.BaseAfterTransition.Scenarios},
		{name: "candidate_before_coexistence", scenarios: configuration.Workloads.Controls.CandidateBeforeCoexistence.Scenarios},
		{name: "base_with_candidate_running", scenarios: configuration.Workloads.Controls.BaseWithCandidateRunning.Scenarios},
		{name: "base_to_candidate", scenarios: configuration.Workloads.Coexistence.BaseToCandidate},
		{name: "candidate_to_base", scenarios: configuration.Workloads.Coexistence.CandidateToBase},
		{name: "alternating", scenarios: configuration.Workloads.Coexistence.Alternating},
		{name: "candidate_traffic", scenarios: configuration.Workloads.CandidateTraffic},
		{name: "rollback", scenarios: configuration.Workloads.Rollback.Scenarios},
	}
}

func resolveEnvironmentFile(root string, raw []byte, override string, processEnvironment map[string]string) (string, error) {
	path := override
	if path == "" {
		var probe struct {
			SharedEnvironment struct {
				EnvFile string `yaml:"env_file"`
			} `yaml:"shared_environment"`
		}
		if err := yaml.Unmarshal(raw, &probe); err != nil {
			return "", fmt.Errorf("inspect shared_environment.env_file: %w", err)
		}
		path = probe.SharedEnvironment.EnvFile
		missing := ""
		path = environmentReference.ReplaceAllStringFunc(path, func(match string) string {
			name := environmentReference.FindStringSubmatch(match)[1]
			value, exists := processEnvironment[name]
			if !exists {
				missing = name
				return match
			}
			return value
		})
		if missing != "" {
			return "", fmt.Errorf("shared_environment.env_file requires environment variable %s", missing)
		}
		if strings.Contains(path, "${") {
			return "", errors.New("shared_environment.env_file contains an unsupported or nested environment expansion")
		}
	}
	if path == "" {
		return "", nil
	}
	if override != "" {
		if !filepath.IsAbs(path) {
			path = filepath.Join(root, path)
		}
		absolute, err := filepath.Abs(path)
		if err != nil {
			return "", err
		}
		return filepath.Clean(absolute), nil
	}
	return config.ResolveWithin(root, path)
}

func resolveArtifactRoot(root, configured, override string) (string, error) {
	if override != "" {
		if !filepath.IsAbs(override) {
			override = filepath.Join(root, override)
		}
		return filepath.Abs(override)
	}
	if filepath.IsAbs(configured) {
		return "", errors.New("configured artifacts.directory must be repository-relative; use --artifact-dir for an explicit external path")
	}
	return config.ResolveDestinationWithin(root, configured)
}

func checkWritableDirectory(path string) error {
	probeRoot := path
	for {
		info, err := os.Stat(probeRoot)
		if err == nil {
			if !info.IsDir() {
				return fmt.Errorf("artifact directory is not writable: %s is not a directory", probeRoot)
			}
			break
		}
		if !os.IsNotExist(err) {
			return fmt.Errorf("artifact directory is not writable: %w", err)
		}
		parent := filepath.Dir(probeRoot)
		if parent == probeRoot {
			return fmt.Errorf("artifact directory is not writable: no existing parent for %s", path)
		}
		probeRoot = parent
	}
	probe, err := os.MkdirTemp(probeRoot, ".backline-write-check-")
	if err != nil {
		return fmt.Errorf("artifact directory is not writable: %w", err)
	}
	return os.Remove(probe)
}

func checkHostExecutables(configuration config.Config) error {
	seen := make(map[string]struct{})
	for _, group := range scenarioLists(configuration) {
		for _, scenario := range group.scenarios {
			for _, step := range scenario.Steps {
				if len(step.Command) == 0 {
					continue
				}
				executable := step.Command[0]
				if strings.ContainsAny(executable, `/\`) {
					continue
				}
				if _, checked := seen[executable]; checked {
					continue
				}
				seen[executable] = struct{}{}
				if _, err := exec.LookPath(executable); err != nil {
					return fmt.Errorf("required host workload executable %q is not on PATH", executable)
				}
			}
		}
	}
	for _, group := range hookLists(configuration) {
		for _, hook := range group.hooks {
			if hook.Runner.Type != "host" || len(hook.Command) == 0 {
				continue
			}
			if err := checkExecutableOnPath(hook.Command[0], seen); err != nil {
				return err
			}
		}
	}
	return nil
}

func checkExecutableOnPath(executable string, seen map[string]struct{}) error {
	if strings.ContainsAny(executable, `/\\`) {
		return nil
	}
	if _, checked := seen[executable]; checked {
		return nil
	}
	seen[executable] = struct{}{}
	if _, err := exec.LookPath(executable); err != nil {
		return fmt.Errorf("required host executable %q is not on PATH", executable)
	}
	return nil
}

func localDockerHost(host string) bool {
	if host == "" || strings.HasPrefix(host, "unix://") || strings.HasPrefix(host, "npipe://") {
		return true
	}
	parsed, err := url.Parse(host)
	if err != nil || (parsed.Scheme != "tcp" && parsed.Scheme != "http" && parsed.Scheme != "https") {
		return false
	}
	hostname := parsed.Hostname()
	if strings.EqualFold(hostname, "localhost") {
		return true
	}
	address := net.ParseIP(hostname)
	return address != nil && address.IsLoopback()
}

func environmentMap(values []string) map[string]string {
	result := make(map[string]string, len(values))
	for _, entry := range values {
		name, value, ok := strings.Cut(entry, "=")
		if ok {
			result[name] = value
		}
	}
	return result
}

func digest(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
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
	return result
}

func appendIf(values []error, err error) []error {
	if err != nil {
		return append(values, err)
	}
	return values
}

func errorText(err error) string {
	if err == nil {
		return ""
	}
	return err.Error() + ": "
}

type safeError struct {
	err     error
	message string
}

func (e *safeError) Error() string { return e.message }
func (e *safeError) Unwrap() error { return e.err }

func redactPreflightError(err error, secrets []string) error {
	if err == nil {
		return nil
	}
	return &safeError{err: err, message: redact.New(secrets...).String(err.Error())}
}

func configuration(err error) error { return &Error{Kind: ConfigurationError, Err: err} }
func prerequisite(err error) error  { return &Error{Kind: PrerequisiteError, Err: err} }
func operational(err error) error   { return &Error{Kind: OperationalError, Err: err} }
