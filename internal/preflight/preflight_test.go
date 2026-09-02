package preflight

import (
	"context"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

type dockerContextRunner struct {
	commands []string
}

type composeConfigRunner struct {
	real *processrun.Runner
}

func (r *composeConfigRunner) Run(ctx context.Context, command processrun.Command) processrun.Result {
	if command.Name == "docker" {
		return processrun.Result{Launched: true, ExitCode: 0, Stdout: []byte(`{"name":"fixture","services":{"db":{"healthcheck":{"test":["CMD","true"]}}}}`)}
	}
	return r.real.Run(ctx, command)
}

func (r *composeConfigRunner) Output(ctx context.Context, dir, name string, args ...string) ([]byte, error) {
	return r.real.Output(ctx, dir, name, args...)
}

func (r *dockerContextRunner) Run(context.Context, processrun.Command) processrun.Result {
	return processrun.Result{}
}

func (r *dockerContextRunner) Output(_ context.Context, _ string, name string, args ...string) ([]byte, error) {
	command := strings.Join(append([]string{name}, args...), " ")
	r.commands = append(r.commands, command)
	switch command {
	case "docker context show":
		return []byte("remote\n"), nil
	case "docker context inspect --format {{json .Endpoints.docker.Host}}":
		return []byte("\"tcp://docker.example:2376\"\n"), nil
	case "docker compose version --short":
		return []byte("2.39.0\n"), nil
	case "docker version --format {{.Server.Version}}":
		return []byte("29.1.2\n"), nil
	default:
		return nil, errors.New("unexpected command: " + command)
	}
}

func TestLocalDockerHost(t *testing.T) {
	for _, host := range []string{"", "unix:///var/run/docker.sock", "npipe:////./pipe/docker_engine", "tcp://127.0.0.1:2375", "http://localhost:2375", "tcp://LOCALHOST:2375"} {
		if !localDockerHost(host) {
			t.Fatalf("expected local: %s", host)
		}
	}
	for _, host := range []string{"ssh://builder@example.com", "tcp://10.0.0.8:2375", "https://docker.example.com"} {
		if localDockerHost(host) {
			t.Fatalf("expected remote: %s", host)
		}
	}
}

func TestRemoteDockerIsRejectedBeforeDaemonContact(t *testing.T) {
	t.Setenv("DOCKER_HOST", "")
	runner := &dockerContextRunner{}
	_, err := New(runner).checkDocker(context.Background(), false)
	if err == nil || !strings.Contains(err.Error(), "not local") {
		t.Fatalf("err=%v", err)
	}
	for _, command := range runner.commands {
		if strings.HasPrefix(command, "docker version") {
			t.Fatalf("remote daemon was contacted before rejection: %v", runner.commands)
		}
	}
}

func TestPreflightErrorsAreRedactedWithoutLosingClassification(t *testing.T) {
	original := configuration(errors.New("token=top-secret"))
	safe := redactPreflightError(original, []string{"top-secret"})
	if strings.Contains(safe.Error(), "top-secret") || !strings.Contains(safe.Error(), "[REDACTED]") {
		t.Fatalf("unsafe error: %v", safe)
	}
	var classified *Error
	if !errors.As(safe, &classified) || classified.Kind != ConfigurationError {
		t.Fatalf("classification lost: %T %v", safe, safe)
	}
}

func TestWritableArtifactCheckLeavesNoConfiguredDirectory(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "not-created", "runs")
	if err := checkWritableDirectory(target); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(root, "not-created")); !os.IsNotExist(err) {
		t.Fatalf("writability check left configured directories behind: %v", err)
	}
}

func TestArtifactRootRequiresCLIForExternalConfiguredPath(t *testing.T) {
	root := t.TempDir()
	if _, err := resolveArtifactRoot(root, "../outside", ""); err == nil {
		t.Fatal("expected configured path rejection")
	}
	if _, err := resolveArtifactRoot(root, ".backline/runs", t.TempDir()); err != nil {
		t.Fatal(err)
	}
}

func TestEnvironmentFilePathSubstitutionIsSinglePass(t *testing.T) {
	root := t.TempDir()
	raw := []byte("shared_environment:\n  env_file: ${ENV_PATH}\n")
	if _, err := resolveEnvironmentFile(root, raw, "", map[string]string{"ENV_PATH": "${NESTED}", "NESTED": ".env"}); err == nil || !strings.Contains(err.Error(), "nested") {
		t.Fatalf("nested substitution error=%v", err)
	}
	if _, err := resolveEnvironmentFile(root, raw, "", map[string]string{}); err == nil || !strings.Contains(err.Error(), "ENV_PATH") {
		t.Fatalf("missing substitution error=%v", err)
	}
}

