# Inicia el backend contra el MySQL local. Detener antes otro backend en 8081.
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
$env:PAQRAP_DB_URL = 'jdbc:mysql://127.0.0.1:3307/paqrap'
$env:PAQRAP_DB_USER = 'paqrap_app'
$env:PAQRAP_DB_PASSWORD = $linea.Substring('PAQRAP_DB_PASSWORD='.Length)

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
    Write-Host 'Backend MySQL en http://127.0.0.1:8081. Detén con Ctrl+C.'
    java -jar backend/target/paqrap-backend-1.0-SNAPSHOT.jar --spring.profiles.active=mysql
} finally {
    Pop-Location
}
