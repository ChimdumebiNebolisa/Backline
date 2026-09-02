package gitops

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/config"
	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

type Repository struct {
	Root   string
	runner CommandRunner
}

type CommandRunner interface {
	Run(context.Context, processrun.Command) processrun.Result
	Output(context.Context, string, string, ...string) ([]byte, error)
}

func Discover(ctx context.Context, start string, runner CommandRunner) (*Repository, error) {
	if runner == nil {
		runner = processrun.NewRunner()
	}
	output, err := runner.Output(ctx, start, "git", "rev-parse", "--show-toplevel")
	if err != nil {
		return nil, fmt.Errorf("locate Git repository: %w", err)
	}
	root := strings.TrimSpace(string(output))
	if root == "" {
		return nil, fmt.Errorf("Git repository root is empty")
	}
	return &Repository{Root: filepath.Clean(root), runner: runner}, nil
}

func (r *Repository) Resolve(ctx context.Context, ref string) (string, error) {
	if strings.TrimSpace(ref) == "" {
		return "", fmt.Errorf("Git ref is empty")
	}
	output, err := r.runner.Output(ctx, r.Root, "git", "rev-parse", "--verify", ref+"^{commit}")
	if err != nil {
		return "", fmt.Errorf("resolve Git ref %q: %w", ref, err)
	}
	return strings.TrimSpace(string(output)), nil
}

func (r *Repository) ReadCommittedFile(ctx context.Context, sha, path string) ([]byte, string, error) {
	cleaned, err := config.CleanRepositoryPath(path)
	if err != nil {
		return nil, "", fmt.Errorf("configuration path: %w", err)
	}
	resolved, err := r.resolveCommittedPath(ctx, sha, cleaned)
	if err != nil {
		return nil, "", err
	}
	output, err := r.runner.Output(ctx, r.Root, "git", "show", sha+":"+resolved)
	if err != nil {
		return nil, "", fmt.Errorf("read %s from %s: %w", resolved, sha, err)
	}
	return output, resolved, nil
}

func (r *Repository) resolveCommittedPath(ctx context.Context, sha, path string) (string, error) {
	current := path
	for redirects := 0; redirects < 16; redirects++ {
		parts := strings.Split(current, "/")
		redirected := false
		for index := range parts {
			prefix := strings.Join(parts[:index+1], "/")
			output, err := r.runner.Output(ctx, r.Root, "git", "ls-tree", sha, "--", prefix)
			if err != nil {
				return "", fmt.Errorf("inspect committed path %q: %w", prefix, err)
			}
			line := strings.TrimSpace(string(output))
			if line == "" {
				return "", fmt.Errorf("committed path %q does not exist in %s", prefix, sha)
			}
			fields := strings.Fields(line)
			if len(fields) < 3 {
				return "", fmt.Errorf("unexpected git ls-tree output for %q", prefix)
			}
			if fields[0] != "120000" {
				continue
			}
			targetBytes, err := r.runner.Output(ctx, r.Root, "git", "show", sha+":"+prefix)
			if err != nil {
				return "", fmt.Errorf("read committed symlink %q: %w", prefix, err)
			}
			target := strings.TrimSpace(string(targetBytes))
			if filepath.IsAbs(target) || filepath.VolumeName(target) != "" {
				return "", fmt.Errorf("committed symlink %q points outside the repository", prefix)
			}
			tail := strings.Join(parts[index+1:], "/")
			resolved := filepath.ToSlash(filepath.Clean(filepath.Join(filepath.Dir(prefix), target, tail)))
			if resolved == ".." || strings.HasPrefix(resolved, "../") {
				return "", fmt.Errorf("committed symlink %q escapes the repository", prefix)
			}
			current = resolved
			redirected = true
			break
		}
		if !redirected {
			return current, nil
		}
	}
	return "", fmt.Errorf("too many committed symlink redirects for %q", path)
}

func (r *Repository) TrackedDirty(ctx context.Context) (bool, error) {
	output, err := r.runner.Output(ctx, r.Root, "git", "status", "--porcelain", "--untracked-files=no")
	if err != nil {
		return false, err
	}
	return strings.TrimSpace(string(output)) != "", nil
}

func (r *Repository) CreateWorktree(ctx context.Context, sha, destination string) error {
	if !filepath.IsAbs(destination) {
		return fmt.Errorf("worktree destination must be absolute")
	}
	if err := os.MkdirAll(filepath.Dir(destination), 0o700); err != nil {
		return fmt.Errorf("create worktree parent: %w", err)
	}
	result := r.runner.Run(ctx, processrun.Command{Name: "git", Args: []string{"worktree", "add", "--detach", destination, sha}, Dir: r.Root, Timeout: 2 * time.Minute})
	if result.Err != nil {
		return fmt.Errorf("create worktree: %w", result.Err)
	}
	if result.ExitCode != 0 {
		return fmt.Errorf("create worktree exited %d: %s", result.ExitCode, strings.TrimSpace(string(result.Stderr)))
	}
	return nil
}

func (r *Repository) RemoveWorktree(ctx context.Context, destination string) error {
	if destination == "" || !filepath.IsAbs(destination) {
		return fmt.Errorf("worktree destination must be an absolute path")
	}
	result := r.runner.Run(ctx, processrun.Command{Name: "git", Args: []string{"worktree", "remove", "--force", destination}, Dir: r.Root, Timeout: time.Minute})
	if result.Err != nil {
		return fmt.Errorf("remove worktree %q: %w", destination, result.Err)
	}
	if result.ExitCode != 0 {
		return fmt.Errorf("remove worktree %q exited %d: %s", destination, result.ExitCode, strings.TrimSpace(string(result.Stderr)))
	}
	return nil
}
