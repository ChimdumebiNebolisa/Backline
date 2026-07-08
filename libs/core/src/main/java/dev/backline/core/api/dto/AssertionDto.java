package dev.backline.core.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Assertion rule attached to a check; JSON property {@code equals} maps to {@link #equalsValue()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AssertionDto {

    private final String path;
    private final Object equalsValue;
    private final Boolean exists;
    private final Object notEquals;
    private final Object contains;
    private final String regex;
    private final Double gt;
    private final Double gte;
    private final Double lt;
    private final Double lte;

    public AssertionDto(String path, Object equalsValue, Boolean exists) {
        this(path, equalsValue, exists, null, null, null, null, null, null, null);
    }

    @JsonCreator
    public AssertionDto(
            @JsonProperty("path") String path,
            @JsonProperty("equals") Object equalsValue,
            @JsonProperty("exists") Boolean exists,
            @JsonProperty("not_equals") Object notEquals,
            @JsonProperty("contains") Object contains,
            @JsonProperty("regex") String regex,
            @JsonProperty("gt") Double gt,
            @JsonProperty("gte") Double gte,
            @JsonProperty("lt") Double lt,
            @JsonProperty("lte") Double lte) {
        this.path = path;
        this.equalsValue = equalsValue;
        this.exists = exists;
        this.notEquals = notEquals;
        this.contains = contains;
        this.regex = regex;
        this.gt = gt;
        this.gte = gte;
        this.lt = lt;
        this.lte = lte;
    }

    public String path() {
        return path;
    }

    @JsonProperty("equals")
    public Object equalsValue() {
        return equalsValue;
    }

    public Boolean exists() {
        return exists;
    }

    @JsonProperty("not_equals")
    public Object notEquals() {
        return notEquals;
    }

    public Object contains() {
        return contains;
    }

    public String regex() {
        return regex;
    }

    public Double gt() {
        return gt;
    }

    public Double gte() {
        return gte;
    }

    public Double lt() {
        return lt;
    }

    public Double lte() {
        return lte;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AssertionDto that)) {
            return false;
        }
        return Objects.equals(path, that.path)
                && Objects.equals(equalsValue, that.equalsValue)
                && Objects.equals(exists, that.exists)
                && Objects.equals(notEquals, that.notEquals)
                && Objects.equals(contains, that.contains)
                && Objects.equals(regex, that.regex)
                && Objects.equals(gt, that.gt)
                && Objects.equals(gte, that.gte)
                && Objects.equals(lt, that.lt)
                && Objects.equals(lte, that.lte);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, equalsValue, exists, notEquals, contains, regex, gt, gte, lt, lte);
    }
}
