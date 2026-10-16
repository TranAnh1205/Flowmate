[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$sourceRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $sourceRoot '.env'

if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Khong tim thay .env. Hay tao file nay tu .env.example.'
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath $envPath) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) {
        continue
    }

    $name, $value = $trimmed.Split('=', 2)
    $settings[$name.Trim()] = $value.Trim()
}

$apiKey = $settings['AI_API_KEY']
$model = $settings['AI_MODEL']
if (-not $apiKey -or $apiKey -eq 'PASTE_OPENAI_API_KEY_HERE') {
    throw 'Hay gan API key that vao dong AI_API_KEY trong .env.'
}
if (-not $model) {
    throw 'AI_MODEL khong duoc de trong.'
}

$dockerBin = 'C:\Program Files\Docker\Docker\resources\bin'
if (Test-Path -LiteralPath $dockerBin) {
    $env:Path = "$dockerBin;$env:Path"
}

Push-Location $sourceRoot
try {
    docker compose version | Out-Null

    Write-Host "Dang kich hoat AI voi model '$model'..."
    docker compose up -d --build --force-recreate --no-deps ai-service
    if ($LASTEXITCODE -ne 0) { throw 'Khong the khoi dong lai AI Service.' }

    docker compose up -d --force-recreate --no-deps gateway-service
    if ($LASTEXITCODE -ne 0) { throw 'Khong the khoi dong lai Gateway Service.' }

    $deadline = (Get-Date).AddSeconds(60)
    do {
        try {
            $aiHealth = (Invoke-RestMethod -Uri 'http://localhost:8083/actuator/health' -TimeoutSec 3).status
            $gatewayHealth = (Invoke-RestMethod -Uri 'http://localhost:8090/actuator/health' -TimeoutSec 3).status
        } catch {
            $aiHealth = 'STARTING'
            $gatewayHealth = 'STARTING'
        }

        if ($aiHealth -eq 'UP' -and $gatewayHealth -eq 'UP') { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if ($aiHealth -ne 'UP' -or $gatewayHealth -ne 'UP') {
        throw "Dich vu chua san sang: AI=$aiHealth, Gateway=$gatewayHealth. Chay docker compose logs ai-service --tail 100 de xem loi."
    }

    Write-Host 'AI Service va Gateway dang hoat dong.' -ForegroundColor Green
    Write-Host 'Mo http://localhost:3000/app/ai-assistant de thu nghiem.'
} finally {
    Pop-Location
}
