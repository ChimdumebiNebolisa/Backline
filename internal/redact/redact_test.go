package redact

import (
	"strings"
	"testing"
)

func TestRedactorRemovesExactAndCommonSecrets(t *testing.T) {
	redactor := New("very-secret", "secret")
	input := "token=abc123 url=postgres://user:password@example/db exact=very-secret Authorization: Bearer ey.payload.signature"
	output := redactor.String(input)
	for _, forbidden := range []string{"abc123", "password", "very-secret", "ey.payload.signature"} {
		if strings.Contains(output, forbidden) {
			t.Fatalf("secret %q remained in %q", forbidden, output)
		}
	}
	if !strings.Contains(output, Marker) {
		t.Fatalf("redaction marker missing: %q", output)
	}
}

func TestEmptyRegisteredValueDoesNotRedactEverything(t *testing.T) {
	if output := New("").String("ordinary output"); output != "ordinary output" {
		t.Fatalf("output = %q", output)
	}
}

func TestShortSecretRequiresTokenBoundaries(t *testing.T) {
	output := New("1").String("version 2026-09-02 at=09:11:31 value=1 embedded=stage1")
	if strings.Contains(output, "value=1") || !strings.Contains(output, "2026-09-02") || !strings.Contains(output, "09:11:31") || !strings.Contains(output, "stage1") {
		t.Fatalf("short-value redaction damaged structural values: %q", output)
	}
}
