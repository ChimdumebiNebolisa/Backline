package artifacts

import (
	"bufio"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
	"github.com/ChimdumebiNebolisa/Backline/internal/redact"
)

func TestSessionRedactsAndTruncatesEveryArtifact(t *testing.T) {
	session, err := NewSession(t.TempDir(), "bl-test", 8, redact.New("top-secret"))
	if err != nil {
		t.Fatal(err)
	}
	defer session.Close()
	if err := session.Emit("RUN_CREATED", "", map[string]any{"secret": "top-secret"}); err != nil {
		t.Fatal(err)
	}
	logPath, truncated, err := session.WriteLog("logs/test.log", []byte("top-secret and additional output"))
	if err != nil || !truncated {
		t.Fatalf("path=%q truncated=%v err=%v", logPath, truncated, err)
	}
	if err := session.WriteSummary(model.Summary{SchemaVersion: 1, RunID: "bl-test", Status: model.OverallError, Config: model.ConfigIdentity{Path: "top-secret"}}); err != nil {
		t.Fatal(err)
	}
	if err := session.WriteYAML("resolved-config.redacted.yml", []byte("version: 1\nenvironment:\n  token: top-secret\n")); err != nil {
		t.Fatal(err)
	}
	if err := session.Close(); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"events.jsonl", "logs/test.log", "summary.json", "resolved-config.redacted.yml"} {
		contents, err := os.ReadFile(filepath.Join(session.Root, filepath.FromSlash(name)))
		if err != nil {
			t.Fatal(err)
		}
		if strings.Contains(string(contents), "top-secret") {
			t.Fatalf("secret leaked in %s: %s", name, contents)
		}
	}
}

func TestStructuredArtifactsRemainValidWithShortSecrets(t *testing.T) {
	session, err := NewSession(t.TempDir(), "bl-test", 1024, redact.New("1"))
	if err != nil {
		t.Fatal(err)
	}
	if err := session.Emit("RUN_CREATED", "stage1", map[string]any{"attempt": 1, "value": "secret-1"}); err != nil {
		t.Fatal(err)
	}
	if err := session.WriteSummary(model.Summary{SchemaVersion: 1, RunID: "bl-test1", Status: model.OverallError}); err != nil {
		t.Fatal(err)
	}
	if err := session.WriteYAML("resolved-config.redacted.yml", []byte("version: 1\nenvironment:\n  DB_PASSWORD: direct-value\n  ordinary: value1\n")); err != nil {
		t.Fatal(err)
	}
	if _, _, err := session.WriteJSONLog("logs/inspect.json", map[string]any{"exit_code": 1, "message": "value=1"}); err != nil {
		t.Fatal(err)
	}
	if err := session.Close(); err != nil {
		t.Fatal(err)
	}

	summaryContents, err := os.ReadFile(filepath.Join(session.Root, "summary.json"))
	if err != nil {
		t.Fatal(err)
	}
	var summary map[string]any
	if err := json.Unmarshal(summaryContents, &summary); err != nil {
		t.Fatalf("summary JSON was corrupted: %v\n%s", err, summaryContents)
	}
	if summary["schema_version"] != float64(1) {
		t.Fatalf("numeric schema version was redacted: %#v", summary)
	}
	eventContents, err := os.ReadFile(filepath.Join(session.Root, "events.jsonl"))
	if err != nil {
		t.Fatal(err)
	}
	var event Event
	if err := json.Unmarshal(eventContents, &event); err != nil {
		t.Fatalf("event JSON was corrupted: %v\n%s", err, eventContents)
	}
	inspectContents, err := os.ReadFile(filepath.Join(session.Root, "logs", "inspect.json"))
	if err != nil {
		t.Fatal(err)
	}
	var inspection map[string]any
	if err := json.Unmarshal(inspectContents, &inspection); err != nil || inspection["exit_code"] != float64(1) {
		t.Fatalf("inspect JSON was corrupted: %v\n%s", err, inspectContents)
	}
	yamlContents, err := os.ReadFile(filepath.Join(session.Root, "resolved-config.redacted.yml"))
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(yamlContents), "direct-value") || !strings.Contains(string(yamlContents), "[REDACTED]") {
		t.Fatalf("sensitive YAML value was not structurally redacted:\n%s", yamlContents)
	}
}

func TestRunIDAndPathConfinement(t *testing.T) {
	runID, err := NewRunID(time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC))
	if err != nil || !strings.HasPrefix(runID, "bl-20260901-120000-") {
		t.Fatalf("runID=%q err=%v", runID, err)
	}
	session, err := NewSession(t.TempDir(), "bl-test", 1024, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer session.Close()
	if _, _, err := session.WriteLog("../escape", []byte("bad")); err == nil {
		t.Fatal("expected path escape rejection")
	}
}

func TestSessionRefusesToMergeWithExistingRunDirectory(t *testing.T) {
	root := t.TempDir()
	first, err := NewSession(root, "bl-test", 1024, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer first.Close()
	if _, err := NewSession(root, "bl-test", 1024, nil); err == nil {
		t.Fatal("expected duplicate run directory rejection")
	}
}

func TestExternalArtifactsAreRedacted(t *testing.T) {
	session, err := NewSession(t.TempDir(), "bl-test", 1024, redact.New("external-secret"))
	if err != nil {
		t.Fatal(err)
	}
	defer session.Close()
	destination := filepath.Join(t.TempDir(), "junit.xml")
	if err := session.WriteExternal(destination, []byte("<test>external-secret</test>")); err != nil {
		t.Fatal(err)
	}
	if err := session.AppendExternal(destination, []byte("external-secret")); err != nil {
		t.Fatal(err)
	}
	contents, err := os.ReadFile(destination)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(contents), "external-secret") {
		t.Fatalf("external artifact leaked secret: %s", contents)
	}
}

func TestEventsPreserveAppendOrder(t *testing.T) {
	session, err := NewSession(t.TempDir(), "bl-test", 1024, nil)
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"RUN_CREATED", "CONFIG_RESOLVED", "WORKTREE_CREATED"}
	for _, eventType := range want {
		if err := session.Emit(eventType, "preflight", nil); err != nil {
			t.Fatal(err)
		}
	}
	if err := session.Close(); err != nil {
		t.Fatal(err)
	}

	file, err := os.Open(filepath.Join(session.Root, "events.jsonl"))
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	scanner := bufio.NewScanner(file)
	var got []string
	var previous time.Time
	for scanner.Scan() {
		var event Event
		if err := json.Unmarshal(scanner.Bytes(), &event); err != nil {
			t.Fatal(err)
		}
		if !previous.IsZero() && event.Timestamp.Before(previous) {
			t.Fatalf("event timestamp moved backwards: %s before %s", event.Timestamp, previous)
		}
		previous = event.Timestamp
		got = append(got, event.Type)
	}
	if err := scanner.Err(); err != nil {
		t.Fatal(err)
	}
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("event order=%v want=%v", got, want)
	}
}
