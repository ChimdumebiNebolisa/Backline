package cleanup

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
)

func TestRegistryRemovesOnlyOwnedChild(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "run", "shared")
	if err := os.MkdirAll(target, 0o700); err != nil {
		t.Fatal(err)
	}
	outside := t.TempDir()
	registry := NewRegistry("bl-test")
	if err := registry.RegisterPath(root, target); err != nil {
		t.Fatal(err)
	}
	if err := registry.RegisterPath(root, root); err == nil {
		t.Fatal("expected owned root rejection")
	}
	if err := registry.RegisterPath(root, outside); err == nil {
		t.Fatal("expected outside path rejection")
	}
	result := registry.Cleanup(context.Background(), false)
	if result.Status != model.StagePass {
		t.Fatalf("cleanup = %#v", result)
	}
	if _, err := os.Stat(target); !os.IsNotExist(err) {
		t.Fatalf("target still exists: %v", err)
	}
	if _, err := os.Stat(outside); err != nil {
		t.Fatalf("outside path changed: %v", err)
	}
}

func TestRegistryRetentionPrintsResources(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "run")
	if err := os.Mkdir(target, 0o700); err != nil {
		t.Fatal(err)
	}
	registry := NewRegistry("bl-test")
	if err := registry.RegisterPath(root, target); err != nil {
		t.Fatal(err)
	}
	result := registry.Cleanup(context.Background(), true)
	if result.Status != model.StageSkipped || len(result.Remaining) != 1 {
		t.Fatalf("retention = %#v", result)
	}
	if _, err := os.Stat(target); err != nil {
		t.Fatalf("retained path missing: %v", err)
	}
}
