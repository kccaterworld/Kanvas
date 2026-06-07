package kanvas.config;

import java.util.*;
import java.io.*;

public class Config {
    private final String projectVersion;
    private final String author;
    private final String description;
    private final String target;
    private final String encoding;
    private final String projectName;
    private final String jarName;
    private final String packageVersion;
    private final String mainClass;
    private final File icon;
    private final File mainFile;
    private final File output;
    private final List<File> sourceDirectories;
    private final List<File> classpath;
    private final List<File> dependencies;
    private final List<String> nativeTargets;

    public Config(String projectName, String jarName, String projectVersion,
        String author, String description, String mainClass, String target, String encoding,
        String packageVersion, File icon, File mainFile, File output, List<File> sourceDirectories,
        List<File> classpath, List<File> dependencies, List<String> nativeTargets) {
        this.projectName = projectName;
        this.jarName = jarName;
        this.packageVersion = packageVersion;
        this.projectVersion = projectVersion;
        this.author = author;
        this.description = description;
        this.mainClass = mainClass;
        this.target = target;
        this.encoding = encoding;
        this.icon = icon;
        this.nativeTargets = nativeTargets;
        this.mainFile = mainFile;
        this.output = output;
        this.sourceDirectories = sourceDirectories;
        this.classpath = classpath;
        this.dependencies = dependencies;
    }

    public String getProjectVersion() { return projectVersion; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getMainClass() { return mainClass; }
    public String getTarget() { return target; }
    public String getEncoding() { return encoding; }
    public String getProjectName() { return projectName; }
    public String getJarName() { return jarName; }
    public String getPackageVersion() { return packageVersion; }
    public File getIcon() { return icon; }
    public File getMainFile() { return mainFile; }
    public File getOutput() { return output; }
    public List<File> getSourceDirectories() { return sourceDirectories; }
    public List<File> getClasspath() { return classpath; }
    public List<File> getDependencies() { return dependencies; }
    public List<String> getNativeTargets() { return nativeTargets; }

    public String toTOMLString() {
        StringBuilder t = new StringBuilder();
        t.append("name = ").append(tomlString(projectName)).append("\n");
        t.append("version = ").append(tomlString(projectVersion)).append("\n");
        t.append("author = ").append(tomlString(author)).append("\n");
        t.append("description = ").append(tomlString(description)).append("\n");
        if (mainClass != null) t.append("mainClass = ").append(tomlString(mainClass)).append("\n");
        t.append("classpath = [");
        for (int i = 0; classpath != null && i < classpath.size(); i++) {
            if (i > 0) t.append(", ");
            File file = classpath.get(i);
            t.append(tomlString(file == null ? null : file.getPath()));
        }
        t.append("]\n");
        t.append("dependencies = [");
        for (int i = 0; dependencies != null && i < dependencies.size(); i++) {
            if (i > 0) t.append(", ");
            File file = dependencies.get(i);
            t.append(tomlString(file == null ? null : file.getPath()));
        }
        t.append("]\n");
        t.append("\n[modules]\n");
        t.append("srcDirs = [");
        for (int i = 0; sourceDirectories != null && i < sourceDirectories.size(); i++) {
            if (i > 0) t.append(", ");
            File file = sourceDirectories.get(i);
            t.append(tomlString(file == null ? null : file.getPath()));
        }
        t.append("]\n");
        t.append("outputDir = ").append(tomlString(output == null ? null : output.getPath())).append("\n");
        t.append("\n[compiler]\n");
        t.append("target = ").append(tomlString(target)).append("\n");
        t.append("encoding = ").append(tomlString(encoding)).append("\n");
        t.append("\n[packaging]\n");
        t.append("jarName = ").append(tomlString(jarName)).append("\n");
        t.append("version = ").append(tomlString(packageVersion)).append("\n");
        if (icon != null) t.append("icon = ").append(tomlString(icon.getPath())).append("\n");
        t.append("nativeTargets = [");
        for (int i = 0; nativeTargets != null && i < nativeTargets.size(); i++) {
            if (i > 0) t.append(", ");
            t.append(tomlString(nativeTargets.get(i)));
        }
        t.append("]\n");
        return t.toString();
    }

    private static String tomlString(String value) {
        if (value == null) return "\"\"";
        StringBuilder s = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': s.append("\\\""); break;
                case '\\': s.append("\\\\"); break;
                case '\b': s.append("\\b"); break;
                case '\f': s.append("\\f"); break;
                case '\n': s.append("\\n"); break;
                case '\r': s.append("\\r"); break;
                case '\t': s.append("\\t"); break;
                default:
                    if (c < 0x20) s.append(String.format("\\u%04x", (int) c));
                    else s.append(c);
            }
        }
        return s.append('"').toString();
    }
}
