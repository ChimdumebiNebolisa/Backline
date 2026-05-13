package dev.backline.cli.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Locates built Spring Boot JARs for {@code backline sample serve} and {@code backline worker}. Resolution
 * order: explicit env var ({@code BACKLINE_SAMPLE_API_JAR} / {@code BACKLINE_WORKER_JAR}), then walk upward
 * from the current working directory for {@code apps/sample-api/build/libs} or {@code apps/worker/build/libs}
 * and pick the newest {@code *.jar}.
 */
public final class JarResolution {

    private JarResolution() {}

    public static Optional<Path> resolveSampleApiJar() {
        return resolveFromEnv("BACKLINE_SAMPLE_API_JAR")
                .or(() -> resolveNewestJar(walkParents(Path.of("").toAbsolutePath()), Path.of("apps", "sample-api", "build", "libs")));
    }

    public static Optional<Path> resolveWorkerJar() {
        return resolveFromEnv("BACKLINE_WORKER_JAR")
                .or(() -> resolveNewestJar(walkParents(Path.of("").toAbsolutePath()), Path.of("apps", "worker", "build", "libs")));
    }

    private static Optional<Path> resolveFromEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            return Optional.empty();
        }
        Path p = Path.of(v);
        if (Files.isRegularFile(p)) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    private static List<Path> walkParents(Path start) {
        java.util.ArrayList<Path> list = new java.util.ArrayList<>();
        Path cur = start;
        while (cur != null) {
            list.add(cur);
            cur = cur.getParent();
        }
        return list;
    }

    private static Optional<Path> resolveNewestJar(List<Path> roots, Path relativeLibs) {
        for (Path root : roots) {
            Path libs = root.resolve(relativeLibs);
            if (!Files.isDirectory(libs)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(libs)) {
                Optional<Path> jar = stream
                        .filter(p -> p.toString().endsWith(".jar"))
                        .max(Comparator.comparingLong(JarResolution::lastModifiedSafe));
                if (jar.isPresent()) {
                    return jar;
                }
            } catch (IOException ignored) {
                // try next root
            }
        }
        return Optional.empty();
    }

    private static long lastModifiedSafe(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
