$names = @('ALIYUNBAILIAN_APIKEY', 'ALIYUNBAILIAN_API_KEY', 'ALIYUNBAILIAN_BASE_URL', 'ALIYUNBAILIAN_MODEL')
Write-Host "=== User-level env vars ==="
$userVars = [Environment]::GetEnvironmentVariables('User')
foreach ($name in $names) {
    $val = $userVars[$name]
    if ($val) {
        $preview = $val.Substring(0, [Math]::Min(15, $val.Length))
        Write-Host "${name}=${preview}... (length=$($val.Length))"
    } else {
        Write-Host "${name}=<not set>"
    }
}
Write-Host ""
Write-Host "=== System-level env vars ==="
$sysVars = [Environment]::GetEnvironmentVariables('Machine')
foreach ($name in $names) {
    $val = $sysVars[$name]
    if ($val) {
        $preview = $val.Substring(0, [Math]::Min(15, $val.Length))
        Write-Host "${name}=${preview}... (length=$($val.Length))"
    } else {
        Write-Host "${name}=<not set>"
    }
}
Write-Host ""
Write-Host "=== Current process env vars ==="
foreach ($name in $names) {
    $val = [Environment]::GetEnvironmentVariable($name)
    if ($val) {
        $preview = $val.Substring(0, [Math]::Min(15, $val.Length))
        Write-Host "${name}=${preview}... (length=$($val.Length))"
    } else {
        Write-Host "${name}=<not set>"
    }
}
