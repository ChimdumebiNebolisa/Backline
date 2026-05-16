package dev.backline.core.run;

/**
 * Auditable run lifecycle events stored alongside run rows.
 */
public enum RunEventType {
    SUBMITTED,
    CLAIMED,
    STARTED,
    RESULT_RECORDED,
    COMPLETED,
    FAILED,
    ERRORED,
    RETRY_SCHEDULED,
    STALE_RECOVERED,
    CANCELLED,
    TIMED_OUT
}
