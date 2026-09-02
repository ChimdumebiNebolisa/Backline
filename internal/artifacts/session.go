package artifacts

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/ChimdumebiNebolisa/Backline/internal/model"
	"github.com/ChimdumebiNebolisa/Backline/internal/redact"
	"gopkg.in/yaml.v3"
)

type Event struct {
	Timestamp time.Time      `json:"timestamp"`
	Type      string         `json:"type"`
	Stage     string         `json:"stage,omitempty"`
	RunID     string         `json:"run_id"`
	Details   map[string]any `json:"details"`
}

type Session struct {
	RunID       string
	Root        string
	maxLogBytes int64
	redactor    *redact.Redactor
	events      *os.File
	mu          sync.Mutex
}

func NewSession(artifactRoot, runID string, maxLogBytes int64, redactor *redact.Redactor) (*Session, error) {
	if runID == "" {
		generated, err := NewRunID(time.Now().UTC())
		if err != nil {
			return nil, err
		}
		runID = generated
	}
	if !strings.HasPrefix(runID, "bl-") || strings.ContainsAny(runID, `/\`) {
		return nil, fmt.Errorf("invalid run ID %q", runID)
	}
	if redactor == nil {
		redactor = redact.New()
	}
	if err := os.MkdirAll(artifactRoot, 0o700); err != nil {
		return nil, fmt.Errorf("create artifact root: %w", err)
	}
	runRoot := filepath.Join(artifactRoot, runID)
	if err := os.Mkdir(runRoot, 0o700); err != nil {
		return nil, fmt.Errorf("create unique run directory: %w", err)
	}
	setupFailed := true
	defer func() {
		if setupFailed {
			_ = os.RemoveAll(runRoot)
		}
	}()
	if err := os.Mkdir(filepath.Join(runRoot, "logs"), 0o700); err != nil {
		return nil, fmt.Errorf("create artifact logs directory: %w", err)
	}
	if err := os.Mkdir(filepath.Join(runRoot, "builds"), 0o700); err != nil {
		return nil, fmt.Errorf("create artifact builds directory: %w", err)
	}
	events, err := os.OpenFile(filepath.Join(runRoot, "events.jsonl"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return nil, fmt.Errorf("create events artifact: %w", err)
	}
	setupFailed = false
	return &Session{RunID: runID, Root: runRoot, maxLogBytes: maxLogBytes, redactor: redactor, events: events}, nil
}

func NewRunID(now time.Time) (string, error) {
	random := make([]byte, 8)
	if _, err := rand.Read(random); err != nil {
		return "", fmt.Errorf("generate run ID: %w", err)
	}
	return fmt.Sprintf("bl-%s-%s", now.UTC().Format("20060102-150405"), hex.EncodeToString(random)), nil
}

func (s *Session) Emit(eventType, stage string, details map[string]any) error {
	if eventType == "" {
		return errors.New("event type is empty")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.events == nil {
		return errors.New("event artifact is closed")
	}
	if details == nil {
		details = map[string]any{}
	}
	event := Event{Timestamp: time.Now().UTC(), Type: eventType, Stage: stage, RunID: s.RunID, Details: details}
	encoded, err := s.redactedJSON(event, false)
	if err != nil {
		return fmt.Errorf("encode event: %w", err)
	}
	if _, err := s.events.Write(append(encoded, '\n')); err != nil {
		return fmt.Errorf("write event: %w", err)
	}
	return s.events.Sync()
}

func (s *Session) WriteLog(relativePath string, data []byte) (string, bool, error) {
	return s.writeLog(relativePath, s.redactor.Bytes(data))
}

func (s *Session) WriteJSONLog(relativePath string, value any) (string, bool, error) {
	encoded, err := s.redactedJSON(value, true)
	if err != nil {
		return "", false, err
	}
	return s.writeLog(relativePath, append(encoded, '\n'))
}

func (s *Session) writeLog(relativePath string, data []byte) (string, bool, error) {
	cleaned, err := safeRelative(relativePath)
	if err != nil {
		return "", false, err
	}
	truncated := false
	if int64(len(data)) > s.maxLogBytes {
		data = data[:s.maxLogBytes]
		truncated = true
	}
	if truncated {
		data = append(data, []byte("\n[TRUNCATED]\n")...)
	}
	destination := filepath.Join(s.Root, cleaned)
	if err := os.MkdirAll(filepath.Dir(destination), 0o700); err != nil {
		return "", false, err
	}
	if err := atomicWrite(destination, data, 0o600); err != nil {
		return "", false, err
	}
	return destination, truncated, nil
}

func (s *Session) WriteSummary(summary model.Summary) error {
	encoded, err := s.redactedJSON(summary, true)
	if err != nil {
		return fmt.Errorf("encode summary: %w", err)
	}
	encoded = append(encoded, '\n')
	return atomicWrite(filepath.Join(s.Root, "summary.json"), encoded, 0o600)
}

func (s *Session) WriteYAML(name string, contents []byte) error {
	cleaned, err := safeRelative(name)
	if err != nil {
		return err
	}
	var document yaml.Node
	if err := yaml.Unmarshal(contents, &document); err != nil {
		return fmt.Errorf("parse YAML artifact: %w", err)
	}
	redactYAMLNode(&document, s.redactor)
	encoded, err := yaml.Marshal(&document)
	if err != nil {
		return fmt.Errorf("encode YAML artifact: %w", err)
	}
	return atomicWrite(filepath.Join(s.Root, cleaned), encoded, 0o600)
}

func (s *Session) WriteText(name, contents string) error {
	cleaned, err := safeRelative(name)
	if err != nil {
		return err
	}
	return atomicWrite(filepath.Join(s.Root, cleaned), s.redactor.Bytes([]byte(contents)), 0o600)
}

func (s *Session) CopySummary(destination string) error {
	contents, err := os.ReadFile(filepath.Join(s.Root, "summary.json"))
	if err != nil {
		return err
	}
	if parent := filepath.Dir(destination); parent != "." {
		if err := os.MkdirAll(parent, 0o700); err != nil {
			return err
		}
	}
	return atomicWrite(destination, contents, 0o600)
}

func (s *Session) WriteExternal(destination string, contents []byte) error {
	if destination == "" {
		return errors.New("external artifact path is empty")
	}
	if parent := filepath.Dir(destination); parent != "." {
		if err := os.MkdirAll(parent, 0o700); err != nil {
			return err
		}
	}
	return atomicWrite(destination, s.redactor.Bytes(contents), 0o600)
}

func (s *Session) AppendExternal(destination string, contents []byte) error {
	if destination == "" {
		return errors.New("external artifact path is empty")
	}
	file, err := os.OpenFile(destination, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	defer file.Close()
	if _, err := file.Write(s.redactor.Bytes(contents)); err != nil {
		return err
	}
	return file.Sync()
}

func (s *Session) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.events == nil {
		return nil
	}
	err := s.events.Close()
	s.events = nil
	return err
}

func safeRelative(path string) (string, error) {
	if path == "" || filepath.IsAbs(path) || filepath.VolumeName(path) != "" {
		return "", errors.New("artifact path must be relative")
	}
	cleaned := filepath.Clean(path)
	if cleaned == ".." || strings.HasPrefix(cleaned, ".."+string(filepath.Separator)) {
		return "", errors.New("artifact path escapes the run directory")
	}
	return cleaned, nil
}

func atomicWrite(destination string, data []byte, mode os.FileMode) error {
	temporary, err := os.CreateTemp(filepath.Dir(destination), ".backline-write-")
	if err != nil {
		return err
	}
	temporaryName := temporary.Name()
	defer os.Remove(temporaryName)
	if err := temporary.Chmod(mode); err != nil {
		_ = temporary.Close()
		return err
	}
	if _, err := temporary.Write(data); err != nil {
		_ = temporary.Close()
		return err
	}
	if err := temporary.Sync(); err != nil {
		_ = temporary.Close()
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	if err := os.Rename(temporaryName, destination); err != nil {
		if removeErr := os.Remove(destination); removeErr != nil && !os.IsNotExist(removeErr) {
			return errors.Join(err, removeErr)
		}
		return os.Rename(temporaryName, destination)
	}
	return nil
}

func (s *Session) FinalizeContext(ctx context.Context) error {
	select {
	case <-ctx.Done():
		return ctx.Err()
	default:
		return s.Close()
	}
}

func (s *Session) redactedJSON(value any, indent bool) ([]byte, error) {
	encoded, err := json.Marshal(value)
	if err != nil {
		return nil, err
	}
	decoder := json.NewDecoder(strings.NewReader(string(encoded)))
	decoder.UseNumber()
	var document any
	if err := decoder.Decode(&document); err != nil {
		return nil, err
	}
	document = redactJSONValue(document, s.redactor)
	if indent {
		return json.MarshalIndent(document, "", "  ")
	}
	return json.Marshal(document)
}

func redactJSONValue(value any, redactor *redact.Redactor) any {
	switch typed := value.(type) {
	case string:
		return redactor.String(typed)
	case []any:
		for index := range typed {
			typed[index] = redactJSONValue(typed[index], redactor)
		}
	case map[string]any:
		for key := range typed {
			typed[key] = redactJSONValue(typed[key], redactor)
		}
	}
	return value
}

func redactYAMLNode(node *yaml.Node, redactor *redact.Redactor) {
	if node.Kind == yaml.MappingNode {
		for index := 0; index+1 < len(node.Content); index += 2 {
			key, value := node.Content[index], node.Content[index+1]
			if sensitiveKey(key.Value) && value.Kind == yaml.ScalarNode && value.Tag == "!!str" && value.Value != "" {
				value.Value = redact.Marker
			} else {
				redactYAMLNode(value, redactor)
			}
		}
		return
	}
	if node.Kind == yaml.ScalarNode && node.Tag == "!!str" {
		node.Value = redactor.String(node.Value)
	}
	for _, child := range node.Content {
		redactYAMLNode(child, redactor)
	}
}

func sensitiveKey(value string) bool {
	normalized := strings.ToLower(strings.ReplaceAll(strings.ReplaceAll(value, "-", "_"), " ", "_"))
	for _, marker := range []string{"authorization", "password", "passwd", "token", "api_key", "secret", "private_key"} {
		if strings.Contains(normalized, marker) {
			return true
		}
	}
	return false
}
