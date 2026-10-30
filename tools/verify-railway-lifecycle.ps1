param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MirrorInitScript
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$verificationRoot = Join-Path $projectRoot 'build/railway-verification'
$runDirectory = Join-Path $verificationRoot ('lifecycle-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$previousJava = $env:JAVA_HOME
$previousPath = $env:PATH
$previousDirectory = $env:MINETORIO_TASK7_GT_DIR
$previousNamespace = $env:MINETORIO_TASK7_NAMESPACE
$previousPhase = $env:MINETORIO_TASK7_RESTART_PHASE
try {
    if ($JavaHome) {
        if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Не найден Java executable в JavaHome' }
        $env:JAVA_HOME = $JavaHome
        $env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
    }
    $env:MINETORIO_TASK7_GT_DIR = $runDirectory
    $env:MINETORIO_TASK7_NAMESPACE = 'minetorio_task7_restart'
    $gradleArguments = @('--init-script', (Join-Path $PSScriptRoot 'railway-verification.init.gradle'))
    if ($MirrorInitScript) {
        $mirror = (Resolve-Path -LiteralPath $MirrorInitScript).Path
        $gradleArguments += @('--init-script', $mirror)
    }
    $gradleArguments += @('runGameTestServer', '--console=plain')
    Push-Location $projectRoot
    try {
        foreach ($phase in @('save', 'load')) {
            $env:MINETORIO_TASK7_RESTART_PHASE = $phase
            & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments 2>&1 | Tee-Object -FilePath (Join-Path $runDirectory ($phase + '.log'))
            if ($LASTEXITCODE -ne 0) { throw "Lifecycle phase $phase завершилась с кодом $LASTEXITCODE. Мир сохранен: $runDirectory" }
        }
    } finally { Pop-Location }
    Write-Output "Lifecycle save/load GREEN. Каталог и логи сохранены: $runDirectory"
} finally {
    $env:JAVA_HOME = $previousJava
    $env:PATH = $previousPath
    $env:MINETORIO_TASK7_GT_DIR = $previousDirectory
    $env:MINETORIO_TASK7_NAMESPACE = $previousNamespace
    $env:MINETORIO_TASK7_RESTART_PHASE = $previousPhase
}
