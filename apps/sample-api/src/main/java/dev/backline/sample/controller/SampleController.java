package dev.backline.sample.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class SampleController {

    private final AtomicLong valueCounter = new AtomicLong(1);
    private final AtomicReference<String> schemaMode = new AtomicReference<>("stable");

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "sample-api");
    }

    @GetMapping("/users/1")
    public Map<String, Object> getUser() {
        return Map.of("id", 1, "email", "user1@example.com", "name", "Sample User");
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (body != null) {
            merged.putAll(body);
        }
        merged.put("id", 2);
        return ResponseEntity.status(HttpStatus.CREATED).body(merged);
    }

    @GetMapping("/slow")
    public Map<String, Object> slow() throws InterruptedException {
        Thread.sleep(700);
        return Map.of("status", "ok", "delayedMs", 700);
    }

    @GetMapping("/broken")
    public ResponseEntity<Map<String, String>> broken() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "intentional failure"));
    }

    /**
     * Deterministic schema playground for observed response-contract demos.
     *
     * <p>Modes ({@code ?mode=} or {@code POST /schema-change/mode}): {@code stable} (changing scalar values,
     * same shape), {@code additive}, {@code breaking-remove}, {@code breaking-type}, {@code array-shape},
     * {@code ignored-meta}, {@code invalid-json}.
     */
    @GetMapping("/schema-change")
    public ResponseEntity<?> schemaChange(@RequestParam(value = "mode", required = false) String modeOverride) {
        String mode = modeOverride == null || modeOverride.isBlank() ? schemaMode.get() : modeOverride.trim();
        long value = valueCounter.getAndIncrement();
        return switch (mode) {
            case "additive" -> ResponseEntity.ok(baseStable(value, true));
            case "breaking-remove" -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", 1);
                body.put("value", value);
                yield ResponseEntity.ok(body);
            }
            case "breaking-type" -> {
                Map<String, Object> body = baseStable(value, false);
                body.put("id", "1");
                yield ResponseEntity.ok(body);
            }
            case "array-shape" -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", 1);
                body.put("items", List.of(Map.of("name", "a", "qty", 1), Map.of("name", "b", "qty", "2")));
                yield ResponseEntity.ok(body);
            }
            case "ignored-meta" -> {
                Map<String, Object> body = baseStable(value, false);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("generated_at", "2026-01-01T00:00:00Z");
                body.put("meta", meta);
                yield ResponseEntity.ok(body);
            }
            case "invalid-json" -> ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{not-json");
            default -> ResponseEntity.ok(baseStable(value, false));
        };
    }

    @PostMapping("/schema-change/mode")
    public Map<String, String> setSchemaMode(@RequestBody(required = false) Map<String, Object> body) {
        String mode = "stable";
        if (body != null && body.get("mode") != null) {
            mode = String.valueOf(body.get("mode")).trim();
        }
        schemaMode.set(mode);
        return Map.of("mode", mode);
    }

    @GetMapping("/schema-change/mode")
    public Map<String, String> getSchemaMode() {
        return Map.of("mode", schemaMode.get());
    }

    private static Map<String, Object> baseStable(long value, boolean withExtra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 1);
        body.put("name", "widget");
        body.put("value", value);
        if (withExtra) {
            body.put("display_name", "Widget");
        }
        return body;
    }
}
