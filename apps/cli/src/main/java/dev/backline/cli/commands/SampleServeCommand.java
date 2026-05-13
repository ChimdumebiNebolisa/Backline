package dev.backline.cli.commands;

import dev.backline.cli.launch.JarResolution;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Starts the sample API via {@code java -jar}. Prefers {@code BACKLINE_SAMPLE_API_JAR}; otherwise searches
 * upward from the working directory for {@code apps/sample-api/build/libs/*.jar}. Build first with
 * {@code ./gradlew :apps:sample-api:bootJar}.
 */
@Command(mixinStandardHelpOptions = true, name = "serve", description = "Run the local sample API JAR (foreground).")
public class SampleServeCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        Optional<Path> jar = JarResolution.resolveSampleApiJar();
        if (jar.isEmpty()) {
            System.err.println(
                    "Sample API JAR not found. Set BACKLINE_SAMPLE_API_JAR or run ./gradlew :apps:sample-api:bootJar first.");
            return 1;
        }
        System.out.println("Sample API starting on http://localhost:8081");
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.get().toString());
        pb.inheritIO();
        Process process = pb.start();
        Thread hook = new Thread(() -> {
            if (process.isAlive()) {
                process.destroy();
            }
        });
        Runtime.getRuntime().addShutdownHook(hook);
        int code = process.waitFor();
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM is shutting down
        }
        return code;
    }
}
