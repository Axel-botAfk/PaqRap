# Inicia el backend contra el MySQL local. -TestConnection verifica sin arrancar HTTP.
param([switch]$TestConnection)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $repo '.env.docker'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Falta .env.docker. Ejecuta primero .\scripts\start-local-db.ps1.'
}
$linea = Get-Content -LiteralPath $envFile |
    Where-Object { $_ -like 'PAQRAP_DB_PASSWORD=*' } | Select-Object -First 1
if (-not $linea -or $linea -eq 'PAQRAP_DB_PASSWORD=') {
    throw 'PAQRAP_DB_PASSWORD no está configurada en .env.docker.'
}
$password = $linea.Substring('PAQRAP_DB_PASSWORD='.Length)

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) { $maven = Get-Command mvn -ErrorAction SilentlyContinue }
$mavenPath = if ($maven) { $maven.Source } else {
    Join-Path $repo '..\tmp\maven\apache-maven-3.10.0\bin\mvn.cmd'
}
if (-not (Test-Path -LiteralPath $mavenPath)) {
    throw 'Maven no está disponible en PATH ni en la carpeta local tmp/maven.'
}

Push-Location $repo
try {
    $mavenArgs = @()
    $cacheLocal = Join-Path $repo '..\tmp\m2'
    if (Test-Path -LiteralPath $cacheLocal) {
        $mavenArgs += "-Dmaven.repo.local=$cacheLocal"
    }
    & $mavenPath @mavenArgs -pl backend -am -Pmysql -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el backend MySQL.' }
    $keyBytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($keyBytes) } finally { $rng.Dispose() }
    $env:PAQRAP_DB_KEY = [Convert]::ToBase64String($keyBytes)
    $env:PAQRAP_DB_CONFIG = Join-Path $repo 'db.properties'
    $encrypted = $password | & java -cp backend/target/classes com.paqrap.api.DbPasswordCipher encrypt
    if ($LASTEXITCODE -ne 0 -or -not $encrypted) { throw 'No se pudo cifrar la contraseña local.' }
    [System.IO.File]::WriteAllLines($env:PAQRAP_DB_CONFIG, @(
        'db.host=127.0.0.1',
        'db.port=3307',
        'db.name=paqrap',
        'db.user=paqrap_app',
        "db.password.encrypted=$encrypted"
    ), [System.Text.Encoding]::ASCII)
    $password = $null
    if ($TestConnection) {
        & $mavenPath @mavenArgs -pl backend -am -Pmysql '-Dtest=MysqlConnectionIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
        if ($LASTEXITCODE -ne 0) { throw 'Falló la prueba de conexión MySQL.' }
    } else {
        Write-Host 'Backend MySQL en http://127.0.0.1:8080. Detén con Ctrl+C.'
        java -jar backend/target/paqrap-backend-1.0-SNAPSHOT.jar --spring.profiles.active=mysql
    }
} finally {
    Remove-Item Env:PAQRAP_DB_KEY -ErrorAction SilentlyContinue
    Pop-Location
}
