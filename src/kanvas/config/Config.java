package kanvas.config;

import java.util.*;
import java.io.*;

public class Config {
    private final String version;
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

    public Config(String projectName, String jarName, String version,
        String author, String description, String mainClass, String target, String encoding,
        String packageVersion, File icon, File mainFile, File output, List<File> sourceDirectories,
        List<File> classpath, List<File> dependencies, List<String> nativeTargets) {
        this.projectName = projectName;
        this.jarName = jarName;
        this.packageVersion = packageVersion;
        this.version = version;
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

    public String getVersion() { return version; }
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

    public String toJSONString() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"name\": ").append(jsonString(projectName)).append(",\n");
        json.append("  \"version\": ").append(jsonString(version)).append(",\n");
        json.append("  \"author\": ").append(jsonString(author)).append(",\n");
        json.append("  \"description\": ").append(jsonString(description)).append(",\n");
        json.append("  \"mainClass\": ").append(jsonString(mainClass)).append(",\n");
        json.append("  \"modules\": {\n");
        json.append("    \"srcDirs\": [");
        for (int i = 0; sourceDirectories != null && i < sourceDirectories.size(); i++) {
            if (i > 0) json.append(", ");
            File file = sourceDirectories.get(i);
            json.append(jsonString(file == null ? null : file.getPath()));
        }
        json.append("],\n");
        json.append("    \"outputDir\": ").append(jsonString(output == null ? null : output.getPath())).append("\n");
        json.append("  },\n");
        json.append("  \"classpath\": [");
        for (int i = 0; classpath != null && i < classpath.size(); i++) {
            if (i > 0) json.append(", ");
            File file = classpath.get(i);
            json.append(jsonString(file == null ? null : file.getPath()));
        }
        json.append("],\n");
        json.append("  \"dependencies\": [");
        for (int i = 0; dependencies != null && i < dependencies.size(); i++) {
            if (i > 0) json.append(", ");
            File file = dependencies.get(i);
            json.append(jsonString(file == null ? null : file.getPath()));
        }
        json.append("],\n");
        json.append("  \"compiler\": {\n");
        json.append("    \"target\": ").append(jsonString(target)).append(",\n");
        json.append("    \"encoding\": ").append(jsonString(encoding)).append("\n");
        json.append("  },\n");
        json.append("  \"packaging\": {\n");
        json.append("    \"jarName\": ").append(jsonString(jarName)).append(",\n");
        json.append("    \"version\": ").append(jsonString(packageVersion)).append(",\n");
        json.append("    \"icon\": ").append(jsonString(icon == null ? null : icon.getPath())).append(",\n");
        json.append("    \"nativeTargets\": [");
        for (int i = 0; nativeTargets != null && i < nativeTargets.size(); i++) {
            if (i > 0) json.append(", ");
            json.append(jsonString(nativeTargets.get(i)));
        }
        json.append("]\n");
        json.append("  }\n");
        json.append("}\n");
        return json.toString();
    }

    private static String jsonString(String value) {
        if (value == null) return "null";

        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\b': json.append("\\b"); break;
                case '\f': json.append("\\f"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (c < 0x20) json.append(String.format("\\u%04x", (int) c));
                    else json.append(c);
            }
        }
        return json.append('"').toString();
    }
}
