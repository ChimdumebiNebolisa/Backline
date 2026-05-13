package dev.backline.reporting;

import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.ProjectDto;
import dev.backline.core.api.dto.ProjectSummaryDto;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDto;

import java.time.Instant;
import java.util.List;

public record ReportInputs(
        RunDto run,
        ProjectDto project,
        List<CheckResultDto> results,
        RunDiffDto diff,
        ProjectSummaryDto summary,
        Instant generatedAt) {}
