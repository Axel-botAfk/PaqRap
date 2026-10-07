# Crea credenciales locales aleatorias una sola vez y arranca solo MySQL.
# No elimina ni reinicializa el volumen de datos.
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $repo '.env.docker'

if (-not (Test-Path -LiteralPath $envFile)) {
    $rootPassword = [Convert]::ToHexString(
        [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $appPassword = [Convert]::ToHexString(
        [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    [System.IO.File]::WriteAllLines($envFile, @(
        "PAQRAP_MYSQL_ROOT_PASSWORD=$rootPassword",
        "PAQRAP_DB_PASSWORD=$appPassword"
    ))
    Write-Host 'Se creó .env.docker con contraseñas aleatorias. No lo subas a Git.'
}

Push-Location $repo
try {
    docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Desktop no está disponible. Ábrelo y vuelve a ejecutar este script.'
    }
    docker compose --env-file .env.docker config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'La configuración Compose no es válida.' }
    docker compose --env-file .env.docker up -d --wait mysql
    if ($LASTEXITCODE -ne 0) { throw 'MySQL no alcanzó el estado healthy. Revisa docker compose logs mysql.' }
    Write-Host 'MySQL de PaqRap listo en 127.0.0.1:3307; esquema y semillas cargados si el volumen era nuevo.'
} finally {
    Pop-Location
}
