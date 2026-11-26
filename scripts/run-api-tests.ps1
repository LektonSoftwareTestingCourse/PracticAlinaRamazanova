$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$createdEnv = $false

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    $createdEnv = $true
}

try {
    docker compose up -d --build --wait postgres rabbitmq bin-lookup card-management authorization transaction-logger switch gateway terminal-simulator merchant-acquirer
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose start failed' }

    mvn -f services/pom.xml -Papi-tests test
    if ($LASTEXITCODE -ne 0) { throw 'API tests failed' }
}
finally {
    docker compose down
    if ($createdEnv) { Remove-Item '.env' -ErrorAction SilentlyContinue }
}
