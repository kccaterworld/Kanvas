package kanvas.dependency;

import kanvas.dependency.Poms.Pom;
import kanvas.dependency.Poms.PomDependency;
import kanvas.project.Dependency;
import kanvas.project.Dependency.Scope;

import java.util.*;

final class DependencyGraph {
    private static final int MAX_ROUNDS = 100;

    record Node(String key, String requested, String selected, Scope scope, boolean declared, boolean repeated, List<Node> children) {}
    record Selected(Dependency.Maven coordinate, boolean hasJar) {}
    record Result(List<Selected> selected, List<Node> tree) {}
    private interface PomLookup { Pom pom(String group, String artifact, String version) throws DependencyException; }
    private record Visit(String group, String artifact, String version, Scope scope, Set<String> exclusions, List<Node> into, boolean declared) {}
    private DependencyGraph() {}

    static Result resolve(List<Dependency.Maven> declared, Poms poms) throws DependencyException { return resolve(declared, poms::pom); }

    private static Result resolve(List<Dependency.Maven> declared, PomLookup poms) throws DependencyException {
        Map<String, String> pinned = new HashMap<>();
        for (Dependency.Maven dependency : declared) pinned.put(dependency.key(), dependency.version());
        Map<String, String> chosen = new HashMap<>(pinned);
        for (int round = 0; round < MAX_ROUNDS; round++) {
            Walk walk = new Walk(chosen, poms);
            walk.run(declared);
            Map<String, String> next = new HashMap<>(chosen);
            for (Map.Entry<String, List<String>> requests : walk.requested.entrySet()) {
                String key = requests.getKey();
                if (pinned.containsKey(key)) continue;
                List<String> candidates = new ArrayList<>();
                if (chosen.containsKey(key)) candidates.add(chosen.get(key));
                candidates.addAll(requests.getValue());
                next.put(key, MavenVersion.highest(candidates));
            }
            if (next.equals(chosen)) {
                if (!walk.problems.isEmpty()) throw new DependencyException(String.join("\n", new LinkedHashSet<>(walk.problems)));
                return new Result(walk.selected(), walk.tree);
            }
            chosen = next;
        }
        throw new DependencyException("Dependency versions didn't settle after " + MAX_ROUNDS + " rounds");
    }

    private static final class Walk {
        final Map<String, String> chosen;
        final PomLookup poms;
        final Map<String, List<String>> requested = new LinkedHashMap<>();
        final Map<String, Scope> scopes = new LinkedHashMap<>();
        final Map<String, Boolean> hasJar = new HashMap<>();
        final Map<String, Boolean> expanded = new HashMap<>();
        final List<Node> tree = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        Walk(Map<String, String> chosen, PomLookup poms) {
            this.chosen = chosen;
            this.poms = poms;
        }

        void run(List<Dependency.Maven> declared) throws DependencyException {
            Deque<Visit> queue = new ArrayDeque<>();
            for (Dependency.Maven dependency : declared)
                queue.add(new Visit(dependency.group(), dependency.artifact(), dependency.version(), dependency.scope(), Set.of(), tree, true));
            while (!queue.isEmpty()) {
                Visit visit = queue.poll();
                String key = visit.group() + ":" + visit.artifact();
                String version = chosen.get(key);
                if (version == null) {
                    String problem = Dependency.Maven.problem(visit.group(), visit.artifact(), visit.version());
                    if (problem != null) {
                        problems.add(key + (visit.version() == null ? "" : ":" + visit.version()) + ": " + problem
                            + ". Declare \"" + key + "\" in kanvas.toml to choose a version.");
                        continue;
                    }
                    requested.computeIfAbsent(key, k -> new ArrayList<>()).add(visit.version());
                    continue;
                }
                List<String> requests = requested.computeIfAbsent(key, k -> new ArrayList<>());
                if (Dependency.Maven.problem(visit.group(), visit.artifact(), visit.version()) == null) requests.add(visit.version());
                Scope scope = scopes.containsKey(key) ? scopes.get(key).union(visit.scope()) : visit.scope();
                scopes.put(key, scope);
                String expansion = key + "|" + visit.scope() + "|" + new TreeSet<>(visit.exclusions());
                List<Node> children = new ArrayList<>();
                if (expanded.containsKey(expansion)) {
                    visit.into().add(new Node(key, visit.version(), version, visit.scope(), visit.declared(), expanded.get(expansion), children));
                    continue;
                }
                visit.into().add(new Node(key, visit.version(), version, visit.scope(), visit.declared(), false, children));
                Pom pom = poms.pom(visit.group(), visit.artifact(), version);
                hasJar.put(key, !"pom".equals(pom.packaging()));
                boolean hasChildren = false;
                for (PomDependency dependency : pom.dependencies()) {
                    Scope childScope = transitiveScope(visit.scope(), dependency);
                    if (childScope == null || excluded(visit.exclusions(), dependency)) continue;
                    Set<String> exclusions = new HashSet<>(visit.exclusions());
                    exclusions.addAll(dependency.exclusions());
                    queue.add(new Visit(dependency.group(), dependency.artifact(), dependency.version(), childScope, exclusions, children, false));
                    hasChildren = true;
                }
                expanded.put(expansion, hasChildren);
            }
        }

        List<Selected> selected() {
            List<Selected> result = new ArrayList<>();
            for (Map.Entry<String, Scope> entry : scopes.entrySet()) {
                String[] parts = entry.getKey().split(":");
                result.add(new Selected(new Dependency.Maven(parts[0], parts[1], chosen.get(entry.getKey()), entry.getValue()),
                    hasJar.getOrDefault(entry.getKey(), true)));
            }
            return result;
        }
    }

    private static Scope transitiveScope(Scope parent, PomDependency dependency) {
        if (dependency.optional() || dependency.classifier() != null) return null;
        if (!List.of("jar", "bundle", "pom").contains(dependency.type())) return null;
        String scope = dependency.scope() == null ? "compile" : dependency.scope();
        return switch (scope) {
            case "compile" -> parent;
            case "runtime" -> ((parent == Scope.PROVIDED) ? Scope.PROVIDED : Scope.RUNTIME);
            default -> null;
        };
    }

    private static boolean excluded(Set<String> exclusions, PomDependency dependency) {
        for (String exclusion : exclusions) {
            String[] parts = exclusion.split(":", 2);
            if (parts.length == 2 && matches(parts[0], dependency.group()) && matches(parts[1], dependency.artifact())) return true;
        }
        return false;
    }

    private static boolean matches(String pattern, String value) { return "*".equals(pattern) || pattern.equals(value); }
}
