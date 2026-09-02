package process

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
	"sync"
	"time"
)

const defaultOutputLimit = 4 * 1024 * 1024

type Command struct {
	Name     string
	Args     []string
	Dir      string
	Env      []string
	Timeout  time.Duration
	MaxBytes int64
}

type Result struct {
	Launched             bool
	ExitCode             int
	TimedOut             bool
	Canceled             bool
	Stdout               []byte
	Stderr               []byte
	Truncated            bool
	Duration             time.Duration
	Signal               string
	TerminationAttempted bool
	TerminationSucceeded bool
	Err                  error
}

type Runner struct{}

func NewRunner() *Runner {
	return &Runner{}
}

func (r *Runner) Run(ctx context.Context, command Command) Result {
	started := time.Now()
	if err := ctx.Err(); err != nil {
		return Result{ExitCode: -1, Canceled: true, Duration: time.Since(started), Err: err}
	}
	if command.Name == "" {
		return Result{ExitCode: -1, Duration: time.Since(started), Err: errors.New("command name is empty")}
	}
	if command.Timeout <= 0 {
		command.Timeout = 30 * time.Second
	}
	if command.MaxBytes <= 0 {
		command.MaxBytes = defaultOutputLimit
	}

	commandContext, cancel := context.WithTimeout(ctx, command.Timeout)
	defer cancel()
	cmd := exec.Command(command.Name, command.Args...)
	cmd.Dir = command.Dir
	if command.Env == nil {
		cmd.Env = os.Environ()
	} else {
		cmd.Env = command.Env
	}
	prepareCommand(cmd)
	stdout := newLimitedBuffer(command.MaxBytes)
	stderr := newLimitedBuffer(command.MaxBytes)
	cmd.Stdout = stdout
	cmd.Stderr = stderr
	if err := cmd.Start(); err != nil {
		return Result{ExitCode: -1, Duration: time.Since(started), Err: fmt.Errorf("launch %s: %w", command.Name, err)}
	}
	tree, treeErr := attachProcessTree(cmd.Process)
	if treeErr != nil {
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		return Result{Launched: true, ExitCode: -1, Duration: time.Since(started), Err: fmt.Errorf("control process tree for %s: %w", command.Name, treeErr)}
	}
	defer tree.Close()

	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	var waitErr error
	timedOut := false
	canceled := false
	terminationAttempted := false
	terminationSucceeded := false
	var terminationErr error
	select {
	case waitErr = <-done:
	case <-commandContext.Done():
		timedOut = errors.Is(commandContext.Err(), context.DeadlineExceeded)
		canceled = errors.Is(commandContext.Err(), context.Canceled)
		terminationAttempted = true
		terminationErr = tree.Terminate(2 * time.Second)
		terminationSucceeded = terminationErr == nil
		waitErr = <-done
	}

	exitCode := 0
	if cmd.ProcessState != nil {
		exitCode = cmd.ProcessState.ExitCode()
	}
	var operationalErr error
	if waitErr != nil {
		var exitError *exec.ExitError
		if !errors.As(waitErr, &exitError) && !timedOut && !canceled {
			operationalErr = fmt.Errorf("wait for %s: %w", command.Name, waitErr)
		}
	}
	if terminationErr != nil {
		operationalErr = fmt.Errorf("terminate process tree for %s: %w", command.Name, terminationErr)
	}
	return Result{
		Launched:             true,
		ExitCode:             exitCode,
		TimedOut:             timedOut,
		Canceled:             canceled,
		Stdout:               stdout.Bytes(),
		Stderr:               stderr.Bytes(),
		Truncated:            stdout.Truncated() || stderr.Truncated(),
		Duration:             time.Since(started),
		Signal:               processSignal(cmd.ProcessState),
		TerminationAttempted: terminationAttempted,
		TerminationSucceeded: terminationSucceeded,
		Err:                  operationalErr,
	}
}

func (r *Runner) Output(ctx context.Context, dir, name string, args ...string) ([]byte, error) {
	result := r.Run(ctx, Command{Name: name, Args: args, Dir: dir, Timeout: 2 * time.Minute})
	if result.Err != nil {
		return nil, result.Err
	}
	if !result.Launched || result.ExitCode != 0 {
		message := strings.TrimSpace(string(result.Stderr))
		if message == "" {
			message = strings.TrimSpace(string(result.Stdout))
		}
		return nil, fmt.Errorf("%s exited %d: %s", name, result.ExitCode, message)
	}
	return result.Stdout, nil
}

type limitedBuffer struct {
	mu        sync.Mutex
	buffer    bytes.Buffer
	remaining int64
	truncated bool
}

func newLimitedBuffer(limit int64) *limitedBuffer {
	return &limitedBuffer{remaining: limit}
}

func (b *limitedBuffer) Write(data []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	original := len(data)
	if int64(len(data)) > b.remaining {
		data = data[:max(0, int(b.remaining))]
		b.truncated = true
	}
	if len(data) > 0 {
		_, _ = b.buffer.Write(data)
		b.remaining -= int64(len(data))
	}
	return original, nil
}

func (b *limitedBuffer) Bytes() []byte {
	b.mu.Lock()
	defer b.mu.Unlock()
	return bytes.Clone(b.buffer.Bytes())
}

func (b *limitedBuffer) Truncated() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.truncated
}

var _ io.Writer = (*limitedBuffer)(nil)
