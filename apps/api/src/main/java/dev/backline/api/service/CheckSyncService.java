package dev.backline.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.api.mapper.AssertionJsonMapper;
import dev.backline.api.mapper.CheckMapper;
import dev.backline.api.persistence.entity.CheckEntity;
import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.api.dto.CheckDefinitionDto;
import dev.backline.core.api.dto.CheckDto;
import dev.backline.core.api.dto.CheckSyncRequest;
import dev.backline.core.validation.AssertionValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CheckSyncService {

    private final CheckRepository checkRepository;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public CheckSyncService(CheckRepository checkRepository, ProjectService projectService, ObjectMapper objectMapper) {
        this.checkRepository = checkRepository;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<CheckDto> sync(CheckSyncRequest req) {
        validateRequest(req);
        ProjectEntity project = projectService.getOrCreateBySlug(req.projectSlug().trim(), req.projectName());
        UUID projectId = project.getId();
        Set<String> incomingKeys = new HashSet<>();
        for (CheckDefinitionDto def : req.checks()) {
            incomingKeys.add(def.key());
        }
        for (CheckEntity existing : checkRepository.findByProjectId(projectId)) {
            if (!incomingKeys.contains(existing.getKey())) {
                existing.setActive(false);
            }
        }
        List<CheckDto> result = new ArrayList<>();
        for (CheckDefinitionDto def : req.checks()) {
            String configHash = computeConfigHash(def);
            String assertionsJson = AssertionJsonMapper.toJsonOrNull(objectMapper, def.assertions());
            Optional<CheckEntity> opt = checkRepository.findByProjectIdAndKey(projectId, def.key());
            CheckEntity entity = opt.orElseGet(() -> {
                CheckEntity c = new CheckEntity();
                c.setProjectId(projectId);
                c.setKey(def.key());
                return c;
            });
            entity.setName(def.name());
            entity.setMethod(def.method());
            entity.setUrl(def.url());
            entity.setExpectedStatus(def.expectedStatus());
            entity.setMaxLatencyMs(def.maxLatencyMs());
            entity.setConfigHash(configHash);
            entity.setAssertionsJson(assertionsJson);
            entity.setActive(true);
            entity = checkRepository.save(entity);
            result.add(CheckMapper.toDto(entity, objectMapper));
        }
        result.sort(Comparator.comparing(CheckDto::key));
        return result;
    }

    private void validateRequest(CheckSyncRequest req) {
        if (req.projectSlug() == null || req.projectSlug().isBlank()) {
            throw new ValidationFailedException("projectSlug is required", "projectSlug");
        }
        if (req.checks() == null || req.checks().isEmpty()) {
            throw new ValidationFailedException("checks must not be empty", "checks");
        }
        Set<String> keys = new HashSet<>();
        for (CheckDefinitionDto c : req.checks()) {
            if (c.key() == null || c.key().isBlank()) {
                throw new ValidationFailedException("check key is required", "checks");
            }
            if (!keys.add(c.key())) {
                throw new ValidationFailedException("duplicate check key: " + c.key(), "checks");
            }
            if (c.name() == null || c.name().isBlank()) {
                throw new ValidationFailedException("check name is required", "checks");
            }
            if (c.method() == null) {
                throw new ValidationFailedException("method is required", "checks");
            }
            if (c.url() == null || c.url().isBlank()) {
                throw new ValidationFailedException("url is required", "checks");
            }
            validateAbsoluteUrl(c.url());
            if (c.expectedStatus() < 100 || c.expectedStatus() > 599) {
                throw new ValidationFailedException("expected_status must be between 100 and 599", "checks");
            }
            if (c.maxLatencyMs() != null && c.maxLatencyMs() <= 0) {
                throw new ValidationFailedException("max_latency_ms must be greater than zero when present", "checks");
            }
            validateAssertions(c.assertions());
        }
    }

    private static void validateAssertions(List<AssertionDto> assertions) {
        if (assertions == null) {
            return;
        }
        for (var assertion : assertions) {
            try {
                AssertionValidator.validateSingleOperator(assertion);
            } catch (IllegalArgumentException ex) {
                throw new ValidationFailedException(ex.getMessage(), "checks.assertions");
            }
        }
    }

    private static void validateAbsoluteUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            boolean supportedScheme = scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
            if (!uri.isAbsolute() || !supportedScheme || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ValidationFailedException("url must be an absolute http(s) URL", "checks");
            }
        } catch (IllegalArgumentException ex) {
            throw new ValidationFailedException("url must be an absolute http(s) URL", "checks");
        }
    }

    private String computeConfigHash(CheckDefinitionDto def) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("method", def.method().name());
            root.put("url", def.url());
            root.put("expected_status", def.expectedStatus());
            if (def.maxLatencyMs() != null) {
                root.put("max_latency_ms", def.maxLatencyMs());
            }
            ArrayNode arr = objectMapper.createArrayNode();
            for (var a : AssertionJsonMapper.sortedCopy(def.assertions() == null ? List.of() : def.assertions())) {
                ObjectNode n = objectMapper.createObjectNode();
                if (a.path() != null) {
                    n.put("path", a.path());
                }
                if (a.exists() != null) {
                    n.put("exists", a.exists());
                }
                if (a.equalsValue() != null) {
                    n.set("equals", objectMapper.valueToTree(a.equalsValue()));
                }
                if (a.notEquals() != null) {
                    n.set("not_equals", objectMapper.valueToTree(a.notEquals()));
                }
                if (a.contains() != null) {
                    n.set("contains", objectMapper.valueToTree(a.contains()));
                }
                if (a.regex() != null) {
                    n.put("regex", a.regex());
                }
                if (a.gt() != null) {
                    n.put("gt", a.gt());
                }
                if (a.gte() != null) {
                    n.put("gte", a.gte());
                }
                if (a.lt() != null) {
                    n.put("lt", a.lt());
                }
                if (a.lte() != null) {
                    n.put("lte", a.lte());
                }
                arr.add(n);
            }
            root.set("assertions", arr);
            byte[] canonical = objectMapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(canonical));
        } catch (Exception e) {
            throw new ValidationFailedException("could not compute config hash");
        }
    }
}
