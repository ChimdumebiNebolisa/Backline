package dev.backline.cli.commands;

import dev.backline.cli.launch.JarResolution;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Runs the worker JAR in the foreground. Prefers {@code BACKLINE_WORKER_JAR}; otherwise searches upward for
 * {@code apps/worker/build/libs/*.jar}. Build first with {@code ./gradlew :apps:worker:bootJar}.
 */
@Command(mixinStandardHelpOptions = true, name = "worker", description = "Run the Backline worker JAR (foreground).")
public class WorkerCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        Optional<Path> jar = JarResolution.resolveWorkerJar();
        if (jar.isEmpty()) {
            System.err.println(
                    "Worker JAR not found. Set BACKLINE_WORKER_JAR or run ./gradlew :apps:worker:bootJar first.");
            return 1;
        }
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
