package dev.backline.config.model;

import java.util.List;

/**
 * Parsed {@code backline.yml} root: project slug, target environment, and check definitions.
 */
public record BacklineConfig(String project, String environment, List<CheckDefinition> checks) {}
