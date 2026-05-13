package dev.backline.core.constants;

import java.util.Set;

/**
 * Header names that must be redacted from logs and previews (lowercase for case-insensitive matching).
 */
public final class SensitiveHeaders {

    public static final Set<String> NAMES = Set.of("authorization", "cookie", "set-cookie");

    private SensitiveHeaders() {}
}
