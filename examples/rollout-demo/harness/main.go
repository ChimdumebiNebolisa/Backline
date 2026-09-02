package main

import (
	"bufio"
	"bytes"
	"encoding/json"
	"encoding/xml"
	"errors"
	"flag"
	"fmt"
	"io/fs"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
)

func main() {
	caseName := flag.String("case", "safe", "rollout demo case")
	backline := flag.String("backline", "", "path to the Backline executable")
	fixture := flag.String("fixture", "examples/rollout-demo/fixture", "fixture source directory")
	artifactRoot := flag.String("artifact-root", "", "persistent artifact root for this case")
	keep := flag.Bool("keep", false, "keep the generated repository")
	flag.Parse()
	if *backline == "" {
		fatal(errors.New("--backline is required"))
	}
	expectedMixed, expectedRollback, expectedMode, expectedExit, mixedStatus, trafficStatus, rollbackStatus := expectations(*caseName)
	repository, err := os.MkdirTemp("", "backline-demo-")
	if err != nil {
		fatal(err)
	}
	if !*keep {
		defer os.RemoveAll(repository)
	}
	if err := copyTree(*fixture, repository, "backline.yml"); err != nil {
		fatal(err)
	}
	git(repository, "init", "--initial-branch=main")
	git(repository, "config", "user.name", "Backline Demo")
	git(repository, "config", "user.email", "demo@backline.invalid")
	git(repository, "add", ".")
	git(repository, "commit", "-m", "base revision")
	configuration, err := os.ReadFile(filepath.Join(*fixture, "backline.yml"))
	if err != nil {
		fatal(err)
	}
	configuration = prepareConfiguration(configuration, *caseName)
	if err := os.WriteFile(filepath.Join(repository, "backline.yml"), configuration, 0o600); err != nil {
		fatal(err)
	}
	if err := os.WriteFile(filepath.Join(repository, "candidate.txt"), []byte("candidate revision\n"), 0o600); err != nil {
		fatal(err)
	}
	git(repository, "add", ".")
	git(repository, "commit", "-m", "candidate revision")

	jsonOutput := filepath.Join(repository, "summary-copy.json")
	junitOutput := filepath.Join(repository, "junit-copy.xml")
	arguments := []string{"verify", "--base-ref", "HEAD^", "--candidate-ref", "HEAD", "--json-output", jsonOutput, "--junit-output", junitOutput, "--no-color"}
	artifactScanRoot := filepath.Join(repository, ".backline")
	if *artifactRoot != "" {
		caseArtifacts, absoluteErr := filepath.Abs(filepath.Join(*artifactRoot, *caseName))
		if absoluteErr != nil {
			fatal(absoluteErr)
		}
		arguments = append(arguments, "--artifact-dir", caseArtifacts)
		artifactScanRoot = caseArtifacts
	}
	command := exec.Command(*backline, arguments...)
	command.Dir = repository
	command.Env = append(os.Environ(), "DEMO_SECRET=fixture-secret-must-be-redacted", "DEMO_MIXED_STATUS="+mixedStatus, "DEMO_TRAFFIC_STATUS="+trafficStatus, "DEMO_ROLLBACK_STATUS="+rollbackStatus)
	var stdout, stderr bytes.Buffer
	command.Stdout, command.Stderr = &stdout, &stderr
	err = command.Run()
	fmt.Print(stdout.String())
	if stderr.Len() > 0 {
		fmt.Fprint(os.Stderr, stderr.String())
	}
	actualExit := 0
	if err != nil {
		var exitError *exec.ExitError
		if !errors.As(err, &exitError) {
			fatal(err)
		}
		actualExit = exitError.ExitCode()
	}
	if actualExit != expectedExit {
		fatal(fmt.Errorf("%s exit code = %d, want %d", *caseName, actualExit, expectedExit))
	}
	contents, err := os.ReadFile(jsonOutput)
	if err != nil {
		fatal(err)
	}
	var summary model.Summary
	if err := json.Unmarshal(contents, &summary); err != nil {
		fatal(fmt.Errorf("parse summary: %w", err))
	}
	assert(summary.SchemaVersion == 1, "summary schema version", summary.SchemaVersion, 1)
	assert(int(summary.ExitCode) == expectedExit, "summary exit code", summary.ExitCode, expectedExit)
	assert(summary.Verdicts["mixed_version"].Status == expectedMixed, "mixed verdict", summary.Verdicts["mixed_version"].Status, expectedMixed)
	assert(summary.Verdicts["rollback"].Status == expectedRollback, "rollback verdict", summary.Verdicts["rollback"].Status, expectedRollback)
	for _, name := range []string{"base_after_transition", "candidate_before_coexistence", "base_with_candidate_running"} {
		outcome, exists := summary.Controls[name]
		assert(exists, "control present "+name, exists, true)
		assert(outcome == model.StagePass, "control "+name, outcome, model.StagePass)
	}
	assert(summary.RollbackMode == expectedMode, "rollback mode", summary.RollbackMode, expectedMode)
	assert(summary.Cleanup.Status == model.StagePass, "cleanup", summary.Cleanup.Status, model.StagePass)
	for _, name := range []string{"base.api", "candidate.api"} {
		assert(summary.ComponentImages[name] != "", "component image "+name, summary.ComponentImages[name], "nonempty")
		assertLoopbackEndpoint(name, summary.ComponentEndpoints[name])
	}
	assert(summary.Environment.SharedServiceImages["postgres"] != "", "shared image postgres", summary.Environment.SharedServiceImages["postgres"], "nonempty")
	if *caseName == "mixed-failure" {
		assert(hasReason(summary.Verdicts["mixed_version"], model.ReasonCoexistenceScenarioFailed), "mixed reason", summary.Verdicts["mixed_version"].ReasonCodes, model.ReasonCoexistenceScenarioFailed)
		assert(hasFailedStep(summary, "coexistence"), "mixed failed stage", summary.Scenarios, "coexistence")
	}
	if *caseName == "rollback-failure" {
		assert(hasReason(summary.Verdicts["rollback"], model.ReasonRollbackScenarioFailed), "rollback reason", summary.Verdicts["rollback"].ReasonCodes, model.ReasonRollbackScenarioFailed)
		assert(hasFailedStep(summary, "rollback"), "rollback failed stage", summary.Scenarios, "rollback")
	}
	if *caseName == "candidate-only-failure" {
		assert(hasReason(summary.Verdicts["rollback"], model.ReasonCandidateOnlyHookFailed), "candidate-only reason", summary.Verdicts["rollback"].ReasonCodes, model.ReasonCandidateOnlyHookFailed)
		assert(hasFailedStage(summary, "candidate_only/"), "candidate-only failed stage", summary.Stages, "candidate_only/")
		assert(hasStageOutcome(summary, "rollback", model.StagePass), "candidate-only rollback diagnostic", summary.Stages, model.StagePass)
	}
	for _, artifact := range []string{"report", "summary", "events", "resolved_config", "junit"} {
		path := summary.Artifacts[artifact]
		if path == "" {
			fatal(fmt.Errorf("summary is missing %s artifact path", artifact))
		}
		if _, err := os.Stat(path); err != nil {
			fatal(fmt.Errorf("%s artifact: %w", artifact, err))
		}
	}
	assertEventArtifact(summary)
	if leakedSecret(artifactScanRoot, "fixture-secret-must-be-redacted") || leakedSecret(repository, "fixture-secret-must-be-redacted") {
		fatal(errors.New("secret fixture value was found in generated artifacts"))
	}
	junit, err := os.ReadFile(junitOutput)
	if err != nil {
		fatal(err)
	}
	var suites struct {
		XMLName xml.Name `xml:"testsuites"`
	}
	if err := xml.Unmarshal(junit, &suites); err != nil || suites.XMLName.Local != "testsuites" {
		fatal(fmt.Errorf("invalid JUnit XML: %w", err))
	}
	assertNoDockerResources(summary.RunID)
	worktrees := output(repository, "git", "worktree", "list", "--porcelain")
	if strings.Contains(worktrees, "backline-preflight-") {
		fatal(errors.New("temporary Git worktree leaked"))
	}
	fmt.Printf("Demo assertion: %s passed (mixed=%s rollback=%s exit=%d)\n", *caseName, expectedMixed, expectedRollback, expectedExit)
	if *keep {
		fmt.Printf("Generated repository: %s\n", repository)
	}
}

