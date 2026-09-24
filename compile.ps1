Remove-Item -Recurse -Force build\classes -ErrorAction SilentlyContinue
Remove-Item -Force build\kanvas.jar -ErrorAction SilentlyContinue

New-Item -ItemType Directory -Force -Path build\classes | Out-Null

javac --release 21 -d build\classes (Get-ChildItem ./src/kanvas -Recurse -Filter *.java | Where-Object { $_.FullName -notmatch '\\assets\\templates\\' }).FullName

if ($LASTEXITCODE -ne 0) { exit 1 }

Copy-Item -Recurse -Force src\kanvas\assets build\classes\kanvas\

jar --create `
    --file build\kanvas.jar `
    --main-class kanvas.cli.Main `
    -C build\classes .

Write-Host "Built build\kanvas.jar"
