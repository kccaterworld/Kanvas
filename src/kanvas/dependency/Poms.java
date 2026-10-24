package kanvas.dependency;

import kanvas.dependency.PomReader.RawDependency;
import kanvas.dependency.PomReader.RawPom;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Poms {
    interface Source { Path find(String group, String artifact, String version) throws DependencyException; }

    record PomDependency(String group, String artifact, String version, String type, String classifier,
                         String scope, boolean optional, List<String> exclusions) {
        String key() { return group + ":" + artifact; }
    }

    record Pom(String packaging, List<PomDependency> dependencies, List<PomDependency> managed) {}

    private record Model(String group, String artifact, String version, String packaging, String parentGroup, String parentVersion,
                         Map<String, String> properties, List<RawDependency> dependencies, List<RawDependency> managed) {}

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    private final Source source;
    private final Map<String, Pom> poms = new HashMap<>();
    private final Map<String, Model> models = new HashMap<>();
    private final Set<String> inProgress = new HashSet<>();

    Poms(Source source) { this.source = source; }

    Pom pom(String group, String artifact, String version) throws DependencyException {
        String coordinate = group + ":" + artifact + ":" + version;
        Pom cached = poms.get(coordinate);
        if (cached != null) return cached;
        if (!inProgress.add("pom " + coordinate)) throw new DependencyException("The POM of " + coordinate + " imports itself");
        try {
            Model model = model(group, artifact, version);
            Map<String, String> context = context(model);

            List<PomDependency> managed = new ArrayList<>();
            Set<String> managedKeys = new HashSet<>();
            List<PomDependency> imports = new ArrayList<>();
            for (RawDependency raw : model.managed()) {
                PomDependency dependency = interpolate(raw, context);
                if ("import".equals(dependency.scope()) && "pom".equals(dependency.type())) imports.add(dependency);
                else if (managedKeys.add(managementKey(dependency))) managed.add(dependency);
            }
            for (PomDependency bom : imports) {
                if (problem(bom) != null) continue;
                for (PomDependency entry : pom(bom.group(), bom.artifact(), bom.version()).managed())
                    if (managedKeys.add(managementKey(entry))) managed.add(entry);
            }
            Map<String, PomDependency> byKey = new HashMap<>();
            for (PomDependency entry : managed) byKey.put(managementKey(entry), entry);

            List<PomDependency> dependencies = new ArrayList<>();
            for (RawDependency raw : model.dependencies()) {
                PomDependency dependency = interpolate(raw, context);
                PomDependency management = byKey.get(managementKey(dependency));
                if (management != null) dependency = new PomDependency(
                    dependency.group(), dependency.artifact(),
                    ((dependency.version() != null) ? dependency.version() : management.version()),
                    dependency.type(), dependency.classifier(),
                    ((dependency.scope() != null) ? dependency.scope() : management.scope()),
                    dependency.optional(),
                    (dependency.exclusions().isEmpty()) ? management.exclusions() : dependency.exclusions()
                );
                dependencies.add(dependency);
            }
            String packaging = model.packaging() == null ? "jar" : interpolate(model.packaging(), context);
            Pom pom = new Pom(packaging, List.copyOf(dependencies), List.copyOf(managed));
            poms.put(coordinate, pom);
            return pom;
        } finally { inProgress.remove("pom " + coordinate); }
    }

    static String problem(PomDependency dependency) { return kanvas.project.Dependency.Maven.problem(dependency.group(), dependency.artifact(), dependency.version()); }

    private Model model(String group, String artifact, String version) throws DependencyException {
        String coordinate = group + ":" + artifact + ":" + version;
        Model cached = models.get(coordinate);
        if (cached != null) return cached;
        if (!inProgress.add("model " + coordinate)) throw new DependencyException("The POM of " + coordinate + " is its own parent");
        try {
            Path file = source.find(group, artifact, version);
            Model model;
            if (file == null) model = new Model(group, artifact, version, "jar", null, null, Map.of(), List.of(), List.of());
            else {
                RawPom raw = PomReader.read(file);
                PomReader.Parent parentRef = raw.parent();
                Model parent = ((parentRef == null || parentRef.version() == null) ? null
                    : model(parentRef.group(), parentRef.artifact(), parentRef.version()));
                Map<String, String> properties = new LinkedHashMap<>();
                if (parent != null) properties.putAll(parent.properties());
                properties.putAll(raw.properties());
                model = new Model(
                    (raw.group() != null) ? raw.group() : ((parentRef != null) ? parentRef.group() : group),
                    (raw.artifact() != null) ? raw.artifact() : artifact,
                    (raw.version() != null) ? raw.version() : ((parentRef != null) ? parentRef.version() : version),
                    raw.packaging(),
                    ((parentRef == null) ? null : parentRef.group()), ((parentRef == null) ? null : parentRef.version()),
                    properties,
                    merge(((parent == null) ? List.of() : parent.dependencies()), raw.dependencies()),
                    merge(((parent == null) ? List.of() : parent.managed()), raw.managed()));
            }
            models.put(coordinate, model);
            return model;
        } finally { inProgress.remove("model " + coordinate); }
    }

    private static List<RawDependency> merge(List<RawDependency> parent, List<RawDependency> child) {
        Map<String, RawDependency> merged = new LinkedHashMap<>();
        for (RawDependency dependency : parent) merged.put(rawKey(dependency), dependency);
        for (RawDependency dependency : child) merged.put(rawKey(dependency), dependency);
        return List.copyOf(merged.values());
    }

    private static String rawKey(RawDependency d) { return d.group() + ":" + d.artifact() + ":" + (d.type() == null ? "jar" : d.type()) + ":" + (d.classifier() == null ? "" : d.classifier());}
    private static String managementKey(PomDependency d) { return d.key() + ":" + d.type() + ":" + (d.classifier() == null ? "" : d.classifier()); }

    private static Map<String, String> context(Model model) {
        Map<String, String> context = new HashMap<>(model.properties());
        for (String prefix : List.of("project.", "pom.", "")) {
            context.put(prefix + "groupId", model.group());
            context.put(prefix + "artifactId", model.artifact());
            context.put(prefix + "version", model.version());
        }
        if (model.parentVersion() != null) {
            context.put("project.parent.version", model.parentVersion());
            context.put("parent.version", model.parentVersion());
            context.put("project.parent.groupId", model.parentGroup());
        }
        context.values().removeIf(Objects::isNull);
        return context;
    }

    private static PomDependency interpolate(RawDependency raw, Map<String, String> context) {
        List<String> exclusions = raw.exclusions().stream().map(exclusion -> interpolate(exclusion, context)).toList();
        String type = interpolate(raw.type(), context), scope = interpolate(raw.scope(), context);
        return new PomDependency(interpolate(raw.group(), context), interpolate(raw.artifact(), context), interpolate(raw.version(), context),
            type == null ? "jar" : type, interpolate(raw.classifier(), context), scope, raw.optional(), exclusions);
    }

    static String interpolate(String value, Map<String, String> context) {
        if (value == null) return null;
        for (int round = 0; round < 20 && value.contains("${"); round++) {
            Matcher matcher = PROPERTY.matcher(value);
            StringBuilder result = new StringBuilder();
            boolean changed = false;
            while (matcher.find()) {
                String replacement = context.get(matcher.group(1));
                changed |= (replacement != null);
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
            }
            matcher.appendTail(result);
            value = result.toString();
            if (!changed) break;
        }
        return value;
    }
}
