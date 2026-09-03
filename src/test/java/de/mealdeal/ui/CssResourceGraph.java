package de.mealdeal.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads a classpath CSS entry point and its relative imports for resource tests. */
final class CssResourceGraph {

    private static final Pattern IMPORT = Pattern.compile(
            "@import\\s+(?:url\\(\\s*)?[\"']([^\"']+)[\"']\\s*\\)?\\s*;");

    private final String entryPath;
    private final Map<String, String> resources;
    private final List<String> cascadeOrder;

    private CssResourceGraph(String entryPath, Map<String, String> resources,
                             List<String> cascadeOrder) {
        this.entryPath = entryPath;
        this.resources = Map.copyOf(resources);
        this.cascadeOrder = List.copyOf(cascadeOrder);
    }

    static CssResourceGraph load(String entryPath) throws IOException {
        LinkedHashMap<String, String> resources = new LinkedHashMap<>();
        List<String> cascadeOrder = new ArrayList<>();
        load(entryPath, resources, cascadeOrder, new LinkedHashSet<>());
        return new CssResourceGraph(entryPath, resources, cascadeOrder);
    }

    String entryCss() {
        return resources.get(entryPath);
    }

    List<String> directImports() {
        return imports(entryPath, entryCss());
    }

    Set<String> resourcePaths() {
        return resources.keySet();
    }

    String combinedCss() {
        return cascadeOrder.stream()
                .map(resources::get)
                .map(css -> IMPORT.matcher(css).replaceAll(""))
                .reduce("", (combined, css) -> combined + System.lineSeparator() + css);
    }

    private static void load(String path, Map<String, String> resources,
                             List<String> cascadeOrder, Set<String> activePaths)
            throws IOException {
        if (activePaths.contains(path)) {
            throw new IllegalStateException("Cyclic CSS import: " + path);
        }
        if (resources.containsKey(path)) {
            return;
        }
        activePaths.add(path);
        String css = read(path);
        resources.put(path, css);
        for (String importedPath : imports(path, css)) {
            load(importedPath, resources, cascadeOrder, activePaths);
        }
        activePaths.remove(path);
        cascadeOrder.add(path);
    }

    private static List<String> imports(String ownerPath, String css) {
        List<String> paths = new ArrayList<>();
        Matcher matcher = IMPORT.matcher(css);
        while (matcher.find()) {
            paths.add(resolve(ownerPath, matcher.group(1)));
        }
        return List.copyOf(paths);
    }

    private static String resolve(String ownerPath, String importedPath) {
        if (importedPath.startsWith("/")) {
            return importedPath;
        }
        URI owner = URI.create("https://classpath.invalid" + ownerPath);
        return owner.resolve(importedPath).getPath();
    }

    private static String read(String path) throws IOException {
        try (InputStream input = CssResourceGraph.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing CSS resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
