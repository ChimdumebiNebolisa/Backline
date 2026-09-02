package contracts

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"gopkg.in/yaml.v3"
)

func TestWorkflowsAreValidAndDoNotUsePullRequestTarget(t *testing.T) {
	_, source, _, _ := runtime.Caller(0)
	root := filepath.Join(filepath.Dir(source), "..", "..", ".github", "workflows")
	for _, name := range []string{"backline-ci.yml", "release.yml", "site-ci.yml"} {
		contents, err := os.ReadFile(filepath.Join(root, name))
		if err != nil {
			t.Fatal(err)
		}
		if strings.Contains(string(contents), "pull_request_target") {
			t.Fatalf("%s uses forbidden pull_request_target", name)
		}
		var document yaml.Node
		if err := yaml.Unmarshal(contents, &document); err != nil {
			t.Fatalf("%s is invalid YAML: %v", name, err)
		}
	}
}

func TestReleaseWorkflowBuildsRequiredTargetsAndChecksums(t *testing.T) {
	_, source, _, _ := runtime.Caller(0)
	contents, err := os.ReadFile(filepath.Join(filepath.Dir(source), "..", "..", ".github", "workflows", "release.yml"))
	if err != nil {
		t.Fatal(err)
	}
	workflow := string(contents)
	for _, expected := range []string{"linux/amd64", "linux/arm64", "darwin/amd64", "darwin/arm64", "windows/amd64", "SHA256SUMS", "sha256sum --check"} {
		if !strings.Contains(workflow, expected) {
			t.Errorf("release workflow missing %q", expected)
		}
	}
}
