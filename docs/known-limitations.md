# Known limitations

1. Coverage is limited by configured workloads; a pass is not proof.
2. Controls reduce false attribution but do not prove causality.
3. Reused baseline workloads must tolerate repeated execution against evolving state.
4. Shared state is not reset between scenarios, so order matters.
5. Execution is sequential and does not reproduce concurrent races.
6. Backline runs one instance per component per revision; it does not model fleets, quorum, autoscaling, or load balancing.
7. Headless consumer selection may require project-controlled gating.
8. At least one addressable component and one primary port per component are required.
9. Custom TLS readiness is project-owned.
10. Orchestration is Docker Compose only; Kubernetes is out of scope.
11. Backline does not clone production state or inspect shared-service semantics.
12. Shared-service upgrades are not modeled; one candidate-defined environment serves both revisions.
13. Backline performs no automatic migration safety analysis or inferred down migration.
14. Prepared rollback makes a different claim from raw rollback.
15. Components must exist in both revisions; candidate-only component additions/removals are unsupported.
16. Release components do not receive host filesystem volumes.
17. Remote environments are rejected by default.
18. Application and workload nondeterminism must be stabilized by the project.
19. Host workload dependencies are project-owned.
20. Redaction and secret classification are best effort.
21. Backline builds and executes trusted code and is not a sandbox.
22. Project commands can make external network calls.
23. Backline verifies rollouts; it does not deploy.
