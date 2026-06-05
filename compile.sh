javac -d build\classes (Get-ChildItem kanvas -Recurse -Filter *.java | Where-Object { $_.FullName -notmatch '\\assets\\templates\\' }).FullName

jar --create `
    --file build\kanvas.jar `
    --main-class kanvas.cli.Main `
    -C build\classes . `
    -C . kanvas
