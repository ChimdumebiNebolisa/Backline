package components

import (
	"errors"
	"testing"

	"github.com/ChimdumebiNebolisa/Backline/internal/config"
)

func TestRoleEnvironmentPrecedence(t *testing.T) {
	component := config.Component{Run: config.ComponentRun{
		Environment:          map[string]string{"COMMON": "yes", "ROLE": "common"},
		BaseEnvironment:      map[string]string{"ROLE": "base"},
		CandidateEnvironment: map[string]string{"ROLE": "candidate"},
	}}
	if got := RoleEnvironment(component, "base")["ROLE"]; got != "base" {
		t.Fatalf("base ROLE=%q", got)
	}
	if got := RoleEnvironment(component, "candidate")["ROLE"]; got != "candidate" {
		t.Fatalf("candidate ROLE=%q", got)
	}
}

func TestOperationalErrorsRemainDistinguishableFromProjectReadinessFailures(t *testing.T) {
	if !IsOperational(operational(errors.New("Docker inspect unavailable"))) {
		t.Fatal("expected wrapped Docker error to remain operational")
	}
	if IsOperational(errors.New("container exited")) {
		t.Fatal("project readiness failure must not be operational")
	}
	failure := &ComponentFailure{Role: "base", Component: "api", err: errors.New("base.api exited")}
	actual, ok := AsComponentFailure(failure)
	if !ok || actual.Role != "base" || actual.Component != "api" {
		t.Fatalf("component failure identity lost: %+v %v", actual, ok)
	}
}

func TestEndpointEnvironmentUsesHostOrNetworkCoordinates(t *testing.T) {
	instance := Instance{
		Role:         "base",
		Component:    config.Component{Name: "public-api", Run: config.ComponentRun{InternalPort: 8080, Scheme: "http"}},
		NetworkAlias: "base-public-api",
		Endpoint:     &Endpoint{Host: "127.0.0.1", Port: 49152},
	}
	host := EndpointEnvironment([]Instance{instance}, false)
	if host["BACKLINE_BASE_PUBLIC_API_URL"] != "http://127.0.0.1:49152" {
		t.Fatalf("host env=%#v", host)
	}
	network := EndpointEnvironment([]Instance{instance}, true)
	if network["BACKLINE_BASE_PUBLIC_API_URL"] != "http://base-public-api:8080" {
		t.Fatalf("network env=%#v", network)
	}
}
