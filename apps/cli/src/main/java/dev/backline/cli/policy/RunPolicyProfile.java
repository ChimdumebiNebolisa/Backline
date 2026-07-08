package dev.backline.cli.policy;

import dev.backline.config.model.RunPolicy;
import picocli.CommandLine;

import java.util.Locale;

/**
 * Named policy presets for CI gating. {@link #STRICT} fails on any newly failing or errored check;
 * {@link #WARN_ONLY} disables threshold enforcement (all limits null).
 */
public enum RunPolicyProfile {
    STRICT(new RunPolicy(0, 0, null)),
    WARN_ONLY(new RunPolicy(null, null, null));

    private final RunPolicy policy;

    RunPolicyProfile(RunPolicy policy) {
        this.policy = policy;
    }

    public RunPolicy toPolicy() {
        return policy;
    }

    public static RunPolicyProfile fromCliValue(String value) {
        if (value == null || value.isBlank()) {
            throw new CommandLine.TypeConversionException("policy preset must not be blank");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> STRICT;
            case "warn-only" -> WARN_ONLY;
            default -> throw new CommandLine.TypeConversionException(
                    "Invalid policy preset '" + value + "'. Expected: strict, warn-only");
        };
    }
}
