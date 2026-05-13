package dev.backline.api.controller;

import dev.backline.core.api.DataResponse;
import dev.backline.core.api.ListResponse;
import dev.backline.core.api.PageMeta;
import dev.backline.core.api.dto.CreateProjectRequest;
import dev.backline.core.api.dto.ProjectDto;
import dev.backline.api.dto.CreateProjectBody;
import dev.backline.api.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Validated
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<DataResponse<ProjectDto>> create(@Valid @RequestBody CreateProjectBody body) {
        var dto = projectService.create(new CreateProjectRequest(body.slug(), body.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DataResponse.of(dto));
    }

    @GetMapping
    public ListResponse<ProjectDto> list(
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        var page = projectService.list(limit, offset);
        return ListResponse.of(page.getContent(), new PageMeta(limit, offset, page.getTotalElements()));
    }

    @GetMapping("/{projectId}")
    public DataResponse<ProjectDto> get(@PathVariable UUID projectId) {
        return DataResponse.of(projectService.findById(projectId));
    }
}
