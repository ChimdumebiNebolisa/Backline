package dev.backline.core.check;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.api.dto.DiffBaselineStrategy;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.contract.ContractSeverity;
import dev.backline.core.run.RunEventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreEnumsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void httpMethodsRoundTripThroughJson() throws Exception {
        for (HttpMethod method : HttpMethod.values()) {
            String json = objectMapper.writeValueAsString(method);
            assertThat(objectMapper.readValue(json, HttpMethod.class)).isEqualTo(method);
        }
    }

    @Test
    void checkResultStatusesRoundTripThroughJson() throws Exception {
        for (CheckResultStatus status : CheckResultStatus.values()) {
            String json = objectMapper.writeValueAsString(status);
            assertThat(objectMapper.readValue(json, CheckResultStatus.class)).isEqualTo(status);
        }
    }

    @Test
    void runEventTypesRoundTripThroughJson() throws Exception {
        for (RunEventType type : RunEventType.values()) {
            String json = objectMapper.writeValueAsString(type);
            assertThat(objectMapper.readValue(json, RunEventType.class)).isEqualTo(type);
        }
    }

    @Test
    void diffBaselineStrategiesRoundTripThroughJson() throws Exception {
        for (DiffBaselineStrategy strategy : DiffBaselineStrategy.values()) {
            String json = objectMapper.writeValueAsString(strategy);
            assertThat(objectMapper.readValue(json, DiffBaselineStrategy.class)).isEqualTo(strategy);
        }
    }

    @Test
    void runDiffChangeTypesRoundTripThroughJson() throws Exception {
        for (RunDiffChangeType changeType : RunDiffChangeType.values()) {
            String json = objectMapper.writeValueAsString(changeType);
            assertThat(objectMapper.readValue(json, RunDiffChangeType.class)).isEqualTo(changeType);
        }
    }

    @Test
    void contractSeveritiesRoundTripThroughJson() throws Exception {
        for (ContractSeverity severity : ContractSeverity.values()) {
            String json = objectMapper.writeValueAsString(severity);
            assertThat(objectMapper.readValue(json, ContractSeverity.class)).isEqualTo(severity);
        }
    }
}
