param(
    [string]$Model = "BAAI/bge-small-zh-v1.5",
    [int]$Port = 7997,
    [string]$Device = "cpu",
    [switch]$Background
)

$ErrorActionPreference = "Stop"

# China mirror for HuggingFace model download
if (-not $env:HF_ENDPOINT) {
    $env:HF_ENDPOINT = "https://hf-mirror.com"
    Write-Host "[INFO] HF_ENDPOINT=$env:HF_ENDPOINT (china mirror)" -ForegroundColor Cyan
}

$env:EMBEDDING_MODEL = $Model
$env:EMBEDDING_PORT = "$Port"
$env:EMBEDDING_DEVICE = $Device

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$appPath = Join-Path $scriptDir "app.py"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "  MedConsult Embedding Server" -ForegroundColor Green
Write-Host "  Model: $Model" -ForegroundColor Green
Write-Host "  Port:  $Port" -ForegroundColor Green
Write-Host "  Device: $Device" -ForegroundColor Green
Write-Host "  URL:   http://localhost:$Port/v1/embeddings" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""

if ($Background) {
    $logFile = Join-Path $scriptDir "embedding-server.log"
    $errFile = Join-Path $scriptDir "embedding-server.err.log"
    $proc = Start-Process -FilePath "python" -ArgumentList "`"$appPath`"" -WorkingDirectory $scriptDir -WindowStyle Hidden -PassThru -RedirectStandardOutput $logFile -RedirectStandardError $errFile
    $proc.Id | Out-File (Join-Path $scriptDir "embedding-server.pid") -Encoding ascii
    Write-Host "[INFO] Started PID=$($proc.Id)" -ForegroundColor Green
    Write-Host "[INFO] Log:  $logFile" -ForegroundColor Cyan
    Write-Host "[INFO] Stop: Stop-Process -Id $($proc.Id)" -ForegroundColor Cyan
} else {
    Set-Location $scriptDir
    python app.py
}
