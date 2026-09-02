package cleanup

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
)

type Resource struct {
	Kind          string
	ID            string
	ManualCommand string
	Remove        func(context.Context) error
	Exists        func(context.Context) (bool, error)
}

type Registry struct {
	runID     string
	mu        sync.Mutex
	resources []Resource
}

func NewRegistry(runID string) *Registry {
	return &Registry{runID: runID}
}

func (r *Registry) Register(resource Resource) error {
	if resource.Kind == "" || resource.ID == "" || resource.Remove == nil || resource.Exists == nil {
		return errors.New("cleanup resource requires kind, ID, remove, and exists")
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.resources = append(r.resources, resource)
	return nil
}

func (r *Registry) RegisterPath(ownedRoot, target string) error {
	rootAbs, err := filepath.Abs(ownedRoot)
	if err != nil {
		return err
	}
	targetAbs, err := filepath.Abs(target)
	if err != nil {
		return err
	}
	relative, err := filepath.Rel(rootAbs, targetAbs)
	if err != nil || relative == "." || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return fmt.Errorf("cleanup path %q is not a child of owned root %q", targetAbs, rootAbs)
	}
	return r.Register(Resource{
		Kind:          "path",
		ID:            targetAbs,
		ManualCommand: manualRemoveCommand(targetAbs),
		Remove: func(context.Context) error {
			return os.RemoveAll(targetAbs)
		},
		Exists: func(context.Context) (bool, error) {
			_, err := os.Lstat(targetAbs)
			if os.IsNotExist(err) {
				return false, nil
			}
			return err == nil, err
		},
	})
}

func (r *Registry) Cleanup(ctx context.Context, retain bool, observers ...func(string)) model.CleanupResult {
	observe := func(string) {}
	if len(observers) > 0 && observers[0] != nil {
		observe = observers[0]
	}
	r.mu.Lock()
	resources := append([]Resource(nil), r.resources...)
	r.mu.Unlock()
	remaining := make([]string, 0)
	if retain {
		for _, resource := range resources {
			observe("retaining " + resource.Kind + ":" + resource.ID)
			remaining = append(remaining, resource.Kind+":"+resource.ID+" | "+resource.ManualCommand)
		}
		return model.CleanupResult{Status: model.StageSkipped, ReasonCodes: []model.ReasonCode{model.ReasonNone}, Remaining: remaining}
	}
	failed := false
	for index := len(resources) - 1; index >= 0; index-- {
		resource := resources[index]
		observe("removing " + resource.Kind + ":" + resource.ID)
		if err := resource.Remove(ctx); err != nil {
			failed = true
			remaining = append(remaining, resource.Kind+":"+resource.ID+" remove failed: "+err.Error()+" | "+resource.ManualCommand)
			continue
		}
		exists, err := resource.Exists(ctx)
		observe("verified " + resource.Kind + ":" + resource.ID)
		if err != nil || exists {
			failed = true
			message := resource.Kind + ":" + resource.ID + " remains"
			if err != nil {
				message += ": " + err.Error()
			}
			remaining = append(remaining, message+" | "+resource.ManualCommand)
		}
	}
	if failed {
		return model.CleanupResult{Status: model.StageError, ReasonCodes: []model.ReasonCode{model.ReasonCleanupFailed}, Remaining: remaining}
	}
	return model.CleanupResult{Status: model.StagePass, ReasonCodes: []model.ReasonCode{model.ReasonNone}}
}

func manualRemoveCommand(path string) string {
	if filepath.Separator == '\\' {
		return fmt.Sprintf("Remove-Item -LiteralPath %q -Recurse -Force", path)
	}
	return fmt.Sprintf("rm -rf -- %q", path)
}
