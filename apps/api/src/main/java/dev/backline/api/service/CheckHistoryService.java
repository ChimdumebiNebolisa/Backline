package dev.backline.api.service;

import dev.backline.api.exception.NotFoundException;
import dev.backline.api.persistence.entity.RunEntity;
import dev.backline.api.persistence.repository.CheckRepository;
import dev.backline.api.persistence.repository.CheckResultRepository;
import dev.backline.api.persistence.repository.RunRepository;
import dev.backline.core.api.dto.CheckHistoryEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckHistoryService {

    private final CheckRepository checkRepository;
    private final CheckResultRepository checkResultRepository;
    private final RunRepository runRepository;

    public CheckHistoryService(
            CheckRepository checkRepository,
            CheckResultRepository checkResultRepository,
            RunRepository runRepository) {
        this.checkRepository = checkRepository;
        this.checkResultRepository = checkResultRepository;
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public Page<CheckHistoryEntry> history(UUID checkId, int limit, int offset) {
        if (!checkRepository.existsById(checkId)) {
            throw new NotFoundException("check not found", "checkId");
        }
        var pageable = PageRequest.of(limit > 0 ? offset / limit : 0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return checkResultRepository.findByCheckIdOrderByCreatedAtDesc(checkId, pageable).map(cr -> {
            RunEntity run =
                    runRepository.findById(cr.getRunId()).orElseThrow(() -> new NotFoundException("run not found", "runId"));
            return new CheckHistoryEntry(
                    cr.getRunId().toString(),
                    run.getStatus(),
                    cr.getStatus(),
                    cr.getActualStatus(),
                    cr.getLatencyMs(),
                    cr.getCreatedAt());
        });
    }
}
