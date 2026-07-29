package dev.hypercore.plugin;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public final class ExternalPluginLoader implements AutoCloseable {
    public static final String DESCRIPTOR_ENTRY = "hypercore-plugin.json";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final PluginManager plugins;
    private final Path pluginDirectory;
    private final List<LoadedPlugin> loadedPlugins = new ArrayList<>();
    private LoadReport lastReport = LoadReport.empty();
    private boolean loadAttempted;

    public ExternalPluginLoader(PluginManager plugins, Path pluginDirectory) {
        this.plugins = java.util.Objects.requireNonNull(plugins, "plugins");
        this.pluginDirectory = java.util.Objects.requireNonNull(pluginDirectory, "pluginDirectory");
    }

    public synchronized LoadReport load() {
        if (loadAttempted) {
            throw new IllegalStateException("External plugin loading has already been attempted");
        }
        loadAttempted = true;

        List<String> errors = new ArrayList<>();
        List<Path> jars = discoverJars(errors);
        Map<String, Candidate> candidates = readCandidates(jars, errors);
        List<Candidate> ordered = orderCandidates(candidates, errors);
        List<String> loadedIds = new ArrayList<>();
        int skipped = candidates.size() - ordered.size();

        for (Candidate candidate : ordered) {
            if (candidate.descriptor.depends().stream().anyMatch(dependency ->
                candidates.containsKey(dependency) && !loadedIds.contains(dependency) && !plugins.contains(dependency)
            )) {
                skipped++;
                errors.add(candidate.descriptor.plugin().id() + ": dependency failed to load");
                continue;
            }

            ExternalPluginClassLoader classLoader = null;
            try {
                classLoader = new ExternalPluginClassLoader(
                    candidate.jar.toUri().toURL(),
                    ExternalPluginLoader.class.getClassLoader()
                );
                Class<?> mainClass = Class.forName(candidate.descriptor.mainClass(), true, classLoader);
                if (!HyperPlugin.class.isAssignableFrom(mainClass)) {
                    throw new IllegalArgumentException("Main class does not implement HyperPlugin");
                }
                HyperPlugin plugin = (HyperPlugin) mainClass.getDeclaredConstructor().newInstance();
                PluginManager.RegistrationResult result = plugins.registerExternal(
                    candidate.descriptor.plugin(),
                    plugin,
                    classLoader
                );
                if (!result.successful()) {
                    skipped++;
                    errors.add(candidate.descriptor.plugin().id() + ": lifecycle ended in " + result.state());
                    plugins.unregister(candidate.descriptor.plugin().id());
                    closeQuietly(classLoader);
                    continue;
                }
                loadedPlugins.add(new LoadedPlugin(candidate.descriptor.plugin().id(), classLoader));
                loadedIds.add(candidate.descriptor.plugin().id());
            } catch (ReflectiveOperationException | IOException | RuntimeException error) {
                skipped++;
                errors.add(candidate.descriptor.plugin().id() + ": " + describe(error));
                closeQuietly(classLoader);
            }
        }

        lastReport = new LoadReport(
            candidates.size(),
            loadedIds.size(),
            skipped,
            List.copyOf(loadedIds),
            List.copyOf(errors)
        );
        for (String error : errors) {
            LOGGER.warn("External plugin skipped: {}", error);
        }
        if (!loadedIds.isEmpty()) {
            LOGGER.info("Loaded {} external HyperCore plugin(s) from {}", loadedIds.size(), pluginDirectory);
        }
        return lastReport;
    }

    public synchronized LoadReport report() {
        return lastReport;
    }

    @Override
    public synchronized void close() {
        for (int index = loadedPlugins.size() - 1; index >= 0; index--) {
            LoadedPlugin loaded = loadedPlugins.get(index);
            plugins.unregister(loaded.id);
            closeQuietly(loaded.classLoader);
        }
        loadedPlugins.clear();
    }

    private List<Path> discoverJars(List<String> errors) {
        try {
            Files.createDirectories(pluginDirectory);
            try (var paths = Files.list(pluginDirectory)) {
                return paths
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .toList();
            }
        } catch (IOException error) {
            errors.add("plugin directory: " + describe(error));
            return List.of();
        }
    }

    private Map<String, Candidate> readCandidates(List<Path> jars, List<String> errors) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Set<String> duplicateIds = new HashSet<>();
        for (Path jar : jars) {
            try (JarFile archive = new JarFile(jar.toFile())) {
                var entry = archive.getJarEntry(DESCRIPTOR_ENTRY);
                if (entry == null) {
                    continue;
                }
                ExternalPluginDescriptor descriptor;
                try (Reader reader = new java.io.InputStreamReader(archive.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                    descriptor = ExternalPluginDescriptor.parse(reader);
                }
                String id = descriptor.plugin().id();
                if (plugins.contains(id) || duplicateIds.contains(id)) {
                    errors.add(id + ": duplicate plugin id");
                    continue;
                }
                if (candidates.putIfAbsent(id, new Candidate(jar, descriptor)) != null) {
                    candidates.remove(id);
                    duplicateIds.add(id);
                    errors.add(id + ": duplicate plugin id");
                }
            } catch (IOException | RuntimeException error) {
                errors.add(jar.getFileName() + ": invalid descriptor: " + describe(error));
            }
        }
        return candidates;
    }

    private List<Candidate> orderCandidates(Map<String, Candidate> candidates, List<String> errors) {
        Set<String> blocked = new LinkedHashSet<>();
        for (Candidate candidate : candidates.values()) {
            for (String dependency : candidate.descriptor.depends()) {
                if (!candidates.containsKey(dependency) && !plugins.contains(dependency)) {
                    blocked.add(candidate.descriptor.plugin().id());
                    errors.add(candidate.descriptor.plugin().id() + ": missing dependency " + dependency);
                }
            }
        }
        boolean changed;
        do {
            changed = false;
            for (Candidate candidate : candidates.values()) {
                if (blocked.contains(candidate.descriptor.plugin().id())) {
                    continue;
                }
                if (candidate.descriptor.depends().stream().anyMatch(blocked::contains)) {
                    blocked.add(candidate.descriptor.plugin().id());
                    errors.add(candidate.descriptor.plugin().id() + ": dependency is unavailable");
                    changed = true;
                }
            }
        } while (changed);

        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (String id : candidates.keySet()) {
            if (!blocked.contains(id)) {
                indegree.put(id, 0);
            }
        }
        for (Candidate candidate : candidates.values()) {
            String id = candidate.descriptor.plugin().id();
            if (!indegree.containsKey(id)) {
                continue;
            }
            for (String dependency : candidate.descriptor.depends()) {
                if (indegree.containsKey(dependency)) {
                    indegree.put(id, indegree.get(id) + 1);
                    dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(id);
                }
            }
        }
        addSoftDependencyEdges(candidates, indegree, dependents);

        Queue<String> ready = indegree.entrySet().stream()
            .filter(entry -> entry.getValue() == 0)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(ArrayDeque::new));
        List<Candidate> ordered = new ArrayList<>(indegree.size());
        while (!ready.isEmpty()) {
            String id = ready.remove();
            ordered.add(candidates.get(id));
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                int remaining = indegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (ordered.size() != indegree.size()) {
            for (String id : indegree.keySet()) {
                if (ordered.stream().noneMatch(candidate -> candidate.descriptor.plugin().id().equals(id))) {
                    errors.add(id + ": dependency cycle");
                }
            }
        }
        return ordered;
    }

    private static void addSoftDependencyEdges(
        Map<String, Candidate> candidates,
        Map<String, Integer> indegree,
        Map<String, List<String>> dependents
    ) {
        for (Candidate candidate : candidates.values()) {
            String id = candidate.descriptor.plugin().id();
            if (!indegree.containsKey(id)) {
                continue;
            }
            for (String dependency : candidate.descriptor.softDepends()) {
                if (!indegree.containsKey(dependency)
                    || dependents.getOrDefault(dependency, List.of()).contains(id)
                    || hasPath(dependents, id, dependency)) {
                    continue;
                }
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(id);
                indegree.put(id, indegree.get(id) + 1);
            }
        }
    }

    private static boolean hasPath(Map<String, List<String>> dependents, String start, String target) {
        Queue<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String current = pending.remove();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(target)) {
                return true;
            }
            pending.addAll(dependents.getOrDefault(current, List.of()));
        }
        return false;
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause instanceof java.lang.reflect.InvocationTargetException) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static void closeQuietly(ExternalPluginClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (IOException error) {
            LOGGER.warn("Failed to close external plugin class loader", error);
        }
    }

    private record Candidate(Path jar, ExternalPluginDescriptor descriptor) {
    }

    private record LoadedPlugin(String id, ExternalPluginClassLoader classLoader) {
    }

    public record LoadReport(
        int discovered,
        int loaded,
        int skipped,
        List<String> loadOrder,
        List<String> errors
    ) {
        private static LoadReport empty() {
            return new LoadReport(0, 0, 0, List.of(), List.of());
        }
    }
}
