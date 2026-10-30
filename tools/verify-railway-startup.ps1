param([string]$JavaHome=$env:JAVA_HOME,[string]$MirrorInitScript)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$verificationRoot=[IO.Path]::GetFullPath((Join-Path $projectRoot ('build/railway-verification/startup-'+[Guid]::NewGuid().ToString('N'))))
[IO.Directory]::CreateDirectory($verificationRoot)|Out-Null
$names=@('JAVA_HOME','PATH','MINETORIO_TASK7_GT_DIR','MINETORIO_TASK7_NAMESPACE','MINETORIO_TASK7_STARTUP_PHASE','MINETORIO_TASK7_STARTUP_CASE')
$previous=@{};foreach($name in $names){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
function Checked-Path([string]$path) {
    $resolved=[IO.Path]::GetFullPath($path)
    if(!$resolved.StartsWith($verificationRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Путь вне task-owned verification root: $resolved"}
    return $resolved
}
function Run-Phase([string]$directory,[string]$phase,[string]$case) {
    $env:MINETORIO_TASK7_GT_DIR=Checked-Path $directory
    $env:MINETORIO_TASK7_STARTUP_PHASE=$phase;$env:MINETORIO_TASK7_STARTUP_CASE=$case
    [IO.Directory]::CreateDirectory($directory)|Out-Null
    & (Join-Path $projectRoot 'gradlew.bat') @script:gradleArguments 2>&1|Tee-Object -FilePath (Join-Path $directory ($phase+'.log'))
    if($LASTEXITCODE -ne 0){throw "Startup $phase/$case завершился с $LASTEXITCODE. Файлы сохранены: $directory"}
}
function World-Hashes([string]$world) {
    $result=@{};Get-ChildItem -LiteralPath $world -Recurse -File|ForEach-Object {$result[[IO.Path]::GetRelativePath($world,$_.FullName)]=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash};return $result
}
function Copy-Fixture([string]$prepared,[string]$name,[bool]$skipIndex,[bool]$corrupt) {
    $source=Checked-Path (Join-Path $prepared 'world');$directory=Checked-Path (Join-Path $verificationRoot $name);$destination=Checked-Path (Join-Path $directory 'world')
    if(Test-Path -LiteralPath $directory){throw "Новый fixture каталог уже существует: $directory"}
    [IO.Directory]::CreateDirectory($destination)|Out-Null
    foreach($file in Get-ChildItem -LiteralPath $source -Recurse -File) {
        $relative=[IO.Path]::GetRelativePath($source,$file.FullName)
        if($skipIndex -and $relative.Replace('\','/') -eq 'data/minetorio_personal_robot_activity.dat'){continue}
        $target=Checked-Path (Join-Path $destination $relative);[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target))|Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $target
    }
    $hashes=[Collections.Generic.List[object]]::new()
    $canonical=Join-Path $destination 'entities/r.531.531.mca'
    if(Test-Path -LiteralPath $canonical){$hashes.Add(@{path='entities/r.531.531.mca';hash=(Get-FileHash -LiteralPath $canonical -Algorithm SHA256).Hash})}
    if($corrupt) {
        $file=Checked-Path (Join-Path $destination 'entities/r.999999.999999.mca');[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($file))|Out-Null
        if(Test-Path -LiteralPath $file){throw 'Corrupt fixture не должен заменять существующий store'}
        $bytes=[byte[]]::new(8192);$bytes[2]=2;$bytes[3]=1;[IO.File]::WriteAllBytes($file,$bytes)
        $hashes.Add(@{path='entities/r.999999.999999.mca';hash=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash})
    }
    ConvertTo-Json -InputObject @($hashes.ToArray()) -Depth 3|Set-Content -LiteralPath (Join-Path $destination 'task7-startup-hashes.json') -Encoding utf8
    return $directory
}
try {
    if($JavaHome){if(!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))){throw 'JavaHome не содержит bin/java.exe'};$env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+[IO.Path]::PathSeparator+$env:PATH}
    $env:MINETORIO_TASK7_NAMESPACE='minetorio_task7_startup'
    $script:gradleArguments=@('--init-script',(Join-Path $PSScriptRoot 'railway-verification.init.gradle'))
    if($MirrorInitScript){$script:gradleArguments+=@('--init-script',(Resolve-Path -LiteralPath $MirrorInitScript).Path)}
    $script:gradleArguments+=@('runGameTestServer','--console=plain')
    Push-Location $projectRoot
    try {
        $prepared=Join-Path $verificationRoot 'prepared';Run-Phase $prepared 'prepare' 'canonical';$original=World-Hashes (Join-Path $prepared 'world')
        foreach($case in @('legacy','ghost','corrupt')) {
            $fixture=Copy-Fixture $prepared $case ($case -eq 'legacy') ($case -eq 'corrupt');Run-Phase $fixture 'verify' $case
            if($case -eq 'legacy'){Run-Phase $fixture 'verify' $case}
        }
        $after=World-Hashes (Join-Path $prepared 'world');if($after.Count -ne $original.Count){throw 'Prepared original file set changed'}
        foreach($path in $original.Keys){if($after[$path] -ne $original[$path]){throw "Prepared original bytes changed: $path"}}
        $empty=Join-Path $verificationRoot 'prepared-empty';Run-Phase $empty 'prepare' 'empty';$emptyOriginal=World-Hashes (Join-Path $empty 'world')
        $fixture=Copy-Fixture $empty 'legacy-empty' $true $false;Run-Phase $fixture 'verify' 'legacy-empty'
        $emptyAfter=World-Hashes (Join-Path $empty 'world');if($emptyAfter.Count -ne $emptyOriginal.Count){throw 'Empty prepared file set changed'}
        foreach($path in $emptyOriginal.Keys){if($emptyAfter[$path] -ne $emptyOriginal[$path]){throw "Empty prepared bytes changed: $path"}}
        Write-Output "Startup legacy/crash/corrupt GREEN. Prepared originals byte-identical. Каталог: $verificationRoot"
    } finally {Pop-Location}
} finally {foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}}
