package dev.backline.api.service;

import dev.backline.core.run.RunStatus;

import java.time.Instant;

public record RunFilter(
        String projectSlug, String environment, RunStatus status, Instant startedAfter, Instant startedBefore) {}
