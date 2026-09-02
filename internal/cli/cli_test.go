package cli

import (
	"bytes"
	"context"
	"errors"
	"strings"
	"testing"
)

type fakeHandler struct {
	verify      VerifyOptions
	validate    ValidateOptions
	doctor      DoctorOptions
	called      string
	code        int
	err         error
	panicOnCall bool
}

func (f *fakeHandler) Verify(_ context.Context, options VerifyOptions) (int, error) {
	if f.panicOnCall {
		panic("sensitive panic details")
	}
	f.called, f.verify = "verify", options
	return f.code, f.err
}

func TestUnexpectedPanicMapsToInternalErrorWithoutDetails(t *testing.T) {
	handler := &fakeHandler{panicOnCall: true}
	var errOut bytes.Buffer
	code := Run(context.Background(), []string{"verify"}, Streams{Out: &bytes.Buffer{}, Err: &errOut}, "test", handler)
	if code != 24 || strings.Contains(errOut.String(), "sensitive") || !strings.Contains(errOut.String(), "internal Backline error") {
		t.Fatalf("code=%d stderr=%q", code, errOut.String())
	}
}

func (f *fakeHandler) Validate(_ context.Context, options ValidateOptions) (int, error) {
	f.called, f.validate = "validate", options
	return f.code, f.err
}

func (f *fakeHandler) Doctor(_ context.Context, options DoctorOptions) (int, error) {
	f.called, f.doctor = "doctor", options
	return f.code, f.err
}

func TestVersionAndHelp(t *testing.T) {
	for _, test := range []struct {
		args []string
		want string
	}{
		{args: []string{"version"}, want: "backline test-version"},
		{args: []string{"--version"}, want: "backline test-version"},
		{args: []string{"help"}, want: "Usage:"},
		{args: []string{"--help"}, want: "Usage:"},
		{args: []string{"help", "verify"}, want: "backline verify"},
		{args: []string{"help", "validate"}, want: "backline validate"},
		{args: []string{"help", "doctor"}, want: "backline doctor"},
		{args: []string{"help", "version"}, want: "backline version"},
		{args: []string{"verify", "--help"}, want: "backline verify"},
		{args: []string{"validate", "--help"}, want: "backline validate"},
		{args: []string{"doctor", "--help"}, want: "backline doctor"},
	} {
		t.Run(strings.Join(test.args, "_"), func(t *testing.T) {
			var out, errOut bytes.Buffer
			code := Run(context.Background(), test.args, Streams{Out: &out, Err: &errOut}, "test-version", &fakeHandler{})
			if code != 0 {
				t.Fatalf("code = %d, stderr = %s", code, errOut.String())
			}
			if !strings.Contains(out.String(), test.want) {
				t.Fatalf("output %q does not contain %q", out.String(), test.want)
			}
		})
	}
}

func TestVersionRejectsArguments(t *testing.T) {
	var out, errOut bytes.Buffer
	code := Run(context.Background(), []string{"version", "unexpected"}, Streams{Out: &out, Err: &errOut}, "test-version", &fakeHandler{})
	if code != 21 || !strings.Contains(errOut.String(), "does not accept arguments") {
		t.Fatalf("code=%d stdout=%q stderr=%q", code, out.String(), errOut.String())
	}
}

func TestVerifyParsesAllFlags(t *testing.T) {
	handler := &fakeHandler{code: 10}
	args := []string{"verify", "--config", "config/backline.yml", "--base-ref", "main", "--candidate-ref", "feature", "--env-file", "C:/tmp/test.env", "--artifact-dir", "artifacts", "--json-output", "summary.json", "--junit-output", "junit.xml", "--keep-on-failure", "--verbose", "--no-color", "--allow-unsafe-compose", "--allow-remote-docker"}
	code := Run(context.Background(), args, Streams{Out: &bytes.Buffer{}, Err: &bytes.Buffer{}}, "test", handler)
	if code != 10 || handler.called != "verify" {
		t.Fatalf("code = %d, called = %q", code, handler.called)
	}
	if handler.verify.ConfigPath != "config/backline.yml" || handler.verify.CandidateRef != "feature" || !handler.verify.KeepOnFailure || !handler.verify.AllowRemoteDocker {
		t.Fatalf("unexpected options: %#v", handler.verify)
	}
}

func TestValidateAndDoctorDefaults(t *testing.T) {
	validate := &fakeHandler{}
	if code := Run(context.Background(), []string{"validate"}, Streams{Out: &bytes.Buffer{}, Err: &bytes.Buffer{}}, "test", validate); code != 0 {
		t.Fatalf("validate code = %d", code)
	}
	if validate.validate.ConfigPath != "backline.yml" || validate.validate.CandidateRef != "HEAD" {
		t.Fatalf("validate defaults = %#v", validate.validate)
	}

	doctor := &fakeHandler{}
	if code := Run(context.Background(), []string{"doctor", "--artifact-dir", "out", "--allow-remote-docker"}, Streams{Out: &bytes.Buffer{}, Err: &bytes.Buffer{}}, "test", doctor); code != 0 {
		t.Fatalf("doctor code = %d", code)
	}
	if doctor.doctor.ArtifactDir != "out" || !doctor.doctor.AllowRemoteDocker {
		t.Fatalf("doctor options = %#v", doctor.doctor)
	}
}

func TestUsageErrorsDoNotCallHandler(t *testing.T) {
	for _, args := range [][]string{{"unknown"}, {"verify", "extra"}, {"validate", "--missing"}, {"help", "missing"}} {
		handler := &fakeHandler{}
		var errOut bytes.Buffer
		code := Run(context.Background(), args, Streams{Out: &bytes.Buffer{}, Err: &errOut}, "test", handler)
		if code != 21 || handler.called != "" || errOut.Len() == 0 {
			t.Fatalf("args=%v code=%d called=%q stderr=%q", args, code, handler.called, errOut.String())
		}
	}
}

func TestHandlerErrorIsPrinted(t *testing.T) {
	handler := &fakeHandler{code: 23, err: errors.New("docker unavailable")}
	var errOut bytes.Buffer
	code := Run(context.Background(), []string{"doctor"}, Streams{Out: &bytes.Buffer{}, Err: &errOut}, "test", handler)
	if code != 23 || !strings.Contains(errOut.String(), "docker unavailable") {
		t.Fatalf("code=%d stderr=%q", code, errOut.String())
	}
}
