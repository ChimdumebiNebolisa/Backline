package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.config.ConfigParseException;
import dev.backline.config.ConfigParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Verifies API health, optional {@code backline.yml} readability, and that {@code BACKLINE_API_URL} is usable.
 */
@Command(mixinStandardHelpOptions = true, name = "doctor", description = "Check local CLI prerequisites and API connectivity.")
public class DoctorCommand implements Callable<Integer> {

    private static final String SAMPLE_API_HEALTH_URL = "http://localhost:8081/health";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    @ParentCommand
    private Backline parent;

    @Option(names = {"--check-sample-api"}, description = "Also probe sample API at " + SAMPLE_API_HEALTH_URL)
    private boolean checkSampleApi;

    @Override
    public Integer call() throws Exception {
        boolean ok = true;
        ok &= checkApi();
        ok &= checkConfig();
        ok &= checkEnv();
        if (checkSampleApi) {
            ok &= checkSampleApiHealth();
        }
        return ok ? 0 : 1;
    }

    private boolean checkApi() {
        try {
            new BacklineApiClient(parent.apiUrl()).getApiHealth();
            System.out.println("OK API health reachable at " + parent.apiUrl());
            return true;
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.out.println("FAIL API health at " + parent.apiUrl() + ": " + detail);
            System.out.println("  fix: start PostgreSQL, then ./gradlew :apps:api:bootRun");
            System.out.println("  fix: verify BACKLINE_API_URL or --api-url points at the API base URL");
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
        } catch (ConfigParseException e) {
            System.out.println("FAIL backline.yml: " + e.getMessage());
            if (e.field() != null && !e.field().isBlank()) {
                System.out.println("  fix: correct the " + e.field() + " field in backline.yml");
            } else {
                System.out.println("  fix: repair YAML syntax or validation errors in backline.yml");
            }
            return false;
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.out.println("FAIL backline.yml: " + detail);
            System.out.println("  fix: repair YAML syntax or validation errors in backline.yml");
            return false;
        }
    }

    private boolean checkEnv() {
        String raw = System.getenv("BACKLINE_API_URL");
        if (raw != null && raw.isBlank()) {
            System.out.println("FAIL BACKLINE_API_URL is set but blank");
            System.out.println("  fix: unset BACKLINE_API_URL or set it to a valid API base URL (e.g. http://localhost:8080)");
            return false;
        }
        System.out.println("OK BACKLINE_API_URL " + (raw == null ? "unset (using default or --api-url)" : "set"));
        return true;
    }

    private boolean checkSampleApiHealth() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(SAMPLE_API_HEALTH_URL))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("OK sample API health reachable at " + SAMPLE_API_HEALTH_URL);
                return true;
            }
            System.out.println("FAIL sample API health at " + SAMPLE_API_HEALTH_URL + ": HTTP " + response.statusCode());
            System.out.println("  fix: start the sample API with ./gradlew :apps:sample-api:bootRun or backline sample serve");
            return false;
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.out.println("FAIL sample API health at " + SAMPLE_API_HEALTH_URL + ": " + detail);
            System.out.println("  fix: start the sample API with ./gradlew :apps:sample-api:bootRun or backline sample serve");
            return false;
        }
    }
}
