package redact

import (
	"regexp"
	"sort"
	"strings"
	"sync"
)

const Marker = "[REDACTED]"

var commonPatterns = []*regexp.Regexp{
	regexp.MustCompile(`(?i)(authorization\s*[:=]\s*)([^\s,;]+(?:\s+[^\s,;]+)?)`),
	regexp.MustCompile(`(?i)(bearer\s+)[A-Za-z0-9._~+\-/]+=*`),
	regexp.MustCompile(`(?i)((?:password|passwd|token|api[_-]?key|secret|private[_-]?key)\s*[:=]\s*)[^\s,;]+`),
	regexp.MustCompile(`(?i)([a-z][a-z0-9+.-]*://[^:/\s]+:)[^@/\s]+@`),
	regexp.MustCompile(`(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----`),
}

type Redactor struct {
	mu     sync.RWMutex
	values []string
}

func New(values ...string) *Redactor {
	redactor := &Redactor{}
	redactor.Register(values...)
	return redactor
}

func (r *Redactor) Register(values ...string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	seen := make(map[string]struct{}, len(r.values)+len(values))
	for _, value := range r.values {
		seen[value] = struct{}{}
	}
	for _, value := range values {
		if value == "" {
			continue
		}
		if _, exists := seen[value]; exists {
			continue
		}
		seen[value] = struct{}{}
		r.values = append(r.values, value)
	}
	sort.SliceStable(r.values, func(left, right int) bool {
		return len(r.values[left]) > len(r.values[right])
	})
}

func (r *Redactor) String(value string) string {
	r.mu.RLock()
	registered := append([]string(nil), r.values...)
	r.mu.RUnlock()
	for _, secret := range registered {
		value = replaceExact(value, secret)
	}
	for _, pattern := range commonPatterns {
		value = pattern.ReplaceAllStringFunc(value, func(match string) string {
			indices := pattern.FindStringSubmatchIndex(match)
			if len(indices) >= 4 && indices[2] >= 0 {
				return match[:indices[3]] + Marker
			}
			return Marker
		})
	}
	return value
}

func replaceExact(value, secret string) string {
	if len(secret) >= 4 {
		return strings.ReplaceAll(value, secret, Marker)
	}
	var output strings.Builder
	searchFrom := 0
	writeFrom := 0
	for {
		relativeIndex := strings.Index(value[searchFrom:], secret)
		if relativeIndex < 0 {
			output.WriteString(value[writeFrom:])
			return output.String()
		}
		index := searchFrom + relativeIndex
		end := index + len(secret)
		leftBoundary := index == 0 || !tokenByte(value[index-1])
		rightBoundary := end == len(value) || !tokenByte(value[end])
		if leftBoundary && rightBoundary {
			output.WriteString(value[writeFrom:index])
			output.WriteString(Marker)
			writeFrom = end
		}
		searchFrom = end
	}
}

func tokenByte(value byte) bool {
	return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z' || value >= '0' && value <= '9' || value == '_'
}

func (r *Redactor) Bytes(value []byte) []byte {
	return []byte(r.String(string(value)))
}

func (r *Redactor) MaxRegisteredLength() int {
	r.mu.RLock()
	defer r.mu.RUnlock()
	if len(r.values) == 0 {
		return 0
	}
	return len(r.values[0])
}