func expectations(name string) (model.VerdictStatus, model.VerdictStatus, model.RollbackMode, int, string, string, string) {
	switch name {
	case "safe":
		return model.VerdictPass, model.VerdictPass, model.RollbackRaw, 0, "ACTIVE", "ACTIVE", "ACTIVE"
	case "mixed-failure":
		return model.VerdictFail, model.VerdictPass, model.RollbackRaw, 10, "ARCHIVED", "ACTIVE", "ACTIVE"
	case "rollback-failure":
		return model.VerdictPass, model.VerdictFail, model.RollbackRaw, 10, "ACTIVE", "ARCHIVED", "ARCHIVED"
	case "prepared-rollback":
		return model.VerdictPass, model.VerdictPass, model.RollbackPrepared, 0, "ACTIVE", "ARCHIVED", "ACTIVE"
	case "handoff":
		return model.VerdictPass, model.VerdictPass, model.RollbackPrepared, 0, "ACTIVE", "ACTIVE", "ACTIVE"
	case "candidate-only-failure":
		return model.VerdictPass, model.VerdictInconclusive, model.RollbackRaw, 20, "ACTIVE", "ACTIVE", "ACTIVE"
	default:
		fatal(fmt.Errorf("unknown demo case %q", name))
		return "", "", "", 0, "", "", ""
	}
}

