[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoRoot,

    [string]$ApiUrl = "http://localhost:8080",

    [string]$Label = "adhoc",

    [int]$RunCount = 8,

    [int]$TimeoutSeconds = 120,

    [int]$PollIntervalMs = 1000,

    [int]$MinimumClaimWorkers = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$PerfRoot = Join-Path $RepoRoot "perf"
$OutDir = Join-Path $PerfRoot "out"
$ProjectsPath = Join-Path $PerfRoot "data\projects.json"
$CliPath = Join-Path $RepoRoot "apps\cli\build\install\backline\bin\backline.bat"
$SummaryPath = Join-Path $OutDir ("queue-load-{0}.json" -f $Label)
$HistoryPath = Join-Path $OutDir ("history-{0}.txt" -f $Label)
$HistoryRelativePath = Join-Path "perf\out" ("history-{0}.txt" -f $Label)

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not (Test-Path $ProjectsPath)) {
    throw "Missing project definitions at $ProjectsPath"
}
if (-not (Test-Path $CliPath)) {
    throw "CLI launcher not found at $CliPath. Build it with .\gradlew.bat :apps:cli:installDist"
}

$Projects = Get-Content $ProjectsPath -Raw | ConvertFrom-Json
$Project = $Projects.queue
if ($null -eq $Project) {
    throw "Queue project definition not found in $ProjectsPath"
}

$TerminalStatuses = @("PASSED", "FAILED", "ERROR", "CANCELLED")
$HttpClient = [System.Net.Http.HttpClient]::new()
$HttpClient.Timeout = [TimeSpan]::FromSeconds(30)
$ApiBaseUri = [Uri]($ApiUrl.TrimEnd("/"))

function Invoke-BacklineApi {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [object]$Body
    )

    $uri = [Uri]::new($ApiBaseUri, $Path)
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $uri)
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 20 -Compress
        $request.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
    }

    $response = $HttpClient.SendAsync($request).GetAwaiter().GetResult()
    $rawBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $jsonBody = $null
    if (-not [string]::IsNullOrWhiteSpace($rawBody)) {
        try {
            $jsonBody = $rawBody | ConvertFrom-Json
        } catch {
            $jsonBody = $null
        }
    }

    [pscustomobject]@{
        StatusCode = [int]$response.StatusCode
        Body       = $rawBody
        Json       = $jsonBody
    }
}

function Get-JsonPropertyValue {
    param(
        [object]$Object,
        [string]$PropertyName
    )

    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$PropertyName]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Ensure-ProjectAndChecks {
    param([object]$ProjectDefinition)

    $projectResponse = Invoke-BacklineApi -Method "POST" -Path "/api/projects" -Body @{
        slug = $ProjectDefinition.slug
        name = $ProjectDefinition.name
    }
    if ($projectResponse.StatusCode -notin @(201, 409)) {
        throw "Project upsert failed with HTTP $($projectResponse.StatusCode): $($projectResponse.Body)"
    }

    $syncResponse = Invoke-BacklineApi -Method "POST" -Path "/api/checks/sync" -Body @{
        projectSlug = $ProjectDefinition.slug
        projectName = $ProjectDefinition.name
        checks      = $ProjectDefinition.checks
    }
    if ($syncResponse.StatusCode -lt 200 -or $syncResponse.StatusCode -ge 300) {
        throw "Check sync failed with HTTP $($syncResponse.StatusCode): $($syncResponse.Body)"
    }
}

function Submit-Runs {
    param(
        [object]$ProjectDefinition,
        [int]$Count
    )

    $submitted = New-Object System.Collections.Generic.List[object]
    for ($i = 0; $i -lt $Count; $i++) {
        $idempotencyKey = "{0}-{1}-{2}" -f $Label, $i, ([Guid]::NewGuid().ToString("N"))
        $response = Invoke-BacklineApi -Method "POST" -Path "/api/runs" -Body @{
            projectSlug    = $ProjectDefinition.slug
            environment    = $ProjectDefinition.environment
            configHash     = $ProjectDefinition.configHash
            idempotencyKey = $idempotencyKey
            source         = "perf-$Label"
        }
        if ($response.StatusCode -ne 201) {
            throw "Run submission failed with HTTP $($response.StatusCode): $($response.Body)"
        }
        $submitted.Add([pscustomobject]@{
                runId           = $response.Json.data.id
                idempotencyKey  = $idempotencyKey
                initialStatus   = $response.Json.data.status
            })
    }
    return $submitted
}

