$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Path (Split-Path -Path $PSScriptRoot -Parent) -Parent
$restartScript = Join-Path $PSScriptRoot 'restart_backend_jars.ps1'
$requiredPorts = 8080..8087

function Assert-AllPortsListening {
    param(
        [string] $Phase
    )

    $listeningPorts = Get-NetTCPConnection -State Listen -ErrorAction Stop |
        Where-Object { $_.LocalPort -in $requiredPorts } |
        Select-Object -ExpandProperty LocalPort -Unique

    $missing = $requiredPorts | Where-Object { $_ -notin $listeningPorts }
    if ($missing) {
        throw "$Phase missing listening ports: $($missing -join ', ')"
    }
}

function Invoke-RestartRound {
    param(
        [int] $Round
    )

    Write-Host "=== Restart round $Round ==="
    & powershell -ExecutionPolicy Bypass -File $restartScript 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "restart_backend_jars.ps1 failed in round $Round with exit code $LASTEXITCODE"
    }
    Assert-AllPortsListening -Phase "round $Round"
}

Set-Location $repoRoot
Invoke-RestartRound -Round 1
Invoke-RestartRound -Round 2
Write-Host 'restart_backend_jars.ps1 is stable across two consecutive runs.'
