package dev.backline.config;

import dev.backline.config.model.BacklineConfig;
import dev.backline.config.model.CheckDefinition;
import dev.backline.config.model.RunPolicy;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.validation.AssertionValidator;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic validation for parsed {@link BacklineConfig}; throws {@link ConfigParseException} on
 * the first failure with a field path such as {@code checks[0].url}.
 */
public final class ConfigValidator {

    private static final Pattern CHECK_KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,119}");

    private ConfigValidator() {}

    public static void validate(BacklineConfig config) {
        if (config == null) {
            throw new ConfigParseException("config is null", null);
        }
        if (isBlank(config.project())) {
            throw new ConfigParseException("project must not be blank", "project");
        }
        if (isBlank(config.environment())) {
            throw new ConfigParseException("environment must not be blank", "environment");
        }
        List<CheckDefinition> checks = config.checks();
        if (checks == null || checks.isEmpty()) {
            throw new ConfigParseException("checks must contain at least one entry", "checks");
        }
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < checks.size(); i++) {
            String prefix = "checks[" + i + "]";
            CheckDefinition c = checks.get(i);
            if (c == null) {
                throw new ConfigParseException("check entry must not be null", prefix);
            }
            if (isBlank(c.key())) {
                throw new ConfigParseException("check key must not be blank", prefix + ".key");
            }
            if (!CHECK_KEY_PATTERN.matcher(c.key()).matches()) {
                throw new ConfigParseException(
                        "check key must match [a-z0-9][a-z0-9-]{0,119}", prefix + ".key");
            }
            if (!seenKeys.add(c.key())) {
                throw new ConfigParseException("duplicate check key: " + c.key(), prefix + ".key");
            }
            if (isBlank(c.name())) {
                throw new ConfigParseException("check name must not be blank", prefix + ".name");
            }
            if (c.name().length() > 200) {
                throw new ConfigParseException("check name must be at most 200 characters", prefix + ".name");
            }
            if (c.method() == null) {
                throw new ConfigParseException("method must be set", prefix + ".method");
            }
            if (isBlank(c.url())) {
                throw new ConfigParseException("url must not be blank", prefix + ".url");
            }
            validateUrl(prefix + ".url", c.url());
            if (c.expectedStatus() < 100 || c.expectedStatus() > 599) {
                throw new ConfigParseException(
                        "expected_status must be between 100 and 599", prefix + ".expected_status");
            }
            if (c.maxLatencyMs() != null) {
                if (c.maxLatencyMs() <= 0) {
                    throw new ConfigParseException("max_latency_ms must be greater than zero when present", prefix
                            + ".max_latency_ms");
                }
            }
            validateAssertions(prefix, c.assertions());
        }
        validatePolicy(config.policy());
    }

    private static void validatePolicy(RunPolicy policy) {
        if (policy == null) {
            return;
        }
        if (policy.maxNewlyFailing() != null && policy.maxNewlyFailing() < 0) {
            throw new ConfigParseException("policy.max_newly_failing must be >= 0", "policy.max_newly_failing");
        }
        if (policy.maxErroredChecks() != null && policy.maxErroredChecks() < 0) {
            throw new ConfigParseException("policy.max_errored_checks must be >= 0", "policy.max_errored_checks");
        }
        if (policy.maxLatencyRegressionMs() != null && policy.maxLatencyRegressionMs() < 0) {
            throw new ConfigParseException(
                    "policy.max_latency_regression_ms must be >= 0", "policy.max_latency_regression_ms");
        }
    }

    private static void validateAssertions(String checkPrefix, List<AssertionDto> assertions) {
        if (assertions == null) {
            return;
        }
        for (int j = 0; j < assertions.size(); j++) {
            String prefix = checkPrefix + ".assertions[" + j + "]";
            AssertionDto a = assertions.get(j);
            try {
                AssertionValidator.validateSingleOperator(a);
            } catch (IllegalArgumentException ex) {
                throw new ConfigParseException(ex.getMessage(), prefix, ex);
            }
        }
    }

    private static void validateUrl(String field, String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new ConfigParseException("url is not a valid URI: " + url, field, e);
        }
        if (!uri.isAbsolute()) {
            throw new ConfigParseException("url must be absolute with http or https scheme", field);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new ConfigParseException("url must use http or https scheme", field);
        }
        if (isBlank(uri.getHost())) {
            throw new ConfigParseException("url must include a host", field);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
