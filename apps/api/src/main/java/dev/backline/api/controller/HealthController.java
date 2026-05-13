package dev.backline.api.controller;

import dev.backline.core.api.DataResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight liveness endpoint for load balancers and smoke tests. This is intentionally separate from Spring Boot
 * Actuator's {@code /actuator/health}, which may aggregate database and other dependency probes.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public DataResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "backline-api");
        body.put("time", Instant.now());
        return DataResponse.of(body);
    }
}
