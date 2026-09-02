package dockerops

import (
	"context"
	"strings"
	"testing"
	"time"

	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

type fakeRunner struct {
	commands []processrun.Command
	result   processrun.Result
	output   []byte
}

func (f *fakeRunner) Run(_ context.Context, command processrun.Command) processrun.Result {
	f.commands = append(f.commands, command)
	return f.result
}

func (f *fakeRunner) Output(_ context.Context, _, _ string, _ ...string) ([]byte, error) {
	return f.output, nil
}

func TestCreateUsesEntrypointReplacementLoopbackAndSortedEnvironment(t *testing.T) {
	runner := &fakeRunner{result: processrun.Result{Launched: true, ExitCode: 0, Stdout: []byte("container-id\n")}}
	client := New(runner)
	id, _, err := client.Create(context.Background(), CreateInput{
		Name:         "bl-run-base-api",
		Image:        "image:tag",
		Command:      []string{"/app/start", "--mode", "test"},
		Environment:  map[string]string{"Z": "last", "A": "first"},
		Labels:       map[string]string{LabelRunID: "bl-run", LabelRevision: "0123456789abcdef"},
		InternalPort: 8080,
	})
	if err != nil || id != "container-id" {
		t.Fatalf("id=%q err=%v", id, err)
	}
	joined := strings.Join(runner.commands[0].Args, " ")
	for _, expected := range []string{"--publish 127.0.0.1::8080", "--entrypoint /app/start image:tag --mode test", "--env A=first --env Z=last", "--label dev.backline.revision_sha=0123456789abcdef"} {
		if !strings.Contains(joined, expected) {
			t.Fatalf("args %q missing %q", joined, expected)
		}
	}
	if strings.Contains(joined, "--mount") {
		t.Fatalf("release component received an unexpected host mount: %q", joined)
	}
}

func TestBuildCarriesRunAndRevisionIdentityLabels(t *testing.T) {
	runner := &fakeRunner{result: processrun.Result{Launched: true, ExitCode: 0}, output: []byte("sha256:image\n")}
	client := New(runner)
	imageID, _, err := client.Build(context.Background(), BuildInput{
		Context: ".", Dockerfile: "Dockerfile", Tag: "backline/test:base", Timeout: time.Minute, MaxBytes: 4096,
		Labels: map[string]string{LabelRunID: "bl-test", LabelRole: "base", LabelComponent: "api", LabelRevision: "0123456789abcdef"},
	})
	if err != nil || imageID != "sha256:image" {
		t.Fatalf("image=%q err=%v", imageID, err)
	}
	joined := strings.Join(runner.commands[0].Args, " ")
	for _, expected := range []string{"--label dev.backline.run_id=bl-test", "--label dev.backline.role=base", "--label dev.backline.component=api", "--label dev.backline.revision_sha=0123456789abcdef"} {
		if !strings.Contains(joined, expected) {
			t.Fatalf("build args %q missing %q", joined, expected)
		}
	}
}

func TestBuildRejectsMissingImmutableImageIdentity(t *testing.T) {
	runner := &fakeRunner{result: processrun.Result{Launched: true, ExitCode: 0}}
	_, _, err := New(runner).Build(context.Background(), BuildInput{Context: ".", Dockerfile: "Dockerfile", Tag: "backline/test:base", Timeout: time.Minute})
	if err == nil || !strings.Contains(err.Error(), "empty ID") {
		t.Fatalf("err=%v", err)
	}
}

func TestCreateRejectsMissingContainerIdentity(t *testing.T) {
	runner := &fakeRunner{result: processrun.Result{Launched: true, ExitCode: 0}}
	_, _, err := New(runner).Create(context.Background(), CreateInput{Name: "test", Image: "image:test"})
	if err == nil || !strings.Contains(err.Error(), "exactly one container ID") {
		t.Fatalf("err=%v", err)
	}
}
