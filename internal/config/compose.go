package config

import (
	"encoding/json"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type composeDocument struct {
	Name     string                     `json:"name"`
	Services map[string]composeService  `json:"services"`
	Volumes  map[string]composeResource `json:"volumes"`
	Networks map[string]composeResource `json:"networks"`
	Configs  map[string]composeResource `json:"configs"`
	Secrets  map[string]composeResource `json:"secrets"`
}

type composeService struct {
	Build         any                        `json:"build"`
	ContainerName string                     `json:"container_name"`
	DependsOn     map[string]json.RawMessage `json:"depends_on"`
	Deploy        struct {
		Replicas int `json:"replicas"`
	} `json:"deploy"`
	Devices     []any          `json:"devices"`
	EnvFile     []string       `json:"env_file"`
	Healthcheck map[string]any `json:"healthcheck"`
	IPC         string         `json:"ipc"`
	NetworkMode string         `json:"network_mode"`
	PID         string         `json:"pid"`
	Ports       []any          `json:"ports"`
	Privileged  bool           `json:"privileged"`
	Restart     string         `json:"restart"`
	Volumes     []any          `json:"volumes"`
	Configs     []composeMount `json:"configs"`
	Secrets     []composeMount `json:"secrets"`
}

type composeResource struct {
	Name     string `json:"name"`
	External any    `json:"external"`
	File     string `json:"file"`
}

type composeMount struct {
	Source string `json:"source"`
	Target string `json:"target"`
}

func ValidateComposeJSON(data []byte, root string, selected []string, allowUnsafe bool) error {
	var document composeDocument
	if err := json.Unmarshal(data, &document); err != nil {
		return fmt.Errorf("decode normalized Compose configuration: %w", err)
	}
	selectedSet := make(map[string]struct{}, len(selected))
	for _, name := range selected {
		selectedSet[name] = struct{}{}
		service, exists := document.Services[name]
		if !exists {
			return fmt.Errorf("selected Compose service %q does not exist", name)
		}
		if service.Healthcheck == nil || disabledHealthcheck(service.Healthcheck) {
			return fmt.Errorf("selected Compose service %q must define an enabled health check", name)
		}
	}
	for _, name := range selected {
		service := document.Services[name]
		for dependency := range service.DependsOn {
			if _, listed := selectedSet[dependency]; !listed {
				return fmt.Errorf("selected service %q depends on unlisted service %q", name, dependency)
			}
		}
		if err := validateComposeService(name, service, allowUnsafe); err != nil {
			return err
		}
		if err := validateComposeServicePaths(name, service, root, allowUnsafe); err != nil {
			return err
		}
	}
	if err := validateComposeResources("volume", document.Name, document.Volumes, root, allowUnsafe); err != nil {
		return err
	}
	if err := validateComposeResources("network", document.Name, document.Networks, root, allowUnsafe); err != nil {
		return err
	}
	if err := validateComposeResources("config", document.Name, document.Configs, root, allowUnsafe); err != nil {
		return err
	}
	if err := validateComposeResources("secret", document.Name, document.Secrets, root, allowUnsafe); err != nil {
		return err
	}
	return nil
}

func disabledHealthcheck(healthcheck map[string]any) bool {
	disabled, _ := healthcheck["disable"].(bool)
	return disabled
}

func validateComposeService(name string, service composeService, allowUnsafe bool) error {
	if service.ContainerName != "" {
		return fmt.Errorf("selected service %q uses fixed container_name", name)
	}
	if service.Restart != "" && service.Restart != "no" {
		return fmt.Errorf("selected service %q uses restart policy %q", name, service.Restart)
	}
	if service.Deploy.Replicas > 1 {
		return fmt.Errorf("selected service %q uses %d replicas", name, service.Deploy.Replicas)
	}
	unsafe := make([]string, 0)
	if service.Privileged {
		unsafe = append(unsafe, "privileged")
	}
	if service.NetworkMode == "host" || service.PID == "host" || service.IPC == "host" {
		unsafe = append(unsafe, "host namespace")
	}
	if len(service.Devices) > 0 {
		unsafe = append(unsafe, "device mapping")
	}
	for _, raw := range service.Volumes {
		source := composeVolumeSource(raw)
		if source == "" {
			continue
		}
		if isBindPath(source) {
			unsafe = append(unsafe, "host bind mount")
		}
		if strings.Contains(strings.ToLower(source), "docker.sock") || strings.Contains(strings.ToLower(source), "docker_engine") {
			unsafe = append(unsafe, "Docker socket mount")
		}
	}
	for _, raw := range service.Ports {
		fixed, nonLoopback := inspectComposePort(raw)
		if fixed {
			unsafe = append(unsafe, "fixed published host port")
		}
		if nonLoopback {
			unsafe = append(unsafe, "non-loopback published port")
		}
	}
	if len(unsafe) > 0 && !allowUnsafe {
		return fmt.Errorf("selected service %q uses unsafe Compose feature: %s", name, strings.Join(uniqueNonempty(unsafe), ", "))
	}
	return nil
}

func validateComposeServicePaths(name string, service composeService, root string, allowUnsafe bool) error {
	paths := append([]string(nil), service.EnvFile...)
	switch build := service.Build.(type) {
	case string:
		paths = append(paths, build)
	case map[string]any:
		contextPath, _ := build["context"].(string)
		if contextPath != "" {
			paths = append(paths, contextPath)
		}
		if dockerfile, ok := build["dockerfile"].(string); ok && dockerfile != "" {
			if contextPath != "" && !filepath.IsAbs(dockerfile) {
				dockerfile = filepath.Join(contextPath, dockerfile)
			}
			paths = append(paths, dockerfile)
		}
	}
	for _, path := range paths {
		if _, err := ResolveWithin(root, path); err != nil && !allowUnsafe {
			return fmt.Errorf("selected service %q path %q is unsafe: %w", name, path, err)
		}
	}
	return nil
}

func validateComposeResources(kind, project string, resources map[string]composeResource, root string, allowUnsafe bool) error {
	for key, resource := range resources {
		generatedName := project + "_" + key
		external := isExternal(resource.External)
		if resource.Name != "" && resource.Name != generatedName && !(external && allowUnsafe) {
			return fmt.Errorf("Compose %s %q uses an explicit global name", kind, key)
		}
		if external && !allowUnsafe {
			return fmt.Errorf("Compose %s %q is external", kind, key)
		}
		if resource.File != "" {
			if _, err := ResolveWithin(root, resource.File); err != nil && !allowUnsafe {
				return fmt.Errorf("Compose %s %q file is unsafe: %w", kind, key, err)
			}
		}
	}
	return nil
}

func isExternal(value any) bool {
	switch typed := value.(type) {
	case bool:
		return typed
	case map[string]any:
		return len(typed) > 0
	default:
		return value != nil
	}
}

func composeVolumeSource(raw any) string {
	switch value := raw.(type) {
	case string:
		parts := strings.SplitN(value, ":", 2)
		if len(parts) == 2 {
			return parts[0]
		}
	case map[string]any:
		source, _ := value["source"].(string)
		typeName, _ := value["type"].(string)
		if typeName == "bind" {
			return source
		}
	}
	return ""
}

func isBindPath(source string) bool {
	return filepath.IsAbs(source) || strings.HasPrefix(source, ".") || (len(source) > 2 && source[1] == ':')
}

func inspectComposePort(raw any) (fixed bool, nonLoopback bool) {
	switch value := raw.(type) {
	case string:
		parts := strings.Split(value, ":")
		if len(parts) == 1 {
			return false, false
		}
		host := ""
		published := ""
		if len(parts) == 2 {
			published = parts[0]
		} else {
			host, published = strings.Join(parts[:len(parts)-2], ":"), parts[len(parts)-2]
		}
		return published != "" && published != "0", !isLoopbackOrEmpty(host)
	case map[string]any:
		published := fmt.Sprint(value["published"])
		if published == "<nil>" {
			published = ""
		}
		host, _ := value["host_ip"].(string)
		return published != "" && published != "0", !isLoopbackOrEmpty(host)
	default:
		return false, false
	}
}

func isLoopbackOrEmpty(host string) bool {
	host = strings.Trim(host, "[]")
	if host == "" || host == "localhost" {
		return true
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

func ResolveWithin(root, candidate string) (string, error) {
	if root == "" {
		return "", fmt.Errorf("root is empty")
	}
	if !filepath.IsAbs(candidate) {
		candidate = filepath.Join(root, candidate)
	}
	rootAbs, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	candidateAbs, err := filepath.Abs(candidate)
	if err != nil {
		return "", err
	}
	resolvedRoot, err := filepath.EvalSymlinks(rootAbs)
	if err != nil {
		return "", err
	}
	resolvedCandidate, err := filepath.EvalSymlinks(candidateAbs)
	if err != nil {
		return "", err
	}
	relative, err := filepath.Rel(resolvedRoot, resolvedCandidate)
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) || filepath.IsAbs(relative) {
		return "", fmt.Errorf("path %q escapes %q", candidate, root)
	}
	return resolvedCandidate, nil
}

// ResolveDestinationWithin confines a repository-relative destination even when
// its final directories do not exist yet by resolving the nearest existing ancestor.
func ResolveDestinationWithin(root, candidate string) (string, error) {
	cleaned, err := CleanRepositoryPath(candidate)
	if err != nil {
		return "", err
	}
	rootAbs, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	resolvedRoot, err := filepath.EvalSymlinks(rootAbs)
	if err != nil {
		return "", err
	}
	destination := filepath.Join(rootAbs, filepath.FromSlash(cleaned))
	ancestor := destination
	for {
		if _, err := os.Lstat(ancestor); err == nil {
			break
		} else if !os.IsNotExist(err) {
			return "", err
		}
		parent := filepath.Dir(ancestor)
		if parent == ancestor {
			return "", fmt.Errorf("destination %q has no existing ancestor", destination)
		}
		ancestor = parent
	}
	resolvedAncestor, err := filepath.EvalSymlinks(ancestor)
	if err != nil {
		return "", err
	}
	suffix, err := filepath.Rel(ancestor, destination)
	if err != nil {
		return "", err
	}
	resolvedDestination := filepath.Join(resolvedAncestor, suffix)
	relative, err := filepath.Rel(resolvedRoot, resolvedDestination)
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) || filepath.IsAbs(relative) {
		return "", fmt.Errorf("path %q escapes %q", candidate, root)
	}
	return filepath.Clean(resolvedDestination), nil
}

func CleanRepositoryPath(path string) (string, error) {
	if path == "" || filepath.IsAbs(path) || filepath.VolumeName(path) != "" {
		return "", fmt.Errorf("path must be repository-relative")
	}
	normalized := filepath.ToSlash(filepath.Clean(path))
	if normalized == ".." || strings.HasPrefix(normalized, "../") {
		return "", fmt.Errorf("path escapes repository")
	}
	return normalized, nil
}

func strconvInt(value any) int {
	switch typed := value.(type) {
	case float64:
		return int(typed)
	case string:
		parsed, _ := strconv.Atoi(typed)
		return parsed
	default:
		return 0
	}
}
