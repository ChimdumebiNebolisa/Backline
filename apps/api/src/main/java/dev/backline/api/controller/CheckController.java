package dev.backline.api.controller;

import dev.backline.core.api.DataResponse;
import dev.backline.core.api.ListResponse;
import dev.backline.core.api.PageMeta;
import dev.backline.core.api.dto.CheckDefinitionDto;
import dev.backline.core.api.dto.CheckDto;
import dev.backline.core.api.dto.CheckSyncRequest;
import dev.backline.api.dto.CheckDefinitionBody;
import dev.backline.api.dto.CheckSyncBody;
import dev.backline.api.service.CheckHistoryService;
import dev.backline.api.service.CheckSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/checks")
@Validated
public class CheckController {

    private final CheckSyncService checkSyncService;
    private final CheckHistoryService checkHistoryService;

    public CheckController(CheckSyncService checkSyncService, CheckHistoryService checkHistoryService) {
        this.checkSyncService = checkSyncService;
        this.checkHistoryService = checkHistoryService;
    }

    @PostMapping("/sync")
    public DataResponse<List<CheckDto>> sync(@Valid @RequestBody CheckSyncBody body) {
        return DataResponse.of(checkSyncService.sync(toCore(body)));
    }

    @GetMapping("/{checkId}/history")
    public ListResponse<dev.backline.core.api.dto.CheckHistoryEntry> history(
            @PathVariable UUID checkId,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        var page = checkHistoryService.history(checkId, limit, offset);
        return ListResponse.of(page.getContent(), new PageMeta(limit, offset, page.getTotalElements()));
    }

    private static CheckSyncRequest toCore(CheckSyncBody body) {
        List<CheckDefinitionDto> defs = body.checks().stream()
                .map(CheckController::toDefinition)
                .toList();
        return new CheckSyncRequest(body.projectSlug(), body.projectName(), defs);
    }

    private static CheckDefinitionDto toDefinition(CheckDefinitionBody b) {
        return new CheckDefinitionDto(
                b.key(),
                b.name(),
                b.method(),
                b.url(),
                b.expectedStatus(),
                b.maxLatencyMs(),
                b.assertions() == null ? List.of() : b.assertions());
    }
}
