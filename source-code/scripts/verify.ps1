param(
    [switch]$WithDocker
)

$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$jdkCandidates = @(
    $env:JAVA_HOME,
    'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot',
    'C:\Program Files\Java\jdk-21'
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) }

$jdk21 = $jdkCandidates | Where-Object {
    (& (Join-Path $_ 'bin\java.exe') -version 2>&1 | Select-Object -First 1) -match 'version "21\.'
} | Select-Object -First 1

if (-not $jdk21) {
    throw 'JDK 21 was not found. Install Temurin/OpenJDK 21 or set JAVA_HOME.'
}

$env:JAVA_HOME = $jdk21
$env:Path = "$jdk21\bin;$env:Path"

Push-Location $sourceRoot
try {
    Write-Host 'Backend tests (Java 21)'
    & mvn clean test
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }

    Write-Host 'Frontend production build and dependency audit'
    Push-Location (Join-Path $sourceRoot 'frontend')
    try {
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
        & npm audit
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency audit failed.' }
    } finally {
        Pop-Location
    }

    Write-Host 'Docker Compose validation'
    $temporaryInternalKey = -not $env:INTERNAL_SERVICE_KEY
    if ($temporaryInternalKey) {
        $temporaryBytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($temporaryBytes) } finally { $rng.Dispose() }
        $env:INTERNAL_SERVICE_KEY = [BitConverter]::ToString($temporaryBytes).Replace('-', '')
    }
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration is invalid.' }

    if ($WithDocker) {
        Write-Host 'Docker stack and E2E tests'
        if ($temporaryInternalKey) {
            Remove-Item Env:INTERNAL_SERVICE_KEY
        }
        & docker info *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop is not running.' }
        if (-not (Test-Path (Join-Path $sourceRoot '.env'))) {
            throw 'Missing .env file. Copy .env.example and replace every change-me value before running.'
        }
        $environmentText = Get-Content (Join-Path $sourceRoot '.env') -Raw
        if ($environmentText -match 'change-me|change-this|replace-with') {
            throw 'The .env file still contains unsafe placeholder values.'
        }
        & docker compose up --build -d
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose could not be started.' }
        # Gateway/AI may retain connections to an old IP after a service is recreated.
        # Recreate consumers last so DNS and connection pools are refreshed.
        & docker compose up -d --force-recreate --no-deps ai-service gateway-service frontend
        if ($LASTEXITCODE -ne 0) { throw 'Gateway/AI/frontend could not be refreshed.' }
        $ready = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            try {
                $gateway = Invoke-RestMethod -Uri 'http://127.0.0.1:8090/actuator/health' -TimeoutSec 2
                $frontend = Invoke-WebRequest -Uri 'http://127.0.0.1:3000/' -TimeoutSec 2
                if ($gateway.status -eq 'UP' -and $frontend.StatusCode -eq 200) {
                    $ready = $true
                    break
                }
            } catch {}
            Start-Sleep -Seconds 2
        }
        if (-not $ready) { throw 'The system did not become healthy before the timeout.' }
        & docker compose ps
        & node scripts/e2e.mjs
        if ($LASTEXITCODE -ne 0) { throw 'E2E tests failed.' }
    }

    Write-Host 'Verification completed successfully.'
} finally {
    Pop-Location
}
