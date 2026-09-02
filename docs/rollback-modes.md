# Rollback modes

Every rollback verdict declares a mode.

## RAW

No rollback hook is configured. Backline stops candidate components and starts fresh base components directly against state left by candidate cutover and traffic.

Required pass wording:

> The base revision passed the configured rollback checks directly against state left by candidate cutover and traffic.

## PREPARED

One or more rollback hooks are configured. They run after candidate stops and before fresh base components start. Hooks may use either revision and may transform state.

Required pass wording:

> The base revision passed the configured rollback checks after candidate cutover, traffic, and the configured rollback preparation.

A prepared pass is not evidence that the base can read untouched candidate-written state. A launched rollback hook failure is a rollback `FAIL`. If candidate-only cutover or candidate traffic is incomplete, rollback cannot pass, but a directly observed rollback failure still takes precedence.