func prepareConfiguration(contents []byte, caseName string) []byte {
	configuration := string(contents)
	switch caseName {
	case "prepared-rollback":
		configuration = strings.Replace(configuration, "  rollback: []", `  rollback:
    - name: prepare archived state for base
      revision: candidate
      runner:
        type: component
        component: api
      environment:
        PGPASSWORD: backline-demo
      command: [psql, -h, postgres, -U, backline, -d, backline, -v, ON_ERROR_STOP=1, -c, "UPDATE demo_items SET status = 'ACTIVE' WHERE id = 'candidate-traffic'"]`, 1)
	case "handoff":
		configuration = strings.Replace(configuration, "  candidate_only: []", `  candidate_only:
    - name: write cross-stage handoff
      revision: candidate
      runner:
        type: host
      command: [go, run, ./workload.go, handoff-write]`, 1)
		configuration = strings.Replace(configuration, "  rollback: []", `  rollback:
    - name: read cross-stage handoff
      revision: base
      runner:
        type: host
      command: [go, run, ./workload.go, handoff-read]`, 1)
	case "candidate-only-failure":
		configuration = strings.Replace(configuration, "  candidate_only: []", `  candidate_only:
    - name: fail candidate-only cutover
      revision: candidate
      runner:
        type: host
      command: [go, run, ./workload.go, fail]`, 1)
		configuration = strings.Replace(configuration, "name: base reads candidate traffic", "name: base rereads baseline after incomplete cutover", 1)
		configuration = strings.Replace(configuration, "name: read candidate-created item", "name: reread baseline item", 1)
		configuration = strings.Replace(configuration, `command: [go, run, ./workload.go, read, candidate-traffic, "${DEMO_ROLLBACK_STATUS}"]`, "command: [go, run, ./workload.go, read, baseline, ACTIVE]", 1)
	}
	return []byte(configuration)
}

func copyTree(source, destination, skip string) error {
	return filepath.WalkDir(source, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		relative, err := filepath.Rel(source, path)
		if err != nil || relative == "." || relative == skip {
			return err
		}
		target := filepath.Join(destination, relative)
		if entry.IsDir() {
			return os.MkdirAll(target, 0o700)
		}
		return copyFile(path, target)
	})
}

func copyFile(source, destination string) error {
	contents, err := os.ReadFile(source)
	if err != nil {
		return err
	}
	return os.WriteFile(destination, contents, 0o600)
}

func git(directory string, arguments ...string) { run(directory, "git", arguments...) }

func output(directory, name string, arguments ...string) string {
	command := exec.Command(name, arguments...)
	command.Dir = directory
	contents, err := command.CombinedOutput()
	if err != nil {
		fatal(fmt.Errorf("%s %s: %w\n%s", name, strings.Join(arguments, " "), err, contents))
	}
	return string(contents)
}

func run(directory, name string, arguments ...string) { _ = output(directory, name, arguments...) }

func hasReason(verdict model.Verdict, reason model.ReasonCode) bool {
	for _, candidate := range verdict.ReasonCodes {
		if candidate == reason {
			return true
		}
	}
	return false
}

