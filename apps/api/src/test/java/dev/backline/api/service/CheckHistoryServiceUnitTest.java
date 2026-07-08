package dev.backline.api.service;

import dev.backline.api.exception.NotFoundException;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.core.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckHistoryServiceUnitTest {

    @Mock
    private CheckRepository checkRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @InjectMocks
    private CheckHistoryService checkHistoryService;

    @Test
    void history_throwsNotFoundForUnknownCheckId() {
        UUID checkId = UUID.randomUUID();
        when(checkRepository.existsById(checkId)).thenReturn(false);
        assertThatThrownBy(() -> checkHistoryService.history(checkId, 10, 0))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void history_returnsProjectionOrderedByCreatedAtDesc() {
        UUID checkId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(checkRepository.existsById(checkId)).thenReturn(true);
        var row = new dev.backline.api.persistence.repository.CheckResultRepository.CheckHistoryProjection() {
            @Override
            public UUID getRunId() {
                return runId;
            }

            @Override
            public RunStatus getRunStatus() {
                return RunStatus.FAILED;
            }

            @Override
            public dev.backline.core.check.CheckResultStatus getResultStatus() {
                return dev.backline.core.check.CheckResultStatus.FAILED;
            }

            @Override
            public Integer getActualStatus() {
                return 500;
            }

            @Override
            public Long getLatencyMs() {
                return 100L;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.parse("2024-01-01T00:00:00Z");
            }
        };
        when(checkResultRepository.findHistoryByCheckId(eq(checkId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        var page = checkHistoryService.history(checkId, 10, 0);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().runId()).isEqualTo(runId.toString());
    }
}
