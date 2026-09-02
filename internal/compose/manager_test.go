package compose

import (
	"context"
	"encoding/json"
	"errors"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/dockerops"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

func TestBuildOverrideLabelsServicesBuildsNetworksAndVolumes(t *testing.T) {
	normalized := []byte(`{"services":{"postgres":{"build":{"context":"."}},"redis":{"image":"redis"}},"networks":{"backend":{}},"volumes":{"data":{}}}`)
	encoded, err := BuildOverride(normalized, []string{"postgres", "redis"}, "bl-test")
	if err != nil {
		t.Fatal(err)
	}
	var override map[string]any
	if err := json.Unmarshal(encoded, &override); err != nil {
		t.Fatal(err)
	}
	services := override["services"].(map[string]any)
	postgres := services["postgres"].(map[string]any)
	redis := services["redis"].(map[string]any)
	if postgres["build"] == nil || redis["build"] != nil {
		t.Fatalf("service overrides = %#v", services)
	}
	labels := postgres["labels"].(map[string]any)
	if labels[dockerops.LabelRunID] != "bl-test" || labels[dockerops.LabelOwned] != "true" {
		t.Fatalf("labels = %#v", labels)
	}
	if override["networks"] == nil || override["volumes"] == nil {
		t.Fatalf("resource labels missing: %#v", override)
	}
}

func TestBuildOverrideDoesNotClaimExternalResources(t *testing.T) {
	normalized := []byte(`{"services":{"postgres":{"image":"postgres"}},"networks":{"external":{"external":true}},"volumes":{"external":{"external":true}}}`)
	encoded, err := BuildOverride(normalized, []string{"postgres"}, "bl-test")
	if err != nil {
		t.Fatal(err)
	}
	var override map[string]any
	if err := json.Unmarshal(encoded, &override); err != nil {
		t.Fatal(err)
	}
	if _, exists := override["networks"].(map[string]any)["external"]; exists {
		t.Fatalf("external network was claimed: %s", encoded)
	}
	if volumes, exists := override["volumes"]; exists {
		if _, claimed := volumes.(map[string]any)["external"]; claimed {
			t.Fatalf("external volume was claimed: %s", encoded)
		}
	}
}

func TestStartReturnsCleanupIdentityAfterPostUpFailure(t *testing.T) {
	runner := partialStartRunner{}
	manager := New(runner, dockerops.New(runner))
	environment, _, err := manager.Start(context.Background(), StartInput{
		RunID: "bl-test", Project: "backline-bl-test", Worktree: t.TempDir(),
		ComposePath: filepath.Join(t.TempDir(), "compose.yml"), ComposeJSON: []byte(`{"services":{"postgres":{"image":"postgres"}}}`),
		Services: []string{"postgres"}, TemporaryRoot: t.TempDir(), StartupTimeout: time.Second, MaxBytes: 4096,
	})
	if err == nil {
		t.Fatal("expected network discovery failure")
	}
	if environment.Project != "backline-bl-test" || environment.OverridePath == "" || environment.ContainerIDs["postgres"] != "container-id" {
		t.Fatalf("partial environment lost cleanup identity: %#v", environment)
	}
}

type partialStartRunner struct{}

func (partialStartRunner) Run(context.Context, processrun.Command) processrun.Result {
	return processrun.Result{Launched: true, ExitCode: 0}
}

func (partialStartRunner) Output(_ context.Context, _ string, _ string, arguments ...string) ([]byte, error) {
	joined := strings.Join(arguments, " ")
	switch {
	case strings.Contains(joined, "compose") && strings.Contains(joined, "ps --quiet postgres"):
		return []byte("container-id\n"), nil
	case joined == "inspect container-id":
		return []byte(`[{"Id":"container-id","Image":"sha256:image","State":{"Running":true,"Health":{"Status":"healthy"}}}]`), nil
	case strings.Contains(joined, "network ls"):
		return nil, errors.New("network discovery unavailable")
	default:
		return nil, errors.New("unexpected command: " + joined)
	}
}
