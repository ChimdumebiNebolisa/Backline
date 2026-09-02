package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestResolveDestinationWithinAllowsMissingChildren(t *testing.T) {
	root := t.TempDir()
	resolved, err := ResolveDestinationWithin(root, ".backline/runs")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(strings.ToLower(resolved), strings.ToLower(filepath.Join(".backline", "runs"))) {
		t.Fatalf("resolved=%q", resolved)
	}
	if _, err := os.Stat(filepath.Join(root, ".backline")); !os.IsNotExist(err) {
		t.Fatalf("resolution created a directory: %v", err)
	}
}

func TestResolveDestinationWithinRejectsSymlinkedAncestorEscape(t *testing.T) {
	root := t.TempDir()
	outside := t.TempDir()
	link := filepath.Join(root, "artifacts")
	if err := os.Symlink(outside, link); err != nil {
		t.Skipf("platform does not permit an unprivileged directory symlink: %v", err)
	}
	if _, err := ResolveDestinationWithin(root, "artifacts/runs"); err == nil {
		t.Fatal("expected symlink escape rejection")
	}
}
