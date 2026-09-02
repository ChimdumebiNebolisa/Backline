package config

import (
	"bufio"
	"bytes"
	"fmt"
	"strings"
)

func ParseEnvironmentFile(data []byte) (map[string]string, []string, error) {
	values := make(map[string]string)
	registered := make([]string, 0)
	scanner := bufio.NewScanner(bytes.NewReader(data))
	lineNumber := 0
	for scanner.Scan() {
		lineNumber++
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if strings.HasPrefix(line, "export ") {
			return nil, nil, fmt.Errorf("environment file line %d: export syntax is not supported", lineNumber)
		}
		name, value, ok := strings.Cut(line, "=")
		name = strings.TrimSpace(name)
		if !ok || !envPattern.MatchString(name) {
			return nil, nil, fmt.Errorf("environment file line %d is invalid", lineNumber)
		}
		value = strings.TrimSpace(value)
		if len(value) >= 2 && ((value[0] == '"' && value[len(value)-1] == '"') || (value[0] == '\'' && value[len(value)-1] == '\'')) {
			value = value[1 : len(value)-1]
		}
		values[name] = value
		if value != "" {
			registered = append(registered, value)
		}
	}
	if err := scanner.Err(); err != nil {
		return nil, nil, fmt.Errorf("read environment file: %w", err)
	}
	return values, uniqueNonempty(registered), nil
}

func MergeEnvironment(fileValues, processValues map[string]string) map[string]string {
	merged := make(map[string]string, len(fileValues)+len(processValues))
	for name, value := range fileValues {
		merged[name] = value
	}
	for name, value := range processValues {
		merged[name] = value
	}
	return merged
}
