$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Path (Split-Path -Path $PSScriptRoot -Parent) -Parent
$backendRoot = Join-Path $repoRoot 'backend'
$logDir = Join-Path $backendRoot '.runtime-logs'

$services = @(
    @{ Name = 'auth-service'; Port = 8081; Jar = 'auth-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'patient-service'; Port = 8082; Jar = 'patient-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'outpatient-service'; Port = 8083; Jar = 'outpatient-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'medical-record-service'; Port = 8084; Jar = 'medical-record-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'drug-service'; Port = 8085; Jar = 'drug-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'ai-service'; Port = 8086; Jar = 'ai-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'notification-service'; Port = 8087; Jar = 'notification-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'gateway'; Port = 8080; Jar = 'gateway-0.1.0-SNAPSHOT.jar' }
)

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$mainClasses = @(
    'com.medconsult.auth.AuthServiceApplication',
    'com.medconsult.patient.PatientServiceApplication',
    'com.medconsult.outpatient.OutpatientServiceApplication',
    'com.medconsult.medicalrecord.MedicalRecordServiceApplication',
    'com.medconsult.drug.DrugServiceApplication',
    'com.medconsult.ai.AiServiceApplication',
    'com.medconsult.notification.NotificationServiceApplication',
    'com.medconsult.gateway.GatewayApplication'
)
$workspaceToken = $backendRoot.Replace('\', '\\')
$existing = Get-CimInstance Win32_Process |
    Where-Object {
        $cmd = $_.CommandLine
        $_.Name -eq 'java.exe' -and (
            $cmd -like "*$workspaceToken*" -or
            ($mainClasses | Where-Object { $cmd -like "*$_*" } | Measure-Object).Count -gt 0
        )
    }
if ($existing) {
    $existing | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Start-Sleep -Seconds 3
}

$started = @()
foreach ($service in $services) {
    $serviceDir = Join-Path $backendRoot $service.Name
    $jarPath = Join-Path $serviceDir (Join-Path 'target' $service.Jar)
    if (-not (Test-Path $jarPath)) {
        throw "Jar not found: $jarPath"
    }

    $stdoutLog = Join-Path $logDir ($service.Name + '.stdout.log')
    $stderrLog = Join-Path $logDir ($service.Name + '.stderr.log')
    if (Test-Path $stdoutLog) { Remove-Item -LiteralPath $stdoutLog -Force }
    if (Test-Path $stderrLog) { Remove-Item -LiteralPath $stderrLog -Force }

    $proc = Start-Process -FilePath 'java' `
        -ArgumentList @('-jar', ('"{0}"' -f $jarPath)) `
        -WorkingDirectory $serviceDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    $started += [pscustomobject]@{
        Name = $service.Name
        Port = $service.Port
        ProcessId = $proc.Id
        Stdout = $stdoutLog
        Stderr = $stderrLog
    }

    Start-Sleep -Milliseconds 700
}

$deadline = (Get-Date).AddSeconds(90)
do {
    $pending = @()
    foreach ($service in $started) {
        try {
            $null = Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction Stop
        } catch {
            $pending += $service
        }
    }
    if (-not $pending) {
        break
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

$rows = foreach ($service in $started) {
    $listening = $false
    try {
        $listener = Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction Stop | Select-Object -First 1
        $listening = $true
        $owner = $listener.OwningProcess
    } catch {
        $owner = $null
    }

    [pscustomobject]@{
        Service = $service.Name
        Port = $service.Port
        StartedPid = $service.ProcessId
        Listening = $listening
        OwningProcess = $owner
        Stdout = $service.Stdout
        Stderr = $service.Stderr
    }
}

$rows | Format-Table -AutoSize