func hasFailedStep(summary model.Summary, stage string) bool {
	for _, step := range summary.Scenarios {
		if step.Stage == stage && step.Outcome == model.StageFail {
			return true
		}
	}
	return false
}

func hasFailedStage(summary model.Summary, prefix string) bool {
	for _, stage := range summary.Stages {
		if strings.HasPrefix(stage.Name, prefix) && stage.Outcome == model.StageFail {
			return true
		}
	}
	return false
}

func hasStageOutcome(summary model.Summary, name string, outcome model.StageOutcome) bool {
	for _, stage := range summary.Stages {
		if stage.Name == name && stage.Outcome == outcome {
			return true
		}
	}
	return false
}

func assertLoopbackEndpoint(name, value string) {
	parsed, err := url.Parse(value)
	if err != nil || parsed.Hostname() != "127.0.0.1" || parsed.Port() == "" {
		fatal(fmt.Errorf("component endpoint %s is not a loopback URL: %q", name, value))
	}
}

func assertEventArtifact(summary model.Summary) {
	file, err := os.Open(summary.Artifacts["events"])
	if err != nil {
		fatal(err)
	}
	defer file.Close()
	seen := map[string]bool{}
	firstIndex := map[string]int{}
	eventIndex := 0
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		var event struct {
			Timestamp time.Time      `json:"timestamp"`
			Type      string         `json:"type"`
			RunID     string         `json:"run_id"`
			Details   map[string]any `json:"details"`
		}
		if err := json.Unmarshal(scanner.Bytes(), &event); err != nil {
			fatal(fmt.Errorf("invalid event JSON: %w", err))
		}
		if event.Timestamp.IsZero() || event.Type == "" || event.RunID != summary.RunID || event.Details == nil {
			fatal(fmt.Errorf("invalid or out-of-order event: %s", scanner.Text()))
		}
		seen[event.Type] = true
		if _, exists := firstIndex[event.Type]; !exists {
			firstIndex[event.Type] = eventIndex
		}
		eventIndex++
	}
	if err := scanner.Err(); err != nil {
		fatal(err)
	}
	ordered := []string{"RUN_CREATED", "CONFIG_RESOLVED", "WORKTREE_CREATED", "IMAGE_BUILD_STARTED", "SHARED_SERVICES_READY", "BASE_READY", "TRANSITION_STARTED", "BASE_CONTROL_COMPLETED", "CANDIDATE_READY", "CANDIDATE_CONTROL_COMPLETED", "BASE_WITH_CANDIDATE_CONTROL_COMPLETED", "CANDIDATE_STOPPED", "ROLLBACK_MODE_SELECTED", "BASE_RESTARTED", "VERDICT_FINALIZED", "CLEANUP_COMPLETED"}
	previousIndex := -1
	for _, required := range ordered {
		if !seen[required] {
			fatal(fmt.Errorf("event artifact is missing %s", required))
		}
		if firstIndex[required] <= previousIndex {
			fatal(fmt.Errorf("event %s is out of order", required))
		}
		previousIndex = firstIndex[required]
	}
}

func leakedSecret(root, secret string) bool {
	leaked := false
	_ = filepath.WalkDir(root, func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return nil
		}
		contents, readErr := os.ReadFile(path)
		if readErr == nil && bytes.Contains(contents, []byte(secret)) {
			leaked = true
		}
		return nil
	})
	return leaked
}

func assertNoDockerResources(runID string) {
	for _, kind := range []string{"container", "network", "volume", "image"} {
		arguments := []string{kind, "ls", "--quiet", "--filter", "label=dev.backline.run_id=" + runID}
		if kind == "container" {
			arguments = []string{"container", "ls", "--all", "--quiet", "--filter", "label=dev.backline.run_id=" + runID}
		} else if kind == "image" {
			arguments = []string{"image", "ls", "--all", "--quiet", "--filter", "label=dev.backline.run_id=" + runID}
		}
		if strings.TrimSpace(output("", "docker", arguments...)) != "" {
			fatal(fmt.Errorf("leaked Docker %s for run %s", kind, runID))
		}
	}
}

func assert(ok bool, label string, actual, expected any) {
	if !ok {
		fatal(fmt.Errorf("%s = %v, want %v", label, actual, expected))
	}
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, "demo failed:", err)
	os.Exit(1)
}
