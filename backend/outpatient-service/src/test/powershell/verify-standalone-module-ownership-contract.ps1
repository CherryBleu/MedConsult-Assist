Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path

$filesThatMustNotImportCommonFeignDto = @(
    'src\main\java\com\medconsult\outpatient\appointment\controller\AppointmentInternalController.java',
    'src\main\java\com\medconsult\outpatient\appointment\service\AppointmentService.java',
    'src\main\java\com\medconsult\outpatient\appointment\service\AppointmentServiceImpl.java'
)

$problems = New-Object System.Collections.Generic.List[string]

foreach ($relativePath in $filesThatMustNotImportCommonFeignDto) {
    $absolutePath = Join-Path $moduleRoot $relativePath
    $content = Get-Content -Path $absolutePath -Raw
    if ($content -match 'com\.medconsult\.common\.feign\.dto\.AppointmentOwnershipDTO') {
        $problems.Add("Unexpected common-feign DTO import in $relativePath")
    }
}

$localDtoPath = Join-Path $moduleRoot 'src\main\java\com\medconsult\outpatient\appointment\dto\AppointmentOwnershipDTO.java'
if (-not (Test-Path $localDtoPath)) {
    $problems.Add("Missing local DTO file: src/main/java/com/medconsult/outpatient/appointment/dto/AppointmentOwnershipDTO.java")
} else {
    $dtoSource = Get-Content -Path $localDtoPath -Raw
    $requiredSnippets = @(
        'record AppointmentOwnershipDTO',
        'Long appointmentId',
        'String appointmentNo',
        'Long patientId',
        'Long doctorId'
    )

    foreach ($snippet in $requiredSnippets) {
        if ($dtoSource -notmatch [regex]::Escape($snippet)) {
            $problems.Add("Local DTO is missing snippet: $snippet")
        }
    }
}

if ($problems.Count -gt 0) {
    $problems | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    exit 1
}

Write-Host 'Standalone ownership DTO contract satisfied.' -ForegroundColor Green
