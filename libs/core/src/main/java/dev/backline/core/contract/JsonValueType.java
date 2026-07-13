package dev.backline.core.contract;

import java.util.Locale;

/** Structural JSON types recorded in observed response contracts. */
public enum JsonValueType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static JsonValueType fromWireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("type name is required");
        }
        return JsonValueType.valueOf(name.trim().toUpperCase(Locale.ROOT));
    }
}
