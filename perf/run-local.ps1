[CmdletBinding()]
param(
    [ValidateSet("smoke", "small", "full", "multi-worker")]
    [string]$Profile = "smoke",

    [switch]$SkipComposeUp,

    [switch]$SkipCliBuild,

    [switch]$LeaveServicesRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$RepoRoot = Split-Path -Parent $PSScriptRoot

$ComposeArgs = @(
    "compose",
    "-f",
    "docker-compose.yml",
    "-f",
    "perf/docker-compose.perf.yml"
)

$Config = switch ($Profile) {
    "smoke" {
        @{
            Label = "smoke"
            WorkerScale = 1
            HealthVus = 2
            HealthDuration = "10s"
            SubmissionVus = 3
            SubmissionIterations = 8
            SubmissionMaxDuration = "30s"
            SlowVus = 2
            SlowDuration = "8s"
            BrokenVus = 2
            BrokenDuration = "8s"
            QueueRuns = 4
            QueueTimeoutSeconds = 90
        }
    }
    "small" {
        @{
            Label = "small"
            WorkerScale = 1
            HealthVus = 5
            HealthDuration = "20s"
            SubmissionVus = 6
            SubmissionIterations = 24
            SubmissionMaxDuration = "45s"
            SlowVus = 4
            SlowDuration = "15s"
            BrokenVus = 4
            BrokenDuration = "15s"
            QueueRuns = 8
            QueueTimeoutSeconds = 120
        }
    }
    "multi-worker" {
        @{
            Label = "multi-worker"
            WorkerScale = 2
            HealthVus = 5
            HealthDuration = "20s"
            SubmissionVus = 8
            SubmissionIterations = 32
            SubmissionMaxDuration = "60s"
            SlowVus = 4
            SlowDuration = "15s"
            BrokenVus = 4
            BrokenDuration = "15s"
            QueueRuns = 16
            QueueTimeoutSeconds = 180
            MinimumClaimWorkers = 2
        }
    }
    default {
        @{
            Label = "full"
            WorkerScale = 1
            HealthVus = 8
            HealthDuration = "30s"
            SubmissionVus = 8
            SubmissionIterations = 40
            SubmissionMaxDuration = "60s"
            SlowVus = 6
            SlowDuration = "20s"
            BrokenVus = 6
            BrokenDuration = "20s"
            QueueRuns = 12
            QueueTimeoutSeconds = 180
        }
    }
}

function Invoke-DockerCompose {
    param([string[]]$ExtraArgs)

    & docker @ComposeArgs @ExtraArgs
    if ($LASTEXITCODE -ne 0) {
        $allArgs = @($ComposeArgs + $ExtraArgs)
        throw "docker compose command failed: docker $($allArgs -join ' ')"
    }
}

function Wait-ForHttpOk {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(10)
    do {
        try {
            $response = $client.GetAsync($Url).GetAwaiter().GetResult()
            if ([int]$response.StatusCode -eq 200) {
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "$Name did not become healthy at $Url within $TimeoutSeconds seconds"
}

function Invoke-K6Script {
    param(
        [string]$ScriptName,
        [hashtable]$Environment
    )

    $extraArgs = @("--profile", "demo", "--profile", "perf", "run", "--no-deps", "--rm")
    foreach ($key in $Environment.Keys) {
        $extraArgs += "-e"
        $extraArgs += ("{0}={1}" -f $key, $Environment[$key])
    }
    $extraArgs += "k6"
    $extraArgs += "run"
    $extraArgs += ("/perf/k6/{0}" -f $ScriptName)
    Invoke-DockerCompose -ExtraArgs $extraArgs
}

try {
    Push-Location $RepoRoot
    try {
        if (-not $SkipComposeUp) {
            Invoke-DockerCompose -ExtraArgs @("--profile", "demo", "up", "--build", "--scale", ("worker={0}" -f $Config.WorkerScale), "-d", "postgres", "api", "worker", "sample-api")
        }

        Wait-ForHttpOk -Name "Backline API actuator" -Url "http://localhost:8080/actuator/health"
        Wait-ForHttpOk -Name "Backline API health" -Url "http://localhost:8080/api/health"
        Wait-ForHttpOk -Name "Sample API health" -Url "http://localhost:8081/health"

        if (-not $SkipCliBuild) {
            $gradlePath = Join-Path $RepoRoot "gradlew.bat"
            & $gradlePath :apps:cli:installDist
            if ($LASTEXITCODE -ne 0) {
                throw "CLI build failed"
            }
        }

        Invoke-K6Script -ScriptName "api-health.js" -Environment @{
            PERF_LABEL      = $Config.Label
            HEALTH_VUS      = $Config.HealthVus
            HEALTH_DURATION = $Config.HealthDuration
        }
        Invoke-K6Script -ScriptName "concurrent-run-submissions.js" -Environment @{
            PERF_LABEL              = $Config.Label
            SUBMISSION_VUS          = $Config.SubmissionVus
            SUBMISSION_ITERATIONS   = $Config.SubmissionIterations
            SUBMISSION_MAX_DURATION = $Config.SubmissionMaxDuration
        }
        Invoke-K6Script -ScriptName "slow-endpoint.js" -Environment @{
            PERF_LABEL    = $Config.Label
            SLOW_VUS      = $Config.SlowVus
            SLOW_DURATION = $Config.SlowDuration
        }
        Invoke-K6Script -ScriptName "broken-endpoint.js" -Environment @{
            PERF_LABEL      = $Config.Label
            BROKEN_VUS      = $Config.BrokenVus
            BROKEN_DURATION = $Config.BrokenDuration
        }

        & (Join-Path $PSScriptRoot "scripts\queue-load.ps1") `
            -RepoRoot $RepoRoot `
            -ApiUrl "http://localhost:8080" `
            -Label $Config.Label `
            -RunCount $Config.QueueRuns `
            -TimeoutSeconds $Config.QueueTimeoutSeconds `
            -MinimumClaimWorkers $(if ($Config.ContainsKey("MinimumClaimWorkers")) { $Config.MinimumClaimWorkers } else { 1 })
        if ($LASTEXITCODE -ne 0) {
            throw "Queue load verification script failed"
        }

    } finally {
        Pop-Location
    }
} finally {
    if (-not $LeaveServicesRunning) {
        Push-Location $RepoRoot
        try {
            Invoke-DockerCompose -ExtraArgs @("--profile", "demo", "down")
        } catch {
        } finally {
            Pop-Location
        }
    }
}
