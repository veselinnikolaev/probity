#requires -Version 5.1
<#
.SYNOPSIS
  Reproducible PR-D0 baseline driver for the async simulation worker.

.DESCRIPTION
  Runs the k6 end-to-end harness (loadtest/k6/baseline.js) against a single
  running app replica and reports a REPRODUCIBLE number instead of a one-shot
  sample:

    1 warm-up run (its result is discarded) + N runs of Scenario A + N runs of
    Scenario B. For each scenario the script prints the MEDIAN and the MIN-MAX
    of throughput, and of accept/e2e/server latency at BOTH p95 and p99
    (server-latency excludes the client status-poll floor; see baseline.js).

  Run independence: by default the script TRUNCATEs the `simulations` table
  before the warm-up AND before every measured run (-ResetBetweenRuns, default
  ON), because each COMPLETED simulation persists a multi-MB result_payload and
  a growing table stalls the completion UPDATE on WAL I/O. Pass
  -ResetBetweenRuns:$false to keep rows between runs (e.g. for a growth test).

  Each run's k6 JSON summary is kept under the temp output directory so raw
  numbers (including every percentile) can be re-inspected.

.PARAMETER PortfolioId
  Seeded portfolio UUID to simulate (required). See loadtest/README.md for how
  to read it from the seeded dev database.

.PARAMETER BaseUrl
  App base URL as seen from the k6 container. Default host.docker.internal:8080
  (Docker Desktop reaches the host-run app).

.EXAMPLE
  ./loadtest/run-baseline.ps1 -PortfolioId 00000000-0000-0000-0000-000000000000
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://host.docker.internal:8080',

    [string]$PortfolioId,

    [string]$User = 'admin',
    # Dev-only default for the seeded local DB; never use outside a local stack.
    [string]$Password = 'Password123!',

    [string]$K6Image = 'grafana/k6',

    [int]$Runs = 3,

    [int]$VusA = 6,
    [int]$IterationsA = 60,

    [int]$VusB = 1,
    [int]$IterationsB = 20,

    [switch]$SkipWarmup,

    # TRUNCATE `simulations` before the warm-up and before every measured run so
    # each run starts from a ~empty table (run independence). Default ON. The old
    # -ResetSimulations spelling is kept as an alias; it too just enables this.
    # Each COMPLETED simulation persists a multi-MB result_payload (all Monte
    # Carlo paths), so without a per-run reset repeated runs bloat the table into
    # the gigabytes and the completion UPDATE stalls on WAL I/O (see
    # docs/PHASE_4_MEASUREMENTS.md).
    [Alias('ResetSimulations')]
    [switch]$ResetBetweenRuns = $true,

    [string]$PostgresContainer = 'probity-postgres-1',
    [string]$DbUser = 'postgres',
    [string]$DbName = 'probity'
)

$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'k6/baseline.js'
if (-not (Test-Path $scriptPath)) {
    throw "baseline.js not found at: $scriptPath"
}
if ([string]::IsNullOrWhiteSpace($PortfolioId)) {
    throw "Provide -PortfolioId <uuid>. See loadtest/README.md for how to read it from the seeded DB."
}

$outDir = Join-Path ([System.IO.Path]::GetTempPath()) 'probity-baseline'
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
# Docker Desktop accepts forward slashes for the bind source.
$mountSource = ($outDir -replace '\\', '/')

function Reset-Simulations {
    Write-Host ''
    Write-Host 'Resetting simulations (TRUNCATE via docker exec)...' -ForegroundColor Yellow
    docker exec $PostgresContainer psql -U $DbUser -d $DbName -c 'TRUNCATE TABLE simulations;' | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "TRUNCATE TABLE simulations failed (docker exec $PostgresContainer exit $LASTEXITCODE)"
    }
}