function Wait-ForTerminalRuns {
    param(
        [System.Collections.Generic.List[object]]$SubmittedRuns,
        [int]$TimeoutSecondsValue,
        [int]$SleepMs
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSecondsValue)
    $state = @{}
    foreach ($run in $SubmittedRuns) {
        $state[$run.runId] = [pscustomobject]@{
            runId          = $run.runId
            status         = $run.initialStatus
            initialStatus  = $run.initialStatus
            finishedAt     = $null
            resultCount    = 0
            pollCount      = 0
        }
    }

    do {
        $pending = 0
        foreach ($runId in @($state.Keys)) {
            $entry = $state[$runId]
            if ($TerminalStatuses -contains $entry.status) {
                continue
            }

            $response = Invoke-BacklineApi -Method "GET" -Path "/api/runs/$runId"
            if ($response.StatusCode -ne 200) {
                throw "Run lookup failed for $runId with HTTP $($response.StatusCode): $($response.Body)"
            }
            $entry.status = $response.Json.data.status
            $entry.finishedAt = Get-JsonPropertyValue -Object $response.Json.data -PropertyName "finishedAt"
            $entry.pollCount++
            if ($TerminalStatuses -notcontains $entry.status) {
                $pending++
            }
        }

        if ($pending -eq 0) {
            break
        }
        if ((Get-Date) -gt $deadline) {
            break
        }
        Start-Sleep -Milliseconds $SleepMs
    } while ($true)

    return $state
}

function Get-RunResults {
    param([string]$RunId)

    $response = Invoke-BacklineApi -Method "GET" -Path "/api/runs/$RunId/results"
    if ($response.StatusCode -ne 200) {
        throw "Run results lookup failed for $RunId with HTTP $($response.StatusCode): $($response.Body)"
    }
    return @($response.Json.data)
}

function Invoke-BacklineCli {
    param([string[]]$Arguments)

    $output = & $CliPath @Arguments 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output   = $output
    }
}

