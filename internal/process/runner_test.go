package process

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"testing"
	"time"
)

func TestRunnerCapturesExitAndTruncation(t *testing.T) {
	t.Setenv("GO_WANT_PROCESS_HELPER", "1")
	name, args := helperCommand("output", "64")
	result := NewRunner().Run(context.Background(), Command{Name: name, Args: args, MaxBytes: 8, Timeout: 5 * time.Second})
	if !result.Launched || result.ExitCode != 0 || !result.Truncated || len(result.Stdout) != 8 {
		t.Fatalf("result = %#v", result)
	}
}

func TestRunnerTimesOut(t *testing.T) {
	t.Setenv("GO_WANT_PROCESS_HELPER", "1")
	name, args := helperCommand("sleep", "10s")
	result := NewRunner().Run(context.Background(), Command{Name: name, Args: args, Timeout: 50 * time.Millisecond})
	if !result.Launched || !result.TimedOut || result.ExitCode == 0 || !result.TerminationAttempted || !result.TerminationSucceeded {
		t.Fatalf("result = %#v", result)
	}
}

func TestRunnerTerminatesChildProcessTree(t *testing.T) {
	t.Setenv("GO_WANT_PROCESS_HELPER", "1")
	marker := filepath.Join(t.TempDir(), "child-survived")
	name, args := helperCommand("spawn-child", marker)
	result := NewRunner().Run(context.Background(), Command{Name: name, Args: args, Timeout: 350 * time.Millisecond})
	if !result.TimedOut {
		t.Fatalf("result = %#v", result)
	}
	time.Sleep(900 * time.Millisecond)
	if _, err := os.Stat(marker); !os.IsNotExist(err) {
		t.Fatalf("child process survived timeout: %v", err)
	}
}

func TestRunnerHonorsParentCancellation(t *testing.T) {
	t.Setenv("GO_WANT_PROCESS_HELPER", "1")
	name, args := helperCommand("sleep", "10s")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	result := NewRunner().Run(ctx, Command{Name: name, Args: args, Timeout: 5 * time.Second})
	if !result.Canceled || result.TimedOut || result.Launched {
		t.Fatalf("result = %#v", result)
	}
}

func TestProcessHelper(t *testing.T) {
	if os.Getenv("GO_WANT_PROCESS_HELPER") != "1" {
		return
	}
	mode := os.Args[len(os.Args)-2]
	value := os.Args[len(os.Args)-1]
	switch mode {
	case "output":
		for index := 0; index < 64; index++ {
			_, _ = os.Stdout.Write([]byte("x"))
		}
	case "sleep":
		duration, _ := time.ParseDuration(value)
		time.Sleep(duration)
	case "spawn-child":
		time.Sleep(100 * time.Millisecond)
		command := exec.Command(os.Args[0], "-test.run=TestProcessHelper", "--", "child-write", value)
		command.Env = os.Environ()
		_ = command.Start()
		time.Sleep(10 * time.Second)
	case "child-write":
		time.Sleep(700 * time.Millisecond)
		_ = os.WriteFile(value, []byte("survived"), 0o600)
	}
	os.Exit(0)
}

func helperCommand(mode, value string) (string, []string) {
	if runtime.GOOS == "windows" {
		return os.Args[0], []string{"-test.run=TestProcessHelper", "--", mode, value}
	}
	return os.Args[0], []string{"-test.run=TestProcessHelper", "--", mode, value}
}
