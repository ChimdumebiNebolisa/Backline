package dev.backline.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.backline.config.model.BacklineConfig;
import dev.backline.config.model.CheckDefinition;
import dev.backline.core.api.dto.AssertionDto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses {@code backline.yml} using Jackson YAML with {@link PropertyNamingStrategies#SNAKE_CASE} so
 * YAML keys such as {@code expected_status} map to Java {@code expectedStatus} without per-field
 * annotations on config records.
 */
public final class ConfigParser {

    private static final ObjectMapper YAML_MAPPER = JsonMapper.builder(new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            .build();

    public BacklineConfig parse(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return parse(in, path.toAbsolutePath().normalize().toString());
        } catch (IOException e) {
            throw new ConfigParseException("cannot read config file: " + e.getMessage(), null, e);
        }
    }

    public BacklineConfig parse(InputStream in, String sourceName) {
        try {
            BacklineConfig config = YAML_MAPPER.readValue(in, BacklineConfig.class);
            ConfigValidator.validate(config);
            return config;
        } catch (ConfigParseException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new ConfigParseException(
                    "invalid YAML or JSON in " + sourceName + ": " + e.getOriginalMessage(),
                    null,
                    e);
        } catch (IOException e) {
            throw new ConfigParseException("cannot read config from " + sourceName + ": " + e.getMessage(), null, e);
        }
    }

    /**
     * SHA-256 hex of canonical JSON for {@code checks} only: checks sorted by {@link CheckDefinition#key()},
     * assertions sorted by {@link AssertionDto#path()}; null assertion lists treated as empty.
     */
    public String canonicalConfigHash(BacklineConfig config) {
        try {
            List<Map<String, Object>> checksJson = config.checks().stream()
                    .sorted(Comparator.comparing(CheckDefinition::key))
                    .map(ConfigParser::checkToCanonicalMap)
                    .toList();
            String json = CANONICAL_JSON.writeValueAsString(checksJson);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize canonical checks", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static Map<String, Object> checkToCanonicalMap(CheckDefinition c) {
        Map<String, Object> map = new TreeMap<>();
        map.put("assertions", canonicalAssertions(c.assertions()));
        map.put("expected_status", c.expectedStatus());
        map.put("key", c.key());
        if (c.maxLatencyMs() != null) {
            map.put("max_latency_ms", c.maxLatencyMs());
        }
        map.put("method", c.method().name());
        map.put("name", c.name());
        map.put("url", c.url());
        return map;
    }

    private static List<Map<String, Object>> canonicalAssertions(List<AssertionDto> assertions) {
        List<AssertionDto> list = assertions == null ? List.of() : assertions;
        return list.stream()
                .sorted(Comparator.comparing(AssertionDto::path))
                .map(ConfigParser::assertionToMap)
                .toList();
    }

    private static Map<String, Object> assertionToMap(AssertionDto a) {
        Map<String, Object> m = new TreeMap<>();
        m.put("path", a.path());
        if (a.equalsValue() != null) {
            m.put("equals", a.equalsValue());
        }
        if (a.exists() != null) {
            m.put("exists", a.exists());
        }
        if (a.notEquals() != null) {
            m.put("not_equals", a.notEquals());
        }
        if (a.contains() != null) {
            m.put("contains", a.contains());
        }
        if (a.regex() != null) {
            m.put("regex", a.regex());
        }
        if (a.gt() != null) {
            m.put("gt", a.gt());
        }
        if (a.gte() != null) {
            m.put("gte", a.gte());
        }
        if (a.lt() != null) {
            m.put("lt", a.lt());
        }
        if (a.lte() != null) {
            m.put("lte", a.lte());
        }
        return m;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
