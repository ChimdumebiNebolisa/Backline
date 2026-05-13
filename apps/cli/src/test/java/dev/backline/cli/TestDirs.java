package dev.backline.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class TestDirs {

    private TestDirs() {}

    public static void wipeDefaultWorkDir() throws IOException {
        Path root = Path.of("").toAbsolutePath().normalize();
        Files.deleteIfExists(root.resolve("backline.yml"));
        Path examples = root.resolve("examples");
        if (Files.isDirectory(examples)) {
            try (var walk = Files.walk(examples)) {
                walk.sorted(Comparator.reverseOrder()).forEach(TestDirs::deleteQuietly);
            }
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best-effort cleanup for isolated Gradle test workingDir
        }
    }
}
