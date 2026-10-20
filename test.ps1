$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$JunitVersion = '1.11.4'
$JunitSha256 = 'b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc'
$JunitUrl = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JunitVersion/junit-platform-console-standalone-$JunitVersion.jar"
$JunitJar = "build\lib\junit-platform-console-standalone-$JunitVersion.jar"
$TestClassesDir = 'build\test-classes'

& .\compile.ps1
if ($LASTEXITCODE -ne 0) { exit 1 }

if (-not (Test-Path $JunitJar)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $JunitJar) | Out-Null
    Invoke-WebRequest -Uri $JunitUrl -OutFile "$JunitJar.download" -UseBasicParsing
    $actual = (Get-FileHash "$JunitJar.download" -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $JunitSha256) {
        Remove-Item -Force "$JunitJar.download"
        Write-Error "Checksum mismatch for $JunitUrl (expected $JunitSha256, got $actual)"
    }
    Move-Item "$JunitJar.download" $JunitJar
}

Remove-Item -Recurse -Force $TestClassesDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $TestClassesDir | Out-Null
$testSources = (Get-ChildItem .\tests\unit -Recurse -Filter *.java).FullName
javac --release 21 -cp "build\classes;$JunitJar" -d $TestClassesDir $testSources
if ($LASTEXITCODE -ne 0) { exit 1 }

java -jar $JunitJar execute `
    --class-path "$TestClassesDir;build\classes" `
    --scan-class-path `
    --fail-if-no-tests `
    --disable-banner
exit $LASTEXITCODE
