package reporting

import (
	"encoding/xml"
	"strings"
	"testing"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
)

func TestRenderersIncludeBothVerdictsAndControls(t *testing.T) {
	summary := fixture()
	terminal := Terminal(summary)
	markdown := Markdown(summary, "1.0.0")
	for _, expected := range []string{"Mixed-version compatibility", "Rollback compatibility [RAW]", "base after transition", "Result: FAIL"} {
		if !strings.Contains(terminal, expected) {
			t.Fatalf("terminal missing %q:\n%s", expected, terminal)
		}
	}
	for _, expected := range []string{"# Backline rollout compatibility report", "ROLLBACK_SCENARIO_FAILED", "Scope and attribution"} {
		if !strings.Contains(markdown, expected) {
			t.Fatalf("Markdown missing %q", expected)
		}
	}
}

func TestJUnitContainsSyntheticVerdicts(t *testing.T) {
	contents, err := JUnit(fixture())
	if err != nil {
		t.Fatal(err)
	}
	var parsed struct {
		XMLName xml.Name `xml:"testsuites"`
	}
	if err := xml.Unmarshal(contents, &parsed); err != nil {
		t.Fatalf("invalid XML: %v", err)
	}
	for _, expected := range []string{"mixed-version compatibility", "rollback compatibility [RAW]", "ROLLBACK_SCENARIO_FAILED"} {
		if !strings.Contains(string(contents), expected) {
			t.Fatalf("JUnit missing %q:\n%s", expected, contents)
		}
	}
}

func fixture() model.Summary {
	now := time.Date(2026, 9, 1, 19, 9, 0, 0, time.UTC)
	return model.Summary{
		RunID: "bl-20260901-190900-7f3c", Status: model.OverallFail,
		StartedAt: now, FinishedAt: now.Add(time.Minute),
		Base: model.RevisionIdentity{Ref: "main", SHA: "a81c92f0"}, Candidate: model.RevisionIdentity{Ref: "HEAD", SHA: "f1073bd0"},
		Config:       model.ConfigIdentity{Path: "backline.yml", SourceSHA: "f1073bd0", SHA256: "digest"},
		RollbackMode: model.RollbackRaw,
		Verdicts: map[string]model.Verdict{
			"mixed_version": {Status: model.VerdictPass, ReasonCodes: []model.ReasonCode{model.ReasonNone}, Message: "configured evidence passed"},
			"rollback":      {Status: model.VerdictFail, ReasonCodes: []model.ReasonCode{model.ReasonRollbackScenarioFailed}, Message: "fresh base failed"},
		},
		Controls:  map[string]model.StageOutcome{"base_after_transition": model.StagePass, "candidate_before_coexistence": model.StagePass, "base_with_candidate_running": model.StagePass},
		Artifacts: map[string]string{"report": ".backline/runs/bl-20260901-190900-7f3c/report.md"}, Cleanup: model.CleanupResult{Status: model.StagePass},
	}
}
