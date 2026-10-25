param([string]$ServerDirectory = (Join-Path $PSScriptRoot '..'))
$ErrorActionPreference = 'Stop'
$serverRoot = (Resolve-Path -LiteralPath $ServerDirectory).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
# Read configuration directly into this process. Never put credentials into args, logs or a copied .env.
$allowed = @('MYSQL_HOST','MYSQL_PORT','MYSQL_DATABASE','MYSQL_USER','MYSQL_PASSWORD',
    'SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','COSMO_KEY','COSMO_CREDENTIALS_FILE')
$settings = @{}
$envPath = Join-Path $serverRoot '.env'
if (Test-Path -LiteralPath $envPath) {
    foreach ($line in [IO.File]::ReadAllLines($envPath)) {
        if ($line -match '^\s*([A-Z_]+)\s*=(.*)$' -and $allowed -contains $matches[1]) {
            $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
}
if (!$settings.SPRING_DATASOURCE_URL -and $settings.MYSQL_DATABASE) {
    $databaseHost = if ($settings.MYSQL_HOST) { $settings.MYSQL_HOST } else { 'localhost' }
    $databasePort = if ($settings.MYSQL_PORT) { $settings.MYSQL_PORT } else { '3306' }
    $settings.SPRING_DATASOURCE_URL = "jdbc:mariadb://${databaseHost}:${databasePort}/$($settings.MYSQL_DATABASE)?useSSL=false"
}
if (!$settings.SPRING_DATASOURCE_USERNAME -and $settings.MYSQL_USER) { $settings.SPRING_DATASOURCE_USERNAME = $settings.MYSQL_USER }
if (!$settings.SPRING_DATASOURCE_PASSWORD -and $settings.ContainsKey('MYSQL_PASSWORD')) { $settings.SPRING_DATASOURCE_PASSWORD = $settings.MYSQL_PASSWORD }
$credentialPath = if ($settings.COSMO_CREDENTIALS_FILE) { $settings.COSMO_CREDENTIALS_FILE } else { 'data/cosmo/credentials.json' }
if (![IO.Path]::IsPathRooted($credentialPath)) { $credentialPath = Join-Path $serverRoot $credentialPath }
if (!(Test-Path -LiteralPath $credentialPath)) { throw 'COSMO 연결 정보가 없습니다. 연결 화면에서 본인 계정을 먼저 인증해 주세요.' }
$settings.COSMO_CREDENTIALS_FILE = [IO.Path]::GetFullPath($credentialPath)
$previous = @{}
$processVariables = @('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','COSMO_KEY','COSMO_CREDENTIALS_FILE')
try {
    foreach ($name in $processVariables) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($settings.ContainsKey($name)) { [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process') }
    }
    Push-Location -LiteralPath $repositoryRoot
    try {
        & .\gradlew.bat importCosmoObjekts --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'COSMO DB 가져오기에 실패했습니다. 인증 상태와 DB 연결을 확인해 주세요.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $processVariables) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $settings.Clear()
}
