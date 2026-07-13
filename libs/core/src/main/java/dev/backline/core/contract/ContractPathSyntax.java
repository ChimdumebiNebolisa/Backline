package dev.backline.core.contract;

import java.util.regex.Pattern;

/**
 * Validates the deliberately small ignore-path syntax for observed contracts.
 *
 * <p>Supported forms: {@code $}, {@code $.a}, {@code $.a.b}, {@code $.items[]}, {@code $.items[].name}.
 * No filters, wildcards, recursive descent, or script expressions.
 */
public final class ContractPathSyntax {

    private static final Pattern VALID = Pattern.compile(
            "^\\$(?:\\.[A-Za-z_][A-Za-z0-9_]*|\\[\\])*$");

    private ContractPathSyntax() {}

    public static boolean isValid(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String trimmed = path.trim();
        if (trimmed.length() > ContractLimits.MAX_IGNORE_PATH_LENGTH) {
            return false;
        }
        return VALID.matcher(trimmed).matches();
    }

    /**
     * Returns true when {@code candidate} equals {@code ignore} or is a descendant of that ignore path.
     */
    public static boolean matchesIgnore(String candidate, String ignore) {
        if (candidate == null || ignore == null) {
            return false;
        }
        String c = candidate.trim();
        String i = ignore.trim();
        if (c.equals(i)) {
            return true;
        }
        // Descendant: ignore $.meta matches $.meta.generated_at; ignore $.items[] matches $.items[].id
        if (c.startsWith(i)) {
            char next = c.length() > i.length() ? c.charAt(i.length()) : 0;
            return next == '.' || next == '[';
        }
        return false;
    }
}