function Invoke-ComposeSql {
    param([string]$Sql)

    $dockerArgs = @(
        "compose",
        "-f",
        "docker-compose.yml",
        "-f",
        "perf/docker-compose.perf.yml",
        "exec",
        "-T",
        "postgres",
        "psql",
        "-U",
        "backline",
        "-d",
        "backline",
        "-t",
        "-A",
        "-F",
        ",",
        "-c",
        $Sql
    )
    $output = & docker @dockerArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "psql query failed: $output"
    }
    return ,@($output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Convert-ClaimWorkerRows {
    param([string[]]$Rows)

    $workers = New-Object System.Collections.Generic.List[object]
    foreach ($row in $Rows) {
        $parts = $row -split ",", 2
        if ($parts.Count -lt 2) {
            continue
        }
        $workers.Add([pscustomobject]@{
                workerId   = $parts[0]
                claimCount = [int]$parts[1]
            })
    }
    return $workers
}

Push-Location $RepoRoot
try {
    Ensure-ProjectAndChecks -ProjectDefinition $Project
    $SubmittedRuns = Submit-Runs -ProjectDefinition $Project -Count $RunCount

    $RunState = Wait-ForTerminalRuns -SubmittedRuns $SubmittedRuns -TimeoutSecondsValue $TimeoutSeconds -SleepMs $PollIntervalMs
    $ExpectedResultCount = @($Project.checks).Count
    $ResultCountMismatches = @()
    $TerminalSummary = @{}
    $FinalRuns = New-Object System.Collections.Generic.List[object]

    foreach ($run in $SubmittedRuns) {
        $entry = $RunState[$run.runId]
        $results = Get-RunResults -RunId $run.runId
        $entry.resultCount = @($results).Count
        if ($TerminalSummary.ContainsKey($entry.status)) {
            $TerminalSummary[$entry.status] = $TerminalSummary[$entry.status] + 1
        } else {
            $TerminalSummary[$entry.status] = 1
        }
        if ($entry.resultCount -ne $ExpectedResultCount) {
            $ResultCountMismatches += [pscustomobject]@{
                runId         = $run.runId
                status        = $entry.status
                expectedCount = $ExpectedResultCount
                actualCount   = $entry.resultCount
            }
        }
        $FinalRuns.Add([pscustomobject]@{
                runId         = $run.runId
                status        = $entry.status
                initialStatus = $entry.initialStatus
                finishedAt    = $entry.finishedAt
                pollCount     = $entry.pollCount
                resultCount   = $entry.resultCount
            })
    }

    $RunIdSqlList = ($SubmittedRuns | ForEach-Object { "'" + $_.runId + "'" }) -join ","
    $StuckRows = @()
    $DuplicateRows = @()
    $RepeatedClaimRows = @()
    $EventRows = @()
    $ClaimWorkerRows = @()
    if (-not [string]::IsNullOrWhiteSpace($RunIdSqlList)) {
        $StuckRows = Invoke-ComposeSql -Sql "SELECT id::text, status FROM runs WHERE id IN ($RunIdSqlList) AND status IN ('QUEUED', 'RUNNING') ORDER BY queued_at;"
        $DuplicateRows = Invoke-ComposeSql -Sql "SELECT run_id::text, check_key, COUNT(*) FROM check_results WHERE run_id IN ($RunIdSqlList) GROUP BY run_id, check_key HAVING COUNT(*) > 1 ORDER BY run_id, check_key;"
        $RepeatedClaimRows = Invoke-ComposeSql -Sql "SELECT run_id::text, COUNT(*) FILTER (WHERE event_type = 'CLAIMED') AS claim_count, COUNT(*) FILTER (WHERE event_type = 'RETRY_SCHEDULED') AS retry_count FROM run_events WHERE run_id IN ($RunIdSqlList) GROUP BY run_id HAVING COUNT(*) FILTER (WHERE event_type = 'CLAIMED') > 1 OR COUNT(*) FILTER (WHERE event_type = 'RETRY_SCHEDULED') > 0 ORDER BY run_id;"
        $EventRows = Invoke-ComposeSql -Sql "SELECT run_id::text, event_type, COUNT(*) FROM run_events WHERE run_id IN ($RunIdSqlList) GROUP BY run_id, event_type ORDER BY run_id, event_type;"
        $ClaimWorkerRows = Invoke-ComposeSql -Sql "SELECT trim(replace(message, 'Run claimed by worker ', '')) AS worker_id, COUNT(*) FROM run_events WHERE run_id IN ($RunIdSqlList) AND event_type = 'CLAIMED' GROUP BY worker_id ORDER BY worker_id;"
    }
    $ClaimWorkers = Convert-ClaimWorkerRows -Rows $ClaimWorkerRows
    $DistinctClaimWorkers = @($ClaimWorkers).Count

    $HistoryResult = Invoke-BacklineCli -Arguments @("--api-url", $ApiUrl, "history", "--project", $Project.slug, "--environment", $Project.environment, "--limit", [string][Math]::Max($RunCount, 25))
    $HistoryResult.Output | Set-Content -Path $HistoryPath
    if ($HistoryResult.ExitCode -ne 0) {
        throw "History command failed: $($HistoryResult.Output)"
    }

    $MissingFromHistory = @()
    foreach ($run in $SubmittedRuns) {
        if ($HistoryResult.Output -notmatch [Regex]::Escape($run.runId)) {
            $MissingFromHistory += $run.runId
        }
    }

    $ReportRelativeFiles = New-Object System.Collections.Generic.List[string]
    $RunsForReports = $SubmittedRuns | Select-Object -Last ([Math]::Min(2, $SubmittedRuns.Count))
    foreach ($run in $RunsForReports) {
        $reportPath = Join-Path $OutDir ("report-{0}-{1}.md" -f $Label, $run.runId)
        $reportRelativePath = Join-Path "perf\out" ("report-{0}-{1}.md" -f $Label, $run.runId)
        $reportResult = Invoke-BacklineCli -Arguments @("--api-url", $ApiUrl, "report", $run.runId, "-o", $reportPath)
        if ($reportResult.ExitCode -ne 0) {
            throw "Report command failed for run $($run.runId): $($reportResult.Output)"
        }
        if (-not (Test-Path $reportPath)) {
            throw "Report file was not created for run $($run.runId): $reportPath"
        }
        if ((Get-Item $reportPath).Length -le 0) {
            throw "Report file is empty for run $($run.runId): $reportPath"
        }
        $ReportRelativeFiles.Add($reportRelativePath)
    }

    $Failures = New-Object System.Collections.Generic.List[string]
    if ($StuckRows.Count -gt 0) {
        $Failures.Add("jobs stuck in QUEUED or RUNNING")
    }
    if ($DuplicateRows.Count -gt 0) {
        $Failures.Add("duplicate check_results rows detected")
    }
    if ($RepeatedClaimRows.Count -gt 0) {
        $Failures.Add("runs were claimed or retried more than once during stable local execution")
    }
    if ($ResultCountMismatches.Count -gt 0) {
        $Failures.Add("one or more runs did not produce the expected number of check results")
    }
    if ($MissingFromHistory.Count -gt 0) {
        $Failures.Add("history output did not include all submitted runs")
    }
    if ($DistinctClaimWorkers -lt $MinimumClaimWorkers) {
        $Failures.Add("submitted runs were not claimed by the required number of worker instances")
    }
    $NonTerminalRuns = @($FinalRuns | Where-Object { $TerminalStatuses -notcontains $_.status })
    if ($NonTerminalRuns.Count -gt 0) {
        $Failures.Add("one or more submitted runs never reached a terminal status")
    }

    $Summary = [pscustomobject]@{
        label                 = $Label
        apiUrl                = $ApiUrl
        projectSlug           = $Project.slug
        runCount              = $RunCount
        expectedCheckCount    = $ExpectedResultCount
        minimumClaimWorkers   = $MinimumClaimWorkers
        distinctClaimWorkers  = $DistinctClaimWorkers
        claimWorkerCounts     = $ClaimWorkers
        submittedRuns         = $FinalRuns
        finalStatusCounts     = $TerminalSummary
        historyFile           = $HistoryRelativePath
        reportFiles           = $ReportRelativeFiles
        stuckRows             = $StuckRows
        duplicateRows         = $DuplicateRows
        repeatedClaimRows     = $RepeatedClaimRows
        eventRows             = $EventRows
        resultCountMismatches = $ResultCountMismatches
        missingFromHistory    = $MissingFromHistory
        failures              = $Failures
    }

    $Summary | ConvertTo-Json -Depth 8 | Set-Content -Path $SummaryPath

    if ($Failures.Count -gt 0) {
        throw ("Queue load verification failed: " + ($Failures -join "; "))
    }
} finally {
    Pop-Location
}
