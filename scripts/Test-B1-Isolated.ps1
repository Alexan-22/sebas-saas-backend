param([switch]$UnitOnly, [switch]$StartupOnly, [switch]$BrowserSession, [string]$JavaHome = 'C:\Users\alexa\.jdks\ms-21.0.12.1', [string]$MavenRepository = 'C:\Users\alexa\.m2\repository')
$ErrorActionPreference = 'Stop'
if ($BrowserSession) { $StartupOnly = $true }
$backend = Split-Path $PSScriptRoot -Parent
$mvn = 'C:\Users\alexa\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd'
$java = $JavaHome
$oldJava = $env:JAVA_HOME
$oldPath = $env:PATH
$saved = @{}
$pattern = '^(SPRING_|SUPABASE_|DB_|DATABASE_|JDBC_|HIBERNATE_|JAKARTA_PERSISTENCE_|FLYWAY_|LIQUIBASE_|JPA_|POSTGRES_|JWT_|APP_|PAYMENTS_|PGHOST$|PGPORT$|PGDATABASE$|PGUSER$|PGPASSWORD$|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)'
try {
    Get-ChildItem Env: | Where-Object { $_.Name -match $pattern } | ForEach-Object {
        $saved[$_.Name]=$_.Value
        [Environment]::SetEnvironmentVariable($_.Name,$null,'Process')
    }
    $env:JAVA_HOME=$java
    $env:PATH="$java\bin;C:\Program Files\Docker\Docker\resources\bin;$oldPath"
    if (!(Test-Path -LiteralPath $mvn)) { throw 'Maven no encontrado' }
    if (!$UnitOnly -or $StartupOnly) {
    if ($env:DOCKER_HOST -and $env:DOCKER_HOST -notmatch '^npipe://') { throw 'Docker remoto no permitido' }
    $endpoint = & docker context inspect --format '{{.Endpoints.docker.Host}}'
    if ($LASTEXITCODE -ne 0 -or $endpoint -notmatch '^npipe://') { throw 'Se requiere contexto Docker local de Windows' }
    $os = & docker info --format '{{.OSType}}'
    if ($LASTEXITCODE -ne 0 -or $os -ne 'linux') { throw 'Docker Linux local no accesible; no se ejecutaron pruebas' }
    }
    $tests = if ($StartupOnly) { 'IsolatedApplicationStartupTest' } elseif ($UnitOnly) { 'SandboxPolicyTest,SandboxPaymentProviderTest,PostgresTestSafetyTest,CancelacionCodServiceTest,PaymentAdminErrorsTest,YapeQrValidatorTest,YapeCheckoutSafetyTest' } else { 'PostgresTestSafetyTest,CancelacionCodServiceTest,CancelacionCodPostgresIT,PostgresTestcontainersSmokeTest,CancelacionCodHttpJpaTest,KardexMigrationTest,ClienteDocumentoMigrationTest,SandboxPaymentProviderTest,PagosMigrationTest,SandboxPolicyTest,PaymentAdminErrorsTest,YapeQrValidatorTest,YapeCheckoutSafetyTest,YapeMigrationTest,IsolatedApplicationStartupTest' }
    $log = Join-Path ([IO.Path]::GetTempPath()) ('sebas-regression-' + [Guid]::NewGuid().ToString('N') + '.log')
    Write-Host "Registro completo de Maven: $log"
    $nativePreference = $ErrorActionPreference
    $mavenExitCode = $null
    try {
        # Windows PowerShell 5.1 wraps redirected native stderr in ErrorRecord.
        # Preserve every line, but let Maven's exit code determine success/failure.
        $ErrorActionPreference = 'Continue'
        $global:LASTEXITCODE = $null
        & $mvn -f "$backend\pom.xml" "-Dmaven.repo.local=$MavenRepository" "-Dtest=$tests" "-Dsebas.browser.session=$($BrowserSession.IsPresent.ToString().ToLowerInvariant())" -V -e test 2>&1 |
            ForEach-Object { $_.ToString() } |
            Tee-Object -FilePath $log
        $mavenExitCode = $global:LASTEXITCODE
    } finally {
        $ErrorActionPreference = $nativePreference
    }
    if ($null -eq $mavenExitCode -or $mavenExitCode -ne 0) {
        throw "Maven fallo (codigo: $mavenExitCode). Registro: $log"
    }
    Write-Host "Maven termino con codigo 0. Registro: $log"
} finally {
    foreach($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
    $env:JAVA_HOME=$oldJava
    $env:PATH=$oldPath
}
