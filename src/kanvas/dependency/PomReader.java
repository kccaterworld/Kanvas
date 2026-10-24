package kanvas.dependency;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.*;

final class PomReader {
    record Parent(String group, String artifact, String version) {}

    record RawDependency(String group, String artifact, String version, String type, String classifier,
                         String scope, boolean optional, List<String> exclusions) {}

    record RawPom(Parent parent, String group, String artifact, String version, String packaging,
                  Map<String, String> properties, List<RawDependency> dependencies, List<RawDependency> managed) {}

    private PomReader() {}

    static RawPom read(Path file) throws DependencyException {
        Element project;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            project = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        } catch (Exception e) { throw new DependencyException("Couldn't read " + file + ": " + e.getMessage(), e); }
        if (!"project".equals(name(project))) throw new DependencyException(file + " is not a POM (its root element is <" + name(project) + ">)");

        Element parent = child(project, "parent");
        Map<String, String> properties = new LinkedHashMap<>();
        Element props = child(project, "properties");
        if (props != null) for (Element property : children(props, null)) properties.put(name(property), property.getTextContent().trim());
        Element management = child(project, "dependencyManagement");
        return new RawPom(
            parent == null ? null : new Parent(text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version")),
            text(project, "groupId"), text(project, "artifactId"), text(project, "version"), text(project, "packaging"),
            properties, dependencies(child(project, "dependencies")), dependencies(management == null ? null : child(management, "dependencies")));
    }

    private static List<RawDependency> dependencies(Element list) {
        List<RawDependency> result = new ArrayList<>();
        if (list == null) return result;
        for (Element dependency : children(list, "dependency")) {
            List<String> exclusions = new ArrayList<>();
            Element excluded = child(dependency, "exclusions");
            if (excluded != null)
                for (Element exclusion : children(excluded, "exclusion")) exclusions.add(text(exclusion, "groupId") + ":" + text(exclusion, "artifactId"));
            result.add(new RawDependency(text(dependency, "groupId"), text(dependency, "artifactId"), text(dependency, "version"),
                text(dependency, "type"), text(dependency, "classifier"), text(dependency, "scope"),
                "true".equalsIgnoreCase(text(dependency, "optional")), exclusions));
        }
        return result;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if ((node instanceof Element element) && (name == null || name.equals(name(element))))
                result.add(element);
        return result;
    }

    private static Element child(Element parent, String name) {
        List<Element> matches = children(parent, name);
        return ((matches.isEmpty()) ? null : matches.getFirst());
    }

    private static String text(Element parent, String name) {
        Element element = child(parent, name);
        if (element == null) return null;
        String value = element.getTextContent().trim();
        return ((value.isEmpty()) ? null : value);
    }

    private static String name(Element element) {
        String name = element.getTagName();
        return name.substring(name.indexOf(':') + 1);
    }
}