function Invoke-K6Run {
    param(
        [Parameter(Mandatory)][string]$Label,
        [Parameter(Mandatory)][int]$Vus,
        [Parameter(Mandatory)][int]$Iterations
    )

    $fileName = "summary-$([guid]::NewGuid().ToString('N')).json"
    $hostSummary = Join-Path $outDir $fileName
    $containerSummary = "/k6out/$fileName"

    Write-Host ''
    Write-Host "--- $Label  (VUs=$Vus, iterations=$Iterations) ---" -ForegroundColor Cyan

    # --summary-export is deprecated in newer k6 releases but still works with the
    # image this repo recorded against (grafana/k6 v2.3.0; see
    # docs/PHASE_4_MEASUREMENTS.md). Keep a pinned image, not :latest.
    Get-Content $scriptPath -Raw | docker run --rm -i `
        -v "${mountSource}:/k6out" `
        $K6Image run `
        --summary-export=$containerSummary `
        -e BASE_URL=$BaseUrl `
        -e PORTFOLIO_ID=$PortfolioId `
        -e USER=$User `
        -e PASSWORD=$Password `
        -e VUS=$Vus `
        -e ITERATIONS=$Iterations `
        - | Out-Host

    if ($LASTEXITCODE -ne 0) { throw "k6 run '$Label' exited with code $LASTEXITCODE" }
    if (-not (Test-Path $hostSummary)) { throw "k6 did not write a summary file for '$Label'" }

    $json = Get-Content $hostSummary -Raw | ConvertFrom-Json
    $m = $json.metrics

    # k6 v1 flattened the stat map directly onto the metric object; older versions nest
    # it under "values". Accept either so the script is not tied to one k6 release.
    function Get-MetricValues($metric) {
        if ($null -eq $metric) { return $null }
        if ($metric.PSObject.Properties.Name -contains 'values') { return $metric.values }
        return $metric
    }

    $completedValues = Get-MetricValues $m.simulations_completed
    $acceptValues    = Get-MetricValues $m.accept_latency_ms
    $e2eValues       = Get-MetricValues $m.e2e_latency_ms
    $serverValues    = Get-MetricValues $m.server_latency_ms

    $acceptP95 = if ($null -ne $acceptValues) { [double]$acceptValues.'p(95)' } else { $null }
    $acceptP99 = if ($null -ne $acceptValues) { [double]$acceptValues.'p(99)' } else { $null }
    $e2eP95    = if ($null -ne $e2eValues)    { [double]$e2eValues.'p(95)' }    else { $null }
    $e2eP99    = if ($null -ne $e2eValues)    { [double]$e2eValues.'p(99)' }    else { $null }
    $serverP95 = if ($null -ne $serverValues) { [double]$serverValues.'p(95)' } else { $null }
    $serverP99 = if ($null -ne $serverValues) { [double]$serverValues.'p(99)' } else { $null }

    [pscustomobject]@{
        Label      = $Label
        Throughput = [double]$completedValues.rate
        AcceptP95  = $acceptP95
        AcceptP99  = $acceptP99
        E2eP95     = $e2eP95
        E2eP99     = $e2eP99
        ServerP95  = $serverP95
        ServerP99  = $serverP99
    }
}

function Get-Distribution {
    param([Parameter(Mandatory)][double[]]$Values)
    $sorted = @($Values | Sort-Object)
    $n = $sorted.Count
    $mid = [math]::Floor($n / 2)
    $median = if ($n % 2 -eq 1) { $sorted[$mid] } else { ($sorted[$mid - 1] + $sorted[$mid]) / 2.0 }
    [pscustomobject]@{ Median = [double]$median; Min = [double]$sorted[0]; Max = [double]$sorted[-1] }
}

function Format-Triple {
    param($Dist, [string]$Format, [string]$Unit)
    if ($null -eq $Dist) { return 'n/a' }
    '{0}  (min {1}, max {2}){3}' -f ($Dist.Median.ToString($Format)), ($Dist.Min.ToString($Format)), ($Dist.Max.ToString($Format)), $Unit
}

