package dev.backline.api.controller;

import dev.backline.core.api.DataResponse;
import dev.backline.core.api.ListResponse;
import dev.backline.core.api.PageMeta;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.CreateRunRequest;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDto;
import dev.backline.core.run.RunStatus;
import dev.backline.api.dto.CreateRunBody;
import dev.backline.api.service.DiffService;
import dev.backline.api.service.RunFilter;
import dev.backline.api.service.RunService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
@Validated
public class RunController {

    private final RunService runService;
    private final DiffService diffService;

    public RunController(RunService runService, DiffService diffService) {
        this.runService = runService;
        this.diffService = diffService;
    }

    @PostMapping
    public ResponseEntity<DataResponse<RunDto>> submit(@Valid @RequestBody CreateRunBody body) {
        var dto = runService.submit(toCore(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(DataResponse.of(dto));
    }

    @GetMapping
    public ListResponse<RunDto> list(
            @RequestParam(required = false) String projectSlug,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) RunStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedBefore,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        var filter = new RunFilter(projectSlug, environment, status, startedAfter, startedBefore);
        var page = runService.list(filter, limit, offset);
        return ListResponse.of(page.getContent(), new PageMeta(limit, offset, page.getTotalElements()));
    }

    @GetMapping("/{runId}")
    @Operation(operationId = "getRunById")
    public DataResponse<RunDto> get(@PathVariable UUID runId) {
        return DataResponse.of(runService.findById(runId));
    }

    @GetMapping("/{runId}/results")
    public ListResponse<CheckResultDto> results(@PathVariable UUID runId) {
        List<CheckResultDto> list = runService.resultsForRun(runId);
        int n = list.size();
        return ListResponse.of(list, new PageMeta(n, 0, n));
    }

    @GetMapping("/{runId}/diff")
    public DataResponse<RunDiffDto> diff(@PathVariable UUID runId) {
        return DataResponse.of(diffService.computeDiff(runId));
    }

    private static CreateRunRequest toCore(CreateRunBody body) {
        return new CreateRunRequest(
                body.projectSlug(),
                body.environment(),
                body.configHash(),
                body.idempotencyKey(),
                body.source());
    }
}