func TestPrepareUsesNonHeadCandidateAndAppliesDirtyRuleOnlyToHead(t *testing.T) {
	root := t.TempDir()
	runTestGit(t, root, "init")
	runTestGit(t, root, "config", "user.email", "backline-tests@example.invalid")
	runTestGit(t, root, "config", "user.name", "Backline Tests")
	writeFixtureFile(t, root, "backline.yml", preflightFixtureConfig)
	writeFixtureFile(t, root, "compose.yml", "services:\n  db:\n    image: postgres:16\n    healthcheck:\n      test: [CMD, true]\n")
	writeFixtureFile(t, root, "Dockerfile", "FROM scratch\n")
	runTestGit(t, root, "add", ".")
	runTestGit(t, root, "commit", "-m", "base")
	runTestGit(t, root, "tag", "base")
	writeFixtureFile(t, root, "candidate.txt", "candidate\n")
	runTestGit(t, root, "add", "candidate.txt")
	runTestGit(t, root, "commit", "-m", "candidate")
	candidateSHA := strings.TrimSpace(testGitOutput(t, root, "rev-parse", "HEAD"))
	writeFixtureFile(t, root, "tip.txt", "tip\n")
	runTestGit(t, root, "add", "tip.txt")
	runTestGit(t, root, "commit", "-m", "active tip")
	writeFixtureFile(t, root, "Dockerfile", "FROM scratch\n# tracked dirty change\n")

	service := New(&composeConfigRunner{real: processrun.NewRunner()})
	prepared, cleanup, err := service.Prepare(context.Background(), Options{StartDirectory: root, CandidateRef: "HEAD^"})
	if err != nil {
		t.Fatalf("prepare non-HEAD candidate: %v", err)
	}
	if prepared.CandidateSHA != candidateSHA {
		t.Fatalf("candidate SHA=%s want=%s", prepared.CandidateSHA, candidateSHA)
	}
	temporaryRoot := prepared.TemporaryRoot
	if err := cleanup(); err != nil {
		t.Fatalf("cleanup: %v", err)
	}
	if _, err := os.Stat(temporaryRoot); !os.IsNotExist(err) {
		t.Fatalf("preflight root remains: %v", err)
	}

	if _, cleanup, err := service.Prepare(context.Background(), Options{StartDirectory: root, CandidateRef: "HEAD"}); err == nil || !strings.Contains(err.Error(), "tracked checkout changes") {
		if cleanup != nil {
			_ = cleanup()
		}
		t.Fatalf("dirty HEAD error=%v", err)
	}
}

func runTestGit(t *testing.T, root string, args ...string) {
	t.Helper()
	command := exec.Command("git", args...)
	command.Dir = root
	if output, err := command.CombinedOutput(); err != nil {
		t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, output)
	}
}

func testGitOutput(t *testing.T, root string, args ...string) string {
	t.Helper()
	command := exec.Command("git", args...)
	command.Dir = root
	output, err := command.CombinedOutput()
	if err != nil {
		t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, output)
	}
	return string(output)
}

func writeFixtureFile(t *testing.T, root, name, contents string) {
	t.Helper()
	if err := os.WriteFile(filepath.Join(root, name), []byte(contents), 0o644); err != nil {
		t.Fatal(err)
	}
}

const preflightFixtureConfig = `version: 1
revisions:
  base: base
shared_environment:
  compose_file: compose.yml
  services: [db]
release:
  components:
    - name: api
      build: {context: ., dockerfile: Dockerfile}
      run:
        internal_port: 8080
        readiness: {type: http, path: /health}
workloads:
  baseline:
    - name: baseline
      steps:
        - {name: base, target: base.api, command: [go, version]}
  controls:
    base_after_transition: {reuse_baseline: true}
    candidate_before_coexistence: {reuse_baseline: true}
    base_with_candidate_running: {reuse_baseline: true}
  coexistence:
    base_to_candidate:
      - name: base to candidate
        steps:
          - {name: base, target: base.api, command: [go, version]}
          - {name: candidate, target: candidate.api, command: [go, version]}
    candidate_to_base:
      - name: candidate to base
        steps:
          - {name: candidate, target: candidate.api, command: [go, version]}
          - {name: base, target: base.api, command: [go, version]}
  candidate_traffic:
    - name: traffic
      mutates_state: true
      steps:
        - {name: candidate, target: candidate.api, command: [go, version]}
  rollback: {reuse_baseline: true}
`