function Show-Scenario {
    param([string]$Title, [object[]]$RunsData)
    Write-Host ''
    Write-Host $Title -ForegroundColor Green

    $throughput = Get-Distribution @($RunsData | ForEach-Object { $_.Throughput })
    $acceptP95  = Get-Distribution @($RunsData | Where-Object { $null -ne $_.AcceptP95 } | ForEach-Object { $_.AcceptP95 })
    $acceptP99  = Get-Distribution @($RunsData | Where-Object { $null -ne $_.AcceptP99 } | ForEach-Object { $_.AcceptP99 })
    $e2eP95     = Get-Distribution @($RunsData | ForEach-Object { $_.E2eP95 })
    $e2eP99     = Get-Distribution @($RunsData | ForEach-Object { $_.E2eP99 })
    $serverP95  = Get-Distribution @($RunsData | Where-Object { $null -ne $_.ServerP95 } | ForEach-Object { $_.ServerP95 })
    $serverP99  = Get-Distribution @($RunsData | Where-Object { $null -ne $_.ServerP99 } | ForEach-Object { $_.ServerP99 })

    Write-Host ('  throughput (sims/s) : ' + (Format-Triple $throughput '0.000' ''))
    Write-Host ('  accept p95 (ms)     : ' + (Format-Triple $acceptP95 '0.0' ''))
    Write-Host ('  accept p99 (ms)     : ' + (Format-Triple $acceptP99 '0.0' ''))
    Write-Host ('  e2e p95 (ms)        : ' + (Format-Triple $e2eP95 '0.0' ''))
    Write-Host ('  e2e p99 (ms)        : ' + (Format-Triple $e2eP99 '0.0' ''))
    Write-Host ('  server p95 (ms)     : ' + (Format-Triple $serverP95 '0.0' ''))
    Write-Host ('  server p99 (ms)     : ' + (Format-Triple $serverP99 '0.0' ''))
}

Write-Host 'PR-D0 baseline driver' -ForegroundColor Green
Write-Host "  base URL   : $BaseUrl"
Write-Host "  portfolio  : $PortfolioId"
Write-Host "  k6 image   : $K6Image"
Write-Host "  runs       : $Runs per scenario (+1 discarded warm-up)"
Write-Host "  reset      : $ResetBetweenRuns (TRUNCATE before warm-up and before each run)"
Write-Host "  output dir : $outDir"

if ($ResetBetweenRuns) {
    Reset-Simulations
}

if (-not $SkipWarmup) {
    Write-Host ''
    Write-Host 'Warm-up run (discarded)...' -ForegroundColor Yellow
    Invoke-K6Run -Label 'warm-up' -Vus $VusA -Iterations $IterationsA | Out-Null
}

$scenarioA = @()
for ($i = 1; $i -le $Runs; $i++) {
    if ($ResetBetweenRuns) { Reset-Simulations }
    $scenarioA += Invoke-K6Run -Label "A$i" -Vus $VusA -Iterations $IterationsA
}

$scenarioB = @()
for ($i = 1; $i -le $Runs; $i++) {
    if ($ResetBetweenRuns) { Reset-Simulations }
    $scenarioB += Invoke-K6Run -Label "B$i" -Vus $VusB -Iterations $IterationsB
}

Show-Scenario "Scenario A - concurrency $VusA, $IterationsA simulations ($Runs runs)" $scenarioA
Show-Scenario "Scenario B - concurrency $VusB, $IterationsB simulations ($Runs runs)" $scenarioB

Write-Host ''
Write-Host 'Per-run raw values:' -ForegroundColor Green
foreach ($r in ($scenarioA + $scenarioB)) {
    $a95  = if ($null -ne $r.AcceptP95) { $r.AcceptP95.ToString('0.0') } else { 'n/a' }
    $a99  = if ($null -ne $r.AcceptP99) { $r.AcceptP99.ToString('0.0') } else { 'n/a' }
    $e95  = if ($null -ne $r.E2eP95)    { $r.E2eP95.ToString('0.0') }    else { 'n/a' }
    $e99  = if ($null -ne $r.E2eP99)    { $r.E2eP99.ToString('0.0') }    else { 'n/a' }
    $s95  = if ($null -ne $r.ServerP95) { $r.ServerP95.ToString('0.0') } else { 'n/a' }
    $s99  = if ($null -ne $r.ServerP99) { $r.ServerP99.ToString('0.0') } else { 'n/a' }
    Write-Host ("  {0,-6}  throughput={1,7:0.000}/s  accept_p95={2,7}ms  accept_p99={3,7}ms  e2e_p95={4,8}ms  e2e_p99={5,8}ms  server_p95={6,7}ms  server_p99={7,7}ms" -f `
        $r.Label, $r.Throughput, $a95, $a99, $e95, $e99, $s95, $s99)
}
Write-Host ''
Write-Host "Raw k6 summaries: $outDir" -ForegroundColor DarkGray