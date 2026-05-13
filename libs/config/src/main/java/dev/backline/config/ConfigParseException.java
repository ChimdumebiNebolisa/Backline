package dev.backline.config;

/**
 * Raised when config YAML cannot be read or fails validation.
 *
 * @param detail human-readable explanation including location when known
 * @param field dotted path such as {@code checks[2].url}, or null if not field-specific
 */
public class ConfigParseException extends RuntimeException {

    private final String detail;
    private final String field;

    public ConfigParseException(String detail, String field) {
        super(buildMessage(detail, field));
        this.detail = detail;
        this.field = field;
    }

    public ConfigParseException(String detail, String field, Throwable cause) {
        super(buildMessage(detail, field), cause);
        this.detail = detail;
        this.field = field;
    }

    private static String buildMessage(String detail, String field) {
        if (field == null || field.isBlank()) {
            return detail;
        }
        return detail + " (field: " + field + ")";
    }

    public String detail() {
        return detail;
    }

    public String field() {
        return field;
    }
}
