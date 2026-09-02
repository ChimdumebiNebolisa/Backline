param(
  [ValidateSet("safe", "mixed-failure", "rollback-failure", "prepared-rollback", "handoff", "candidate-only-failure", "all")]
  [string]$Case = "all"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$binary = Join-Path ([System.IO.Path]::GetTempPath()) ("backline-demo-" + [guid]::NewGuid().ToString("N") + ".exe")
try {
  Push-Location $root
  go build -o $binary ./cmd/backline
  $cases = if ($Case -eq "all") { @("safe", "mixed-failure", "rollback-failure") } else { @($Case) }
  foreach ($name in $cases) {
    $arguments = @("run", "./examples/rollout-demo/harness", "--case", $name, "--backline", $binary, "--fixture", "./examples/rollout-demo/fixture")
    if ($env:BACKLINE_DEMO_ARTIFACT_ROOT) {
      $arguments += @("--artifact-root", $env:BACKLINE_DEMO_ARTIFACT_ROOT)
    }
    & go @arguments
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
}
finally {
  Pop-Location
  Remove-Item -LiteralPath $binary -Force -ErrorAction SilentlyContinue
}
