package process

import (
	"os"
	"runtime"
	"sort"
	"strings"
)

func MinimalEnvironment(passthrough []string, configured, reserved map[string]string) []string {
	canonical := func(name string) string {
		if runtime.GOOS == "windows" {
			return strings.ToUpper(name)
		}
		return name
	}
	allowed := map[string]struct{}{}
	for _, name := range []string{"PATH", "HOME", "TMP", "TEMP", "TMPDIR"} {
		allowed[canonical(name)] = struct{}{}
	}
	if runtime.GOOS == "windows" {
		for _, name := range []string{"Path", "PATHEXT", "SystemRoot", "WINDIR", "ComSpec", "USERPROFILE"} {
			allowed[canonical(name)] = struct{}{}
		}
	}
	for _, name := range passthrough {
		allowed[canonical(name)] = struct{}{}
	}
	type environmentValue struct{ name, value string }
	values := make(map[string]environmentValue)
	put := func(name, value string) { values[canonical(name)] = environmentValue{name: name, value: value} }
	for _, entry := range os.Environ() {
		name, value, ok := strings.Cut(entry, "=")
		if !ok {
			continue
		}
		if _, include := allowed[canonical(name)]; include {
			put(name, value)
		}
	}
	for name, value := range configured {
		put(name, value)
	}
	for name, value := range reserved {
		put(name, value)
	}
	names := make([]string, 0, len(values))
	for _, value := range values {
		names = append(names, value.name)
	}
	sort.Strings(names)
	result := make([]string, 0, len(names))
	for _, name := range names {
		value := values[canonical(name)]
		result = append(result, value.name+"="+value.value)
	}
	return result
}
