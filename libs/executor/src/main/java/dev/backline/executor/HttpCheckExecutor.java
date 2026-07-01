package dev.backline.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.api.dto.AssertionResultDto;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.constants.ResponseLimits;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes one HTTP check and evaluates status, latency, and JSONPath assertions.
 *
 * <p>This class is intentionally free of Spring and logging. Request and response header values are
 * not logged here, so there is no risk of leaking {@code Authorization}, {@code Cookie}, or
 * {@code Set-Cookie} through this component. Callers that log check metadata must apply the same
 * discipline using {@link dev.backline.core.constants.SensitiveHeaders}.
 */
public final class HttpCheckExecutor {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public HttpCheckExecutor(HttpClient httpClient, ObjectMapper objectMapper) {
        this(httpClient, objectMapper, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Builds an {@link HttpClient} with a 5s connect timeout, {@link HttpClient.Redirect#NEVER}, and
     * the default 30s per-request timeout.
     */
    public HttpCheckExecutor() {
        this(defaultHttpClient(), new ObjectMapper(), DEFAULT_REQUEST_TIMEOUT);
    }

    public HttpCheckExecutor(HttpClient httpClient, ObjectMapper objectMapper, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public HttpCheckOutcome execute(HttpCheckRequest request) {
        Objects.requireNonNull(request, "request");

        URI uri;
        try {
            uri = URI.create(request.url());
        } catch (IllegalArgumentException ex) {
            return invalidUrlOutcome("INVALID_URL", "URL is not a valid URI");
        }

        if (!uri.isAbsolute()) {
            return invalidUrlOutcome("INVALID_URL", "URL must be absolute");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return invalidUrlOutcome("INVALID_URL", "URL scheme must be http or https");
        }

        HttpRequest httpRequest = buildHttpRequest(request, uri);

        Instant start = Instant.now();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            return evaluateResponse(request, response, latencyMs);
        } catch (HttpTimeoutException ex) {
            return new HttpCheckOutcome(
                    CheckResultStatus.ERROR,
                    null,
                    null,
                    "TIMEOUT",
                    "Request timed out after " + requestTimeout.toMillis() + " ms",
                    null,
                    List.of());
        } catch (IOException ex) {
            return new HttpCheckOutcome(
                    CheckResultStatus.ERROR,
                    null,
                    null,
                    "TRANSPORT_ERROR",
                    ex.getMessage(),
                    null,
                    List.of());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new HttpCheckOutcome(
                    CheckResultStatus.ERROR,
                    null,
                    null,
                    "TRANSPORT_ERROR",
                    "Interrupted while waiting for HTTP response",
                    null,
                    List.of());
        }
    }

    private static HttpCheckOutcome invalidUrlOutcome(String code, String message) {
        return new HttpCheckOutcome(CheckResultStatus.ERROR, null, null, code, message, null, List.of());
    }

    private HttpRequest buildHttpRequest(HttpCheckRequest request, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout);

        if (request.headers() != null) {
            for (var e : request.headers().entrySet()) {
                String name = e.getKey();
                if (name == null || name.isBlank()) {
                    continue;
                }
                builder.header(name, e.getValue());
            }
        }

        HttpMethod method = request.method();
        return switch (method) {
            case GET -> builder.GET().build();
            case DELETE -> builder.method("DELETE", HttpRequest.BodyPublishers.noBody()).build();
            case HEAD -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            case POST -> builder.POST(HttpRequest.BodyPublishers.ofString("")).build();
            case PUT -> builder.PUT(HttpRequest.BodyPublishers.ofString("")).build();
            case PATCH -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString("")).build();
            case OPTIONS -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        };
    }

    private HttpCheckOutcome evaluateResponse(HttpCheckRequest request, HttpResponse<String> response, long latencyMs) {
        int actualStatus = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        String preview = buildResponsePreview(body);

        CheckResultStatus status = CheckResultStatus.PASSED;
        String errorCode = null;
        String errorMessage = null;

        if (request.maxLatencyMs() != null && latencyMs > request.maxLatencyMs()) {
            status = CheckResultStatus.FAILED;
            errorCode = "LATENCY_EXCEEDED";
            errorMessage = "Latency " + latencyMs + " ms exceeded max " + request.maxLatencyMs() + " ms";
        }

        if (actualStatus != request.expectedStatus()) {
            status = CheckResultStatus.FAILED;
            if (errorCode == null) {
                errorCode = "STATUS_MISMATCH";
                errorMessage = "Expected status " + request.expectedStatus() + " but was " + actualStatus;
            }
        }

        List<AssertionDto> assertionDefs = request.assertions() == null ? List.of() : request.assertions();
        List<AssertionResultDto> assertionResults = List.of();
        if (!assertionDefs.isEmpty() && !body.isEmpty()) {
            assertionResults = evaluateAssertions(assertionDefs, body);
            boolean anyFailed = assertionResults.stream().anyMatch(r -> !r.passed());
            if (anyFailed) {
                status = CheckResultStatus.FAILED;
                if (errorCode == null) {
                    errorCode = "ASSERTION_FAILED";
                    errorMessage = "One or more assertions failed";
                }
            }
        }

        if (status == CheckResultStatus.PASSED) {
            errorCode = null;
            errorMessage = null;
        }

        return new HttpCheckOutcome(status, actualStatus, latencyMs, errorCode, errorMessage, preview, assertionResults);
    }

    private List<AssertionResultDto> evaluateAssertions(List<AssertionDto> assertions, String body) {
        List<AssertionResultDto> results = new ArrayList<>();
        for (AssertionDto assertion : assertions) {
            results.add(evaluateOneAssertion(assertion, body));
        }
        return results;
    }

    private AssertionResultDto evaluateOneAssertion(AssertionDto assertion, String body) {
        String path = assertion.path();
        Object actual = null;
        boolean pathPresent = false;
        try {
            actual = JsonPath.read(body, path);
            pathPresent = true;
        } catch (PathNotFoundException ex) {
            pathPresent = false;
        }

        Boolean expectedExists = assertion.exists();
        Object expectedEquals = assertion.equalsValue();

        if (expectedExists != null && expectedExists) {
            boolean passed = pathPresent;
            String message = passed ? null : "Expected JSONPath to exist: " + path;
            return new AssertionResultDto(path, expectedEquals, expectedExists, actual, passed, message);
        }

        if (expectedEquals != null) {
            if (!pathPresent) {
                return new AssertionResultDto(
                        path,
                        expectedEquals,
                        expectedExists,
                        null,
                        false,
                        "JSONPath not found: " + path);
            }
            boolean passed = valuesEqual(expectedEquals, actual);
            String message = passed ? null : "Value mismatch at " + path;
            return new AssertionResultDto(path, expectedEquals, expectedExists, actual, passed, message);
        }

        boolean passed = true;
        return new AssertionResultDto(path, expectedEquals, expectedExists, actual, passed, null);
    }

    private static boolean valuesEqual(Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            return true;
        }
        if (expected instanceof Number n1 && actual instanceof Number n2) {
            return new BigDecimal(n1.toString()).compareTo(new BigDecimal(n2.toString())) == 0;
        }
        return false;
    }

    private String buildResponsePreview(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        int max = ResponseLimits.RESPONSE_PREVIEW_MAX_BYTES;
        if (bytes.length <= max) {
            return body;
        }
        String truncated = decodeUtf8Preview(bytes, max);
        return truncated + "...[truncated]";
    }

    private static String decodeUtf8Preview(byte[] bytes, int maxBytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(bytes, 0, maxBytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalStateException("Failed to decode response preview", ex);
        }
    }
}
