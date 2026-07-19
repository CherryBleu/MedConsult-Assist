param(
    [int]$Port = 8083,
    [string]$ProbePath = '/v3/api-docs'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$modulePom = 'backend/outpatient-service/pom.xml'
$logToken = '{0}-{1}' -f $Port, (Get-Date -Format 'yyyyMMdd-HHmmss')
$stdoutLog = Join-Path $repoRoot ("backend\outpatient-service\target\spring-boot-run.$logToken.stdout.log")
$stderrLog = Join-Path $repoRoot ("backend\outpatient-service\target\spring-boot-run.$logToken.stderr.log")
$probeUri = "http://127.0.0.1:$Port$ProbePath"

if (Test-Path $stdoutLog) { Remove-Item $stdoutLog -Force }
if (Test-Path $stderrLog) { Remove-Item $stderrLog -Force }

$process = Start-Process -FilePath 'mvn' `
    -ArgumentList '-f', $modulePom, '-DskipTests', "-Dspring-boot.run.arguments=--server.port=$Port", 'spring-boot:run' `
    -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru `
    -WindowStyle Hidden

$started = $false
$portOpen = $false
$httpProbePassed = $false
$httpProbeStatus = $null

try {
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 1
        $process.Refresh()
        $stdout = if (Test-Path $stdoutLog) { Get-Content -Path $stdoutLog -Raw } else { '' }
        $stderr = if (Test-Path $stderrLog) { Get-Content -Path $stderrLog -Raw } else { '' }
        $combined = $stdout + "`n" + $stderr

        if ($combined -match 'Started OutpatientServiceApplication' -or $combined -match "Tomcat started on port $Port") {
            $started = $true
            $portOpen = Test-NetConnection -ComputerName '127.0.0.1' -Port $Port -InformationLevel Quiet
            if ($portOpen) {
                try {
                    $response = Invoke-WebRequest -Uri $probeUri -UseBasicParsing -TimeoutSec 10
                    $httpProbeStatus = [int]$response.StatusCode
                    $httpProbePassed = $response.StatusCode -ge 200 -and $response.StatusCode -lt 300
                }
                catch {
                    if ($_.Exception.Response) {
                        $httpProbeStatus = [int]$_.Exception.Response.StatusCode
                    }
                }

                if ($httpProbePassed) {
                    break
                }
            }
        }

        if ($combined -match 'BUILD FAILURE' -or
            $combined -match 'Application run failed' -or
            $combined -match 'APPLICATION FAILED TO START' -or
            $combined -match 'Exception encountered during context initialization') {
            break
        }

        if ($process.HasExited) {
            break
        }
    }

    $stdoutTail = if (Test-Path $stdoutLog) { Get-Content -Path $stdoutLog -Tail 120 | Out-String } else { '' }
    $stderrTail = if (Test-Path $stderrLog) { Get-Content -Path $stderrLog -Tail 120 | Out-String } else { '' }

    if ($started -and $portOpen -and $httpProbePassed) {
        Write-Output 'SPRING_BOOT_RUN_VERIFIED'
        Write-Output ("LOG_STDOUT={0}" -f $stdoutLog)
        Write-Output ("LOG_STDERR={0}" -f $stderrLog)
        Write-Output ("HTTP_PROBE_URI={0}" -f $probeUri)
        Write-Output ("HTTP_PROBE_STATUS={0}" -f $httpProbeStatus)
        Write-Output $stdoutTail
        if ($stderrTail.Trim()) {
            Write-Output 'STDERR:'
            Write-Output $stderrTail
        }
        exit 0
    }

    Write-Output 'SPRING_BOOT_RUN_NOT_VERIFIED'
    Write-Output ("LOG_STDOUT={0}" -f $stdoutLog)
    Write-Output ("LOG_STDERR={0}" -f $stderrLog)
    Write-Output ("HTTP_PROBE_URI={0}" -f $probeUri)
    Write-Output ("HTTP_PROBE_STATUS={0}" -f $(if ($null -ne $httpProbeStatus) { $httpProbeStatus } else { 'NOT_REACHED' }))
    Write-Output $stdoutTail
    if ($stderrTail.Trim()) {
        Write-Output 'STDERR:'
        Write-Output $stderrTail
    }
    exit 1
}
finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        Wait-Process -Id $process.Id -Timeout 5 -ErrorAction SilentlyContinue
    }
}
