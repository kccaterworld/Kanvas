javac -d build\classes (Get-ChildItem kanvas\processor -Recurse -Filter *.java).FullName

jar --create `
    --file build\kanvas.jar `
    --main-class kanvas.processor.Main `
    -C build\classes . `
    -C . kanvas

java -jar .\build\kanvas.jar
