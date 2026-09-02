package process

import (
	"runtime"
	"strings"
	"testing"
)

func TestMinimalEnvironmentReservedValuesWin(t *testing.T) {
	values := MinimalEnvironment(nil, map[string]string{"BACKLINE_RUN_ID": "configured"}, map[string]string{"BACKLINE_RUN_ID": "reserved"})
	if !containsEnvironment(values, "BACKLINE_RUN_ID=reserved") {
		t.Fatalf("environment = %#v", values)
	}
}

func TestMinimalEnvironmentHandlesWindowsNamesCaseInsensitively(t *testing.T) {
	if runtime.GOOS != "windows" {
		t.Skip("Windows environment semantics")
	}
	values := MinimalEnvironment(nil, map[string]string{"path": "configured-path"}, nil)
	count := 0
	for _, value := range values {
		if strings.EqualFold(strings.SplitN(value, "=", 2)[0], "PATH") {
			count++
			if value != "path=configured-path" {
				t.Fatalf("PATH value = %q", value)
			}
		}
	}
	if count != 1 {
		t.Fatalf("PATH count = %d in %#v", count, values)
	}
}

func containsEnvironment(values []string, expected string) bool {
	for _, value := range values {
		if value == expected {
			return true
		}
	}
	return false
}
