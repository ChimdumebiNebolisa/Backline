package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.config.ConfigParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Verifies API health, optional {@code backline.yml} readability, and that {@code BACKLINE_API_URL} is usable.
 */
@Command(mixinStandardHelpOptions = true, name = "doctor", description = "Check local CLI prerequisites and API connectivity.")
public class DoctorCommand implements Callable<Integer> {

    @ParentCommand
    private Backline parent;

    @Override
    public Integer call() throws Exception {
        boolean ok = true;
        ok &= checkApi();
        ok &= checkConfig();
        ok &= checkEnv();
        return ok ? 0 : 1;
    }

    private boolean checkApi() {
        try {
            new BacklineApiClient(parent.apiUrl()).getApiHealth();
            System.out.println("OK API health reachable at " + parent.apiUrl());
            return true;
        } catch (Exception e) {
            System.out.println("FAIL API health: " + e.getMessage());
            return false;
        }
    }

    private boolean checkConfig() {
        Path yml = Path.of("backline.yml");
        if (!Files.isRegularFile(yml)) {
            System.out.println("OK backline.yml not present (skipped)");
            return true;
        }
        try {
            new ConfigParser().parse(yml.toAbsolutePath().normalize());
            System.out.println("OK backline.yml parses");
            return true;
        } catch (Exception e) {
            System.out.println("FAIL backline.yml: " + e.getMessage());
            return false;
        }
    }

    private boolean checkEnv() {
        String raw = System.getenv("BACKLINE_API_URL");
        if (raw != null && raw.isBlank()) {
            System.out.println("FAIL BACKLINE_API_URL is set but blank");
            return false;
        }
        System.out.println("OK BACKLINE_API_URL " + (raw == null ? "unset (using default or --api-url)" : "set"));
        return true;
    }
}
