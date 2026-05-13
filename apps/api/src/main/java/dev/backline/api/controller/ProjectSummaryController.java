package dev.backline.api.controller;

import dev.backline.core.api.DataResponse;
import dev.backline.core.api.dto.ProjectSummaryDto;
import dev.backline.api.service.ProjectSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectSummaryController {

    private final ProjectSummaryService projectSummaryService;

    public ProjectSummaryController(ProjectSummaryService projectSummaryService) {
        this.projectSummaryService = projectSummaryService;
    }

    @GetMapping("/{projectId}/summary")
    public DataResponse<ProjectSummaryDto> summary(@PathVariable UUID projectId) {
        return DataResponse.of(projectSummaryService.summarize(projectId));
    }
}
