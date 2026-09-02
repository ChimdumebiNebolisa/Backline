package orchestrator

import "fmt"

type LifecycleState string

const (
	StatePreflight        LifecycleState = "PREFLIGHT"
	StateBuild            LifecycleState = "BUILD"
	StateShared           LifecycleState = "SHARED_ENVIRONMENT"
	StateBootstrap        LifecycleState = "BOOTSTRAP"
	StateBaseStartup      LifecycleState = "BASE_STARTUP"
	StateBaseline         LifecycleState = "BASELINE"
	StateTransition       LifecycleState = "TRANSITION"
	StateBaseControl      LifecycleState = "BASE_AFTER_TRANSITION_CONTROL"
	StateCandidateStartup LifecycleState = "CANDIDATE_STARTUP"
	StateCandidateControl LifecycleState = "CANDIDATE_CONTROL"
	StateMixedControl     LifecycleState = "BASE_WITH_CANDIDATE_CONTROL"
	StateCoexistence      LifecycleState = "COEXISTENCE"
	StateCandidateOnly    LifecycleState = "CANDIDATE_ONLY"
	StateCandidateTraffic LifecycleState = "CANDIDATE_TRAFFIC"
	StateRollback         LifecycleState = "ROLLBACK"
	StateReporting        LifecycleState = "REPORTING"
	StateCleanup          LifecycleState = "CLEANUP"
	StateComplete         LifecycleState = "COMPLETE"
)

type StateMachine struct {
	current LifecycleState
}

func NewStateMachine() *StateMachine { return &StateMachine{current: StatePreflight} }

func (machine *StateMachine) Current() LifecycleState { return machine.current }

func (machine *StateMachine) Advance(next LifecycleState) error {
	if next == StateReporting && machine.current != StateCleanup && machine.current != StateComplete {
		machine.current = next
		return nil
	}
	allowed := map[LifecycleState]LifecycleState{
		StatePreflight: StateBuild, StateBuild: StateShared, StateShared: StateBootstrap,
		StateBootstrap: StateBaseStartup, StateBaseStartup: StateBaseline, StateBaseline: StateTransition,
		StateTransition: StateBaseControl, StateBaseControl: StateCandidateStartup,
		StateCandidateStartup: StateCandidateControl, StateCandidateControl: StateMixedControl,
		StateMixedControl: StateCoexistence, StateCoexistence: StateCandidateOnly,
		StateCandidateOnly: StateCandidateTraffic, StateCandidateTraffic: StateRollback,
		StateReporting: StateCleanup, StateCleanup: StateComplete,
	}
	if allowed[machine.current] != next {
		return fmt.Errorf("invalid lifecycle transition %s -> %s", machine.current, next)
	}
	machine.current = next
	return nil
}
