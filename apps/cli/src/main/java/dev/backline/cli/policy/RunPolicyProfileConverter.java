package dev.backline.cli.policy;

import picocli.CommandLine;

/**
 * Accepts CLI values {@code strict} and {@code warn-only} for {@link RunPolicyProfile}.
 */
public final class RunPolicyProfileConverter implements CommandLine.ITypeConverter<RunPolicyProfile> {

    @Override
    public RunPolicyProfile convert(String value) {
        return RunPolicyProfile.fromCliValue(value);
    }
}
