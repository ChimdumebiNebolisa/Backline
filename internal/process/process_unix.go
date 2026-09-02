//go:build !windows

package process

import (
	"errors"
	"os"
	"os/exec"
	"syscall"
	"time"
)

type platformProcessTree struct {
	pid int
}

func prepareCommand(command *exec.Cmd) {
	command.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
}

func attachProcessTree(process *os.Process) (*platformProcessTree, error) {
	return &platformProcessTree{pid: process.Pid}, nil
}

func (p *platformProcessTree) Terminate(grace time.Duration) error {
	if err := syscall.Kill(-p.pid, syscall.SIGTERM); err != nil && !errors.Is(err, syscall.ESRCH) {
		return err
	}
	time.Sleep(grace)
	if err := syscall.Kill(-p.pid, syscall.SIGKILL); err != nil && !errors.Is(err, syscall.ESRCH) {
		return err
	}
	return nil
}

func (p *platformProcessTree) Close() error { return nil }
