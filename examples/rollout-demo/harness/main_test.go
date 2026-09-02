package main

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/ChimdumebiNebolisa/Backline/internal/config"
)

func TestEveryDemoCaseProducesValidConfiguration(t *testing.T) {
	contents, err := os.ReadFile(filepath.Join("..", "fixture", "backline.yml"))
	if err != nil {
		t.Fatal(err)
	}
	environment := map[string]string{
		"DEMO_SECRET":          "secret",
		"DEMO_MIXED_STATUS":    "ACTIVE",
		"DEMO_TRAFFIC_STATUS":  "ARCHIVED",
		"DEMO_ROLLBACK_STATUS": "ACTIVE",
	}
	for _, caseName := range []string{"safe", "mixed-failure", "rollback-failure", "prepared-rollback", "handoff", "candidate-only-failure"} {
		t.Run(caseName, func(t *testing.T) {
			if _, err := config.Parse(prepareConfiguration(contents, caseName), environment); err != nil {
				t.Fatal(err)
			}
		})
	}
}
