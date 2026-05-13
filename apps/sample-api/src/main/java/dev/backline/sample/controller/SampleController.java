package dev.backline.sample.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SampleController {

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

    @GetMapping("/schema-change")
    public Map<String, Object> schemaChange() {
        return Map.of("id", 1, "renamedField", "value");
    }
}
