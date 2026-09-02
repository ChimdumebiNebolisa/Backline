package reporting

import (
	"encoding/xml"
	"fmt"
	"path/filepath"
	"runtime"
	"sort"
	"strings"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
)

func Terminal(summary model.Summary) string {
	var output strings.Builder
	fmt.Fprintf(&output, "Base       %-12s %s\n", summary.Base.Ref, short(summary.Base.SHA))
	fmt.Fprintf(&output, "Candidate  %-12s %s\n", summary.Candidate.Ref, short(summary.Candidate.SHA))
	fmt.Fprintf(&output, "Run        %s\n", summary.RunID)
	fmt.Fprintln(&output)
	fmt.Fprintln(&output, "Baseline")
	fmt.Fprintf(&output, "  %-12s %d steps\n", stageOutcome(summary, "baseline"), countStageSteps(summary, "baseline"))
	fmt.Fprintln(&output)
	fmt.Fprintln(&output, "Controls")
	for _, control := range []struct{ key, label string }{
		{"base_after_transition", "base after transition"},
		{"candidate_before_coexistence", "candidate before coexistence"},
		{"base_with_candidate_running", "base with candidate running"},
	} {
		fmt.Fprintf(&output, "  %-12s %s\n", summary.Controls[control.key], control.label)
	}
	fmt.Fprintln(&output)
	writeTerminalVerdict(&output, "Mixed-version compatibility", summary.Verdicts["mixed_version"], "")
	fmt.Fprintln(&output)
	writeTerminalVerdict(&output, "Rollback compatibility", summary.Verdicts["rollback"], " ["+string(summary.RollbackMode)+"]")
	if failed, exists := firstFailedStep(summary); exists {
		fmt.Fprintln(&output)
		fmt.Fprintln(&output, "Failed scenario")
		fmt.Fprintf(&output, "  Stage:      %s\n", failed.Stage)
		fmt.Fprintf(&output, "  Scenario:   %s\n", failed.Scenario)
		fmt.Fprintf(&output, "  Step:       %s\n", failed.Step)
		fmt.Fprintf(&output, "  Target:     %s\n", failed.Target)
		fmt.Fprintf(&output, "  Command:    %s\n", strings.Join(failed.Command, " "))
		fmt.Fprintf(&output, "  Revision:   %s %s\n", failed.CWDRevision, failed.RevisionSHA)
		fmt.Fprintf(&output, "  Directory:  %s\n", failed.WorkingDirectory)
		fmt.Fprintf(&output, "  Started:    %s\n", failed.StartedAt.UTC().Format("2006-01-02T15:04:05Z07:00"))
		fmt.Fprintf(&output, "  Duration:   %d ms\n", failed.DurationMS)
		if failed.ExitCode != nil {
			fmt.Fprintf(&output, "  Exit code:  %d\n", *failed.ExitCode)
		}
		if failed.Signal != "" {
			fmt.Fprintf(&output, "  Signal:     %s\n", failed.Signal)
		}
		if failed.TerminationAttempted {
			fmt.Fprintf(&output, "  Termination: %s\n", terminationText(failed.TerminationAttempted, failed.TerminationSucceeded))
		}
		fmt.Fprintf(&output, "  Reason:     %s\n", failed.ReasonCode)
		fmt.Fprintf(&output, "  Classification: %s\n", outcomeClassification(failed.Outcome))
		fmt.Fprintf(&output, "  Log:        %s\n", failed.LogPath)
	}
	fmt.Fprintln(&output)
	fmt.Fprintln(&output, "Artifacts")
	fmt.Fprintf(&output, "  %s\n", artifactRoot(summary))
	if len(summary.Cleanup.Remaining) > 0 {
		fmt.Fprintln(&output)
		fmt.Fprintln(&output, "Retained resources")
		for _, resource := range summary.Cleanup.Remaining {
			fmt.Fprintf(&output, "  %s\n", resource)
		}
	}
	fmt.Fprintln(&output)
	fmt.Fprintf(&output, "Result: %s\n", summary.Status)
	return output.String()
}

