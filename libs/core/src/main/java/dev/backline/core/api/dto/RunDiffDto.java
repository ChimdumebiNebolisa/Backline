package dev.backline.core.api.dto;

import java.util.List;

/**
 * Diff between a run and the previous completed run for the same project and environment.
 */
public record RunDiffDto(String runId, String previousRunId, List<RunDiffEntry> entries) {}
