package dev.backline.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.check.CheckResultStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates a machine-readable JSON report artifact from already-fetched API DTOs.
 */
public final class DefaultJsonReportGenerator {

    private final ObjectMapper mapper;

    public DefaultJsonReportGenerator() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String generate(ReportInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        Map<String, Object> root = new HashMap<>();
        root.put("generatedAt", inputs.generatedAt());
        root.put("run", inputs.run());
        root.put("project", inputs.project());
        root.put("summary", inputs.summary());
        root.put("results", inputs.results());
        root.put("diff", inputs.diff());
        root.put("riskScore", riskScore(inputs.results(), inputs.diff()));
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize JSON report", e);
        }
    }

    private static int riskScore(List<CheckResultDto> results, RunDiffDto diff) {
        int score = 0;
        if (results != null) {
            for (CheckResultDto result : results) {
                if (result.status() == CheckResultStatus.ERROR) {
                    score += 3;
                } else if (result.status() == CheckResultStatus.FAILED) {
                    score += 2;
                }
            }
        }
        if (diff != null && diff.entries() != null) {
            score += diff.entries().size();
        }
        return score;
    }
}
