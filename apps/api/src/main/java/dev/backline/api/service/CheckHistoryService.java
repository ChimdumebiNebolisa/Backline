package dev.backline.api.service;

import dev.backline.api.exception.NotFoundException;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.core.api.dto.CheckHistoryEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckHistoryService {

    private final CheckRepository checkRepository;
    private final CheckResultRepository checkResultRepository;

    public CheckHistoryService(
            CheckRepository checkRepository,
            CheckResultRepository checkResultRepository) {
        this.checkRepository = checkRepository;
        this.checkResultRepository = checkResultRepository;
    }

    @Transactional(readOnly = true)
    public Page<CheckHistoryEntry> history(UUID checkId, int limit, int offset) {
        if (!checkRepository.existsById(checkId)) {
            throw new NotFoundException("check not found", "checkId");
        }
        var pageable = new OffsetBasedPageRequest(limit, offset, Sort.by(Sort.Direction.DESC, "createdAt"));
        return checkResultRepository.findHistoryByCheckId(checkId, pageable).map(row -> new CheckHistoryEntry(
                row.getRunId().toString(),
                row.getRunStatus(),
                row.getResultStatus(),
                row.getActualStatus(),
                row.getLatencyMs(),
                row.getCreatedAt()));
    }
}