func Markdown(summary model.Summary, version string) string {
	var output strings.Builder
	fmt.Fprintln(&output, "# Backline rollout compatibility report")
	fmt.Fprintln(&output)
	fmt.Fprintf(&output, "- Backline version: `%s`\n", version)
	fmt.Fprintf(&output, "- Run: `%s`\n", summary.RunID)
	fmt.Fprintf(&output, "- Started: `%s`\n", summary.StartedAt.UTC().Format("2006-01-02T15:04:05Z"))
	fmt.Fprintf(&output, "- Finished: `%s`\n", summary.FinishedAt.UTC().Format("2006-01-02T15:04:05Z"))
	fmt.Fprintf(&output, "- Host platform: `%s/%s`\n", runtime.GOOS, runtime.GOARCH)
	fmt.Fprintf(&output, "- Configuration: `%s` from `%s` (`%s`)\n", summary.Config.Path, summary.Config.SourceSHA, summary.Config.SHA256)
	fmt.Fprintf(&output, "- Base: `%s` (`%s`)\n", summary.Base.Ref, summary.Base.SHA)
	fmt.Fprintf(&output, "- Candidate: `%s` (`%s`)\n", summary.Candidate.Ref, summary.Candidate.SHA)
	fmt.Fprintf(&output, "- Environment file: `%s` (`%s`)\n", environmentDisplay(summary), summary.Environment.EnvFileSHA256)
	fmt.Fprintf(&output, "- Docker: context `%s`, engine `%s`, Compose `%s`\n", summary.Environment.DockerContext, summary.Environment.DockerVersion, summary.Environment.ComposeVersion)
	fmt.Fprintf(&output, "- Selected shared services: `%s`\n", strings.Join(summary.Environment.SelectedServices, "`, `"))
	fmt.Fprintf(&output, "- Run-scoped networks: `%s`\n", strings.Join(summary.Environment.Networks, "`, `"))
	if len(summary.Warnings) > 0 {
		fmt.Fprintln(&output, "\n## Safety warnings")
		for _, warning := range summary.Warnings {
			fmt.Fprintf(&output, "- **WARNING:** %s\n", warning)
		}
	}

	fmt.Fprintln(&output, "\n## Verdicts")
	writeMarkdownVerdict(&output, "Mixed-version compatibility", summary.Verdicts["mixed_version"])
	writeMarkdownVerdict(&output, "Rollback compatibility ["+string(summary.RollbackMode)+"]", summary.Verdicts["rollback"])

	fmt.Fprintln(&output, "\n## Controls")
	fmt.Fprintln(&output, "| Control | Outcome |")
	fmt.Fprintln(&output, "| --- | --- |")
	for _, key := range []string{"base_after_transition", "candidate_before_coexistence", "base_with_candidate_running"} {
		fmt.Fprintf(&output, "| %s | %s |\n", strings.ReplaceAll(key, "_", " "), summary.Controls[key])
	}

	fmt.Fprintln(&output, "\n## Lifecycle stages")
	fmt.Fprintln(&output, "| Stage | Outcome | Reason codes | Revision/runner | Exit | Termination | Log | Diagnostics |")
	fmt.Fprintln(&output, "| --- | --- | --- | --- | ---: | --- | --- | --- |")
	for _, stage := range summary.Stages {
		exit := ""
		if stage.ExitCode != nil {
			exit = fmt.Sprint(*stage.ExitCode)
		}
		diagnostic := stage.Message
		if stage.Truncated {
			diagnostic = strings.TrimSpace(diagnostic + " [output truncated]")
		}
		fmt.Fprintf(&output, "| %s | %s | %s | %s/%s | %s | %s | `%s` | %s |\n", stage.Name, stage.Outcome, reasonText(stage.ReasonCodes), stage.Revision, stage.Runner, exit, terminationText(stage.TerminationAttempted, stage.TerminationSucceeded), stage.LogPath, markdownCell(diagnostic))
	}

	fmt.Fprintln(&output, "\n## Scenario steps")
	if len(summary.Scenarios) == 0 {
		fmt.Fprintln(&output, "No scenario steps completed.")
	} else {
		fmt.Fprintln(&output, "| Stage | Scenario | Step | Target | Command | Revision | Started | Duration | Outcome | Classification | Reason | Termination | Log |")
		fmt.Fprintln(&output, "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
		for _, step := range summary.Scenarios {
			logPath := step.LogPath
			if step.Truncated {
				logPath += " [truncated]"
			}
			fmt.Fprintf(&output, "| %s | %s | %s | %s | `%s` | %s `%s`<br>`%s` | %s | %d ms | %s | %s | %s | %s | `%s` |\n", step.Stage, markdownCell(step.Scenario), markdownCell(step.Step), step.Target, markdownCell(strings.Join(step.Command, " ")), step.CWDRevision, step.RevisionSHA, markdownCell(step.WorkingDirectory), step.StartedAt.UTC().Format("2006-01-02T15:04:05Z07:00"), step.DurationMS, step.Outcome, outcomeClassification(step.Outcome), step.ReasonCode, terminationText(step.TerminationAttempted, step.TerminationSucceeded), logPath)
		}
	}

	fmt.Fprintln(&output, "\n## Image identities")
	writeMap(&output, "Release component", summary.ComponentImages)
	writeMap(&output, "Shared service", summary.Environment.SharedServiceImages)
	fmt.Fprintln(&output, "\n## Host endpoints")
	writeMap(&output, "Component", summary.ComponentEndpoints)

	fmt.Fprintln(&output, "\n## Operational errors")
	if len(summary.OperationalErrors) == 0 {
		fmt.Fprintln(&output, "None.")
	} else {
		for _, item := range summary.OperationalErrors {
			fmt.Fprintf(&output, "- `%s` `%s`: %s\n", item.Stage, item.ReasonCode, item.Message)
		}
	}

	fmt.Fprintln(&output, "\n## Cleanup")
	fmt.Fprintf(&output, "Outcome: **%s**.\n", summary.Cleanup.Status)
	for _, resource := range summary.Cleanup.Remaining {
		fmt.Fprintf(&output, "- `%s`\n", resource)
	}

	fmt.Fprintln(&output, "\n## Scope and attribution")
	fmt.Fprintln(&output, "A PASS is limited to the configured lifecycle, controls, hooks, and workloads. It is not proof of universal compatibility.")
	fmt.Fprintln(&output)
	fmt.Fprintln(&output, "A direct failure demonstrates incompatibility in the observed run, but does not by itself establish a unique root cause.")
	return output.String()
}

type junitSuites struct {
	XMLName xml.Name     `xml:"testsuites"`
	Suites  []junitSuite `xml:"testsuite"`
}

type junitSuite struct {
	Name     string      `xml:"name,attr"`
	Tests    int         `xml:"tests,attr"`
	Failures int         `xml:"failures,attr"`
	Errors   int         `xml:"errors,attr"`
	Skipped  int         `xml:"skipped,attr"`
	Cases    []junitCase `xml:"testcase"`
}

type junitCase struct {
	Name      string        `xml:"name,attr"`
	Classname string        `xml:"classname,attr"`
	Time      string        `xml:"time,attr,omitempty"`
	Failure   *junitProblem `xml:"failure,omitempty"`
	Error     *junitProblem `xml:"error,omitempty"`
	Skipped   *junitProblem `xml:"skipped,omitempty"`
}

type junitProblem struct {
	Message string `xml:"message,attr,omitempty"`
	Body    string `xml:",chardata"`
}

func JUnit(summary model.Summary) ([]byte, error) {
	work := junitSuite{Name: "Backline lifecycle"}
	for _, stage := range summary.Stages {
		work.Cases = append(work.Cases, junitFromOutcome("stage: "+stage.Name, "backline.lifecycle", stage.Outcome, reasonText(stage.ReasonCodes), stage.Message, float64(stage.DurationMS)/1000))
	}
	for _, scenario := range groupScenarios(summary.Scenarios) {
		work.Cases = append(work.Cases, junitFromOutcome(scenario.name, "backline."+scenario.stage, scenario.outcome, scenario.reason, scenario.message, float64(scenario.durationMS)/1000))
	}
	for index, operational := range summary.OperationalErrors {
		work.Cases = append(work.Cases, junitCase{Name: fmt.Sprintf("operational error %d: %s", index+1, operational.Stage), Classname: "backline.operational", Error: &junitProblem{Message: string(operational.ReasonCode), Body: operational.Message}})
	}
	countSuite(&work)

	verdictSuite := junitSuite{Name: "Backline compatibility verdicts"}
	for _, item := range []struct {
		name string
		key  string
	}{{"mixed-version compatibility", "mixed_version"}, {"rollback compatibility [" + string(summary.RollbackMode) + "]", "rollback"}} {
		verdict := summary.Verdicts[item.key]
		outcome := model.StagePass
		if verdict.Status == model.VerdictFail {
			outcome = model.StageFail
		} else if verdict.Status == model.VerdictInconclusive {
			outcome = model.StageIncomplete
		}
		verdictSuite.Cases = append(verdictSuite.Cases, junitFromOutcome(item.name, "backline.verdict", outcome, reasonText(verdict.ReasonCodes), verdict.Message, 0))
	}
	countSuite(&verdictSuite)

	encoded, err := xml.MarshalIndent(junitSuites{Suites: []junitSuite{work, verdictSuite}}, "", "  ")
	if err != nil {
		return nil, err
	}
	return append([]byte(xml.Header), append(encoded, '\n')...), nil
}

type groupedScenario struct {
	stage      string
	name       string
	outcome    model.StageOutcome
	reason     string
	message    string
	durationMS int64
}

func groupScenarios(steps []model.StepResult) []groupedScenario {
	indexes := make(map[string]int)
	result := make([]groupedScenario, 0)
	for _, step := range steps {
		key := step.Stage + "\x00" + step.Scenario
		index, exists := indexes[key]
		if !exists {
			index = len(result)
			indexes[key] = index
			result = append(result, groupedScenario{stage: step.Stage, name: step.Scenario, outcome: model.StagePass, reason: string(model.ReasonNone)})
		}
		group := &result[index]
		group.durationMS += step.DurationMS
		if step.Outcome != model.StagePass && group.outcome == model.StagePass {
			group.outcome = step.Outcome
			group.reason = string(step.ReasonCode)
			group.message = fmt.Sprintf("step %s: %s", step.Step, step.Message)
			if step.LogPath != "" {
				group.message += "\nlog: " + step.LogPath
			}
		}
	}
	return result
}

func junitFromOutcome(name, class string, outcome model.StageOutcome, reason, message string, seconds float64) junitCase {
	item := junitCase{Name: name, Classname: class, Time: fmt.Sprintf("%.3f", seconds)}
	problem := &junitProblem{Message: reason, Body: message}
	switch outcome {
	case model.StageFail:
		item.Failure = problem
	case model.StageError:
		item.Error = problem
	case model.StageSkipped, model.StageIncomplete:
		item.Skipped = problem
	}
	return item
}

func countSuite(suite *junitSuite) {
	suite.Tests = len(suite.Cases)
	for _, item := range suite.Cases {
		if item.Failure != nil {
			suite.Failures++
		}
		if item.Error != nil {
			suite.Errors++
		}
		if item.Skipped != nil {
			suite.Skipped++
		}
	}
}

func writeTerminalVerdict(output *strings.Builder, title string, verdict model.Verdict, suffix string) {
	fmt.Fprintln(output, title+suffix)
	fmt.Fprintf(output, "  %-12s %s\n", verdict.Status, verdict.Message)
	if verdict.Status != model.VerdictPass {
		fmt.Fprintf(output, "  Reason: %s\n", reasonText(verdict.ReasonCodes))
	}
}

func writeMarkdownVerdict(output *strings.Builder, title string, verdict model.Verdict) {
	fmt.Fprintf(output, "\n### %s\n\n", title)
	fmt.Fprintf(output, "**%s** — %s\n", verdict.Status, verdict.Message)
	if verdict.Status != model.VerdictPass {
		fmt.Fprintf(output, "\nReason codes: `%s`\n", reasonText(verdict.ReasonCodes))
	}
}

func writeMap(output *strings.Builder, label string, values map[string]string) {
	if len(values) == 0 {
		fmt.Fprintf(output, "- %s images: none\n", label)
		return
	}
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	for _, key := range keys {
		fmt.Fprintf(output, "- %s `%s`: `%s`\n", label, key, values[key])
	}
}

func environmentDisplay(summary model.Summary) string {
	if summary.Environment.EnvFileDisplay == "" {
		return "[none]"
	}
	if summary.Environment.EnvFileExternal {
		return "[external] " + summary.Environment.EnvFileDisplay
	}
	return summary.Environment.EnvFileDisplay
}

func reasonText(reasons []model.ReasonCode) string {
	if len(reasons) == 0 {
		return string(model.ReasonNone)
	}
	values := make([]string, len(reasons))
	for index, reason := range reasons {
		values[index] = string(reason)
	}
	return strings.Join(values, ", ")
}

func artifactRoot(summary model.Summary) string {
	if path := summary.Artifacts["report"]; path != "" {
		return filepath.Dir(path)
	}
	return "[unavailable]"
}

func markdownCell(value string) string {
	value = strings.ReplaceAll(value, "|", "\\|")
	return strings.ReplaceAll(value, "\n", "<br>")
}

func short(value string) string {
	if len(value) <= 7 {
		return value
	}
	return value[:7]
}

func terminationText(attempted, succeeded bool) string {
	if !attempted {
		return "not required"
	}
	if succeeded {
		return "succeeded"
	}
	return "failed"
}

func outcomeClassification(outcome model.StageOutcome) string {
	switch outcome {
	case model.StageFail:
		return "project-controlled"
	case model.StageError:
		return "operational"
	case model.StageIncomplete:
		return "interrupted"
	default:
		return "evidence"
	}
}

func stageOutcome(summary model.Summary, name string) model.StageOutcome {
	for _, stage := range summary.Stages {
		if stage.Name == name {
			return stage.Outcome
		}
	}
	return model.StageSkipped
}

func countStageSteps(summary model.Summary, stage string) int {
	count := 0
	for _, step := range summary.Scenarios {
		if step.Stage == stage {
			count++
		}
	}
	return count
}

func firstFailedStep(summary model.Summary) (model.StepResult, bool) {
	for _, step := range summary.Scenarios {
		if step.Outcome == model.StageFail || step.Outcome == model.StageError || step.Outcome == model.StageIncomplete {
			return step, true
		}
	}
	return model.StepResult{}, false
}
