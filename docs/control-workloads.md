# Control workloads

Backline requires three controls before it interprets directional coexistence failures.

1. `base_after_transition` reruns a base control after candidate transition while the original base process remains alive.
2. `candidate_before_coexistence` proves the candidate works on its own before mixed-version scenarios.
3. `base_with_candidate_running` checks the original base again after candidate startup and candidate control.

Controls may `reuse_baseline: true` or define explicit scenarios. Reuse preserves command arrays, ordering, working directories, timeouts, and environments while mapping targets to the control role.

A failed transition or candidate control is a failed rollout precondition and normally makes unfinished verdicts inconclusive. A failed base-after-transition or base-with-candidate-running control is direct mixed-version evidence and makes the mixed verdict fail when orchestration and shared services remain usable.

Controls reduce false attribution; they do not prove causality. Shared state evolves across stages and a failure can still have multiple causes.
