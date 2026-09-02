//go:build windows

package process

import "os"

func processSignal(*os.ProcessState) string { return "" }
