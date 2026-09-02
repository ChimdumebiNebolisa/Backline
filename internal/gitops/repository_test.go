package gitops

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	processrun "github.com/ChimdumebiNebolisa/Backline/internal/process"
)

func TestRepositoryReadsCommittedConfigAndManagesWorktree(t *testing.T) {
	root := initializeRepository(t)
	nested := filepath.Join(root, "nested")
	if err := os.MkdirAll(nested, 0o755); err != nil {
		t.Fatal(err)
	}
	repository, err := Discover(context.Background(), nested, processrun.NewRunner())
	if err != nil {
		t.Fatal(err)
	}
	sha, err := repository.Resolve(context.Background(), "HEAD")
	if err != nil {
		t.Fatal(err)
	}
	contents, path, err := repository.ReadCommittedFile(context.Background(), sha, "backline.yml")
	if err != nil {
		t.Fatal(err)
	}
	if path != "backline.yml" || !strings.Contains(string(contents), "version: 1") {
		t.Fatalf("path=%q contents=%q", path, contents)
	}
	worktree := filepath.Join(t.TempDir(), "candidate")
	if err := repository.CreateWorktree(context.Background(), sha, worktree); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(worktree, "backline.yml")); err != nil {
		t.Fatal(err)
	}
	if err := repository.RemoveWorktree(context.Background(), worktree); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(worktree); !os.IsNotExist(err) {
		t.Fatalf("worktree still exists: %v", err)
	}
}

func TestTrackedDirtyIgnoresUntrackedFiles(t *testing.T) {
	root := initializeRepository(t)
	repository, err := Discover(context.Background(), root, processrun.NewRunner())
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "untracked.txt"), []byte("ignored"), 0o644); err != nil {
		t.Fatal(err)
	}
	dirty, err := repository.TrackedDirty(context.Background())
	if err != nil || dirty {
		t.Fatalf("dirty=%v err=%v", dirty, err)
	}
	if err := os.WriteFile(filepath.Join(root, "backline.yml"), []byte("changed"), 0o644); err != nil {
		t.Fatal(err)
	}
	dirty, err = repository.TrackedDirty(context.Background())
	if err != nil || !dirty {
		t.Fatalf("dirty=%v err=%v", dirty, err)
	}
}

func TestReadCommittedFileUsesRequestedNonHeadRevision(t *testing.T) {
	root := initializeRepository(t)
	if err := os.WriteFile(filepath.Join(root, "backline.yml"), []byte("version: 2\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	runGit(t, root, "add", "backline.yml")
	runGit(t, root, "commit", "-m", "candidate")
	repository, err := Discover(context.Background(), root, processrun.NewRunner())
	if err != nil {
		t.Fatal(err)
	}
	base, err := repository.Resolve(context.Background(), "HEAD^")
	if err != nil {
		t.Fatal(err)
	}
	contents, _, err := repository.ReadCommittedFile(context.Background(), base, "backline.yml")
	if err != nil || string(contents) != "version: 1\n" {
		t.Fatalf("contents=%q err=%v", contents, err)
	}
}

func TestReadCommittedFileRejectsSymlinkEscape(t *testing.T) {
	root := initializeRepository(t)
	target := filepath.Join(root, "target.txt")
	if err := os.WriteFile(target, []byte("../outside.yml\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	fields := strings.Fields(gitOutput(t, root, "hash-object", "-w", "target.txt"))
	blob := fields[len(fields)-1]
	runGit(t, root, "update-index", "--add", "--cacheinfo", "120000,"+blob+",escaped.yml")
	runGit(t, root, "commit", "-m", "add escaping symlink")
	repository, err := Discover(context.Background(), root, processrun.NewRunner())
	if err != nil {
		t.Fatal(err)
	}
	sha, _ := repository.Resolve(context.Background(), "HEAD")
	if _, _, err := repository.ReadCommittedFile(context.Background(), sha, "escaped.yml"); err == nil || !strings.Contains(err.Error(), "escapes") {
		t.Fatalf("error = %v", err)
	}
}

func TestReadCommittedFileResolvesIntermediateInternalSymlink(t *testing.T) {
	root := initializeRepository(t)
	if err := os.MkdirAll(filepath.Join(root, "config"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "config", "nested.yml"), []byte("version: 1\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	runGit(t, root, "add", "config/nested.yml")
	if err := os.WriteFile(filepath.Join(root, "link-target.txt"), []byte("config\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	fields := strings.Fields(gitOutput(t, root, "hash-object", "-w", "link-target.txt"))
	blob := fields[len(fields)-1]
	runGit(t, root, "update-index", "--add", "--cacheinfo", "120000,"+blob+",config-link")
	runGit(t, root, "commit", "-m", "add internal config symlink")
	repository, err := Discover(context.Background(), root, processrun.NewRunner())
	if err != nil {
		t.Fatal(err)
	}
	sha, _ := repository.Resolve(context.Background(), "HEAD")
	contents, resolved, err := repository.ReadCommittedFile(context.Background(), sha, "config-link/nested.yml")
	if err != nil || resolved != "config/nested.yml" || string(contents) != "version: 1\n" {
		t.Fatalf("resolved=%q contents=%q err=%v", resolved, contents, err)
	}
}

func TestReadCommittedFileRejectsIntermediateSymlinkEscape(t *testing.T) {
	root := initializeRepository(t)
	if err := os.WriteFile(filepath.Join(root, "link-target.txt"), []byte("../outside\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	fields := strings.Fields(gitOutput(t, root, "hash-object", "-w", "link-target.txt"))
	blob := fields[len(fields)-1]
	runGit(t, root, "update-index", "--add", "--cacheinfo", "120000,"+blob+",escaped")
	runGit(t, root, "commit", "-m", "add escaping directory symlink")
	repository, err := Discover(context.Background(), root, processrun.NewRunner())
	if err != nil {
		t.Fatal(err)
	}
	sha, _ := repository.Resolve(context.Background(), "HEAD")
	if _, _, err := repository.ReadCommittedFile(context.Background(), sha, "escaped/backline.yml"); err == nil || !strings.Contains(err.Error(), "escapes") {
		t.Fatalf("error = %v", err)
	}
}

func initializeRepository(t *testing.T) string {
	t.Helper()
	root := t.TempDir()
	runGit(t, root, "init")
	runGit(t, root, "config", "user.email", "backline-tests@example.invalid")
	runGit(t, root, "config", "user.name", "Backline Tests")
	if err := os.WriteFile(filepath.Join(root, "backline.yml"), []byte("version: 1\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	runGit(t, root, "add", "backline.yml")
	runGit(t, root, "commit", "-m", "fixture")
	return root
}

func runGit(t *testing.T, dir string, args ...string) {
	t.Helper()
	command := exec.Command("git", args...)
	command.Dir = dir
	if output, err := command.CombinedOutput(); err != nil {
		t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, output)
	}
}

func gitOutput(t *testing.T, dir string, args ...string) string {
	t.Helper()
	command := exec.Command("git", args...)
	command.Dir = dir
	output, err := command.CombinedOutput()
	if err != nil {
		t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, output)
	}
	return string(output)
}
