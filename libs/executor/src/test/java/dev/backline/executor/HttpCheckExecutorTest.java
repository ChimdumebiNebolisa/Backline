package dev.backline.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.constants.ResponseLimits;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCheckExecutorTest {

    private MockWebServer server;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void passesWithMatchingStatusLatencyAndAssertions() {
        server.enqueue(new MockResponse().setBody("{\"id\":1}").addHeader("Content-Type", "application/json"));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpCheckExecutor executor = new HttpCheckExecutor(client, mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                30_000,
                List.of(new AssertionDto("$.id", 1, null)),
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.PASSED);
        assertThat(outcome.actualStatus()).isEqualTo(200);
        assertThat(outcome.latencyMs()).isNotNull();
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isTrue();
    }

    @Test
    void failsLatencyWhenMaxLatencyExceeded() throws InterruptedException {
        server.enqueue(new MockResponse().setBodyDelay(50, java.util.concurrent.TimeUnit.MILLISECONDS).setBody("{}"));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpCheckExecutor executor = new HttpCheckExecutor(client, mapper, Duration.ofSeconds(30));

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/slow").toString(),
                200,
                1,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.FAILED);
        assertThat(outcome.errorCode()).isEqualTo("LATENCY_EXCEEDED");
    }

    @Test
    void failsStatusMismatchOn500() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("err"));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.FAILED);
        assertThat(outcome.errorCode()).isEqualTo("STATUS_MISMATCH");
        assertThat(outcome.actualStatus()).isEqualTo(500);
    }

    @Test
    void transportErrorWhenConnectionRefused() {
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);
        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                "http://127.0.0.1:1/nope",
                200,
                null,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.ERROR);
        assertThat(outcome.errorCode()).isEqualTo("TRANSPORT_ERROR");
    }

    @Test
    void timesOutWithShortRequestTimeout() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(200))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpCheckExecutor executor = new HttpCheckExecutor(client, mapper, Duration.ofMillis(200));

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/hang").toString(),
                200,
                null,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.ERROR);
        assertThat(outcome.errorCode()).isEqualTo("TIMEOUT");
        assertThat(outcome.errorMessage()).contains("200");
    }

    @Test
    void assertionEqualsMatchesNumericTypes() {
        server.enqueue(new MockResponse().setBody("{\"id\":1}"));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                List.of(new AssertionDto("$.id", 1, null)),
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.PASSED);
    }

    @Test
    void assertionEqualsMismatchFails() {
        server.enqueue(new MockResponse().setBody("{\"id\":1}"));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                List.of(new AssertionDto("$.id", 2, null)),
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.FAILED);
        assertThat(outcome.errorCode()).isEqualTo("ASSERTION_FAILED");
    }

    @Test
    void assertionExistsFailsWhenMissing() {
        server.enqueue(new MockResponse().setBody("{\"id\":1}"));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                List.of(new AssertionDto("$.missing", null, true)),
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.FAILED);
        assertThat(outcome.errorCode()).isEqualTo("ASSERTION_FAILED");
    }

    @Test
    void assertionExistsFalsePassesWhenMissing() {
        server.enqueue(new MockResponse().setBody("{\"id\":1}"));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                List.of(new AssertionDto("$.missing", null, false)),
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.PASSED);
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isTrue();
    }

    @Test
    void assertionExistsFalseFailsWhenPresent() {
        server.enqueue(new MockResponse().setBody("{\"id\":1}"));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                List.of(new AssertionDto("$.id", null, false)),
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.FAILED);
        assertThat(outcome.errorCode()).isEqualTo("ASSERTION_FAILED");
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isFalse();
        assertThat(outcome.assertionResults().getFirst().message()).contains("Expected JSONPath to be absent");
    }

    @Test
    void assertionExistsFailsWhenBodyIsEmpty() {
        server.enqueue(new MockResponse().setBody(""));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                List.of(new AssertionDto("$.id", null, true)),
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.FAILED);
        assertThat(outcome.errorCode()).isEqualTo("ASSERTION_FAILED");
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isFalse();
    }

    @Test
    void assertionExistsFalsePassesWhenBodyIsEmpty() {
        server.enqueue(new MockResponse().setBody(""));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                List.of(new AssertionDto("$.id", null, false)),
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.PASSED);
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isTrue();
    }

    @Test
    void responsePreviewTruncatesLargeBody() {
        int size = ResponseLimits.RESPONSE_PREVIEW_MAX_BYTES + 100;
        StringBuilder sb = new StringBuilder(size);
        sb.append("x".repeat(size));
        server.enqueue(new MockResponse().setBody(sb.toString()));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.responsePreview()).endsWith("...[truncated]");
        byte[] previewBytes = outcome.responsePreview().replace("...[truncated]", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(previewBytes.length).isLessThanOrEqualTo(ResponseLimits.RESPONSE_PREVIEW_MAX_BYTES);
    }

    @Test
    void responsePreviewTruncatesMultibyteBodyWithinByteLimit() {
        String body = "\u20AC".repeat(ResponseLimits.RESPONSE_PREVIEW_MAX_BYTES);
        server.enqueue(new MockResponse().setBody(body));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/").toString(),
                200,
                null,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.responsePreview()).endsWith("...[truncated]");
        byte[] previewBytes = outcome.responsePreview().replace("...[truncated]", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(previewBytes.length).isLessThanOrEqualTo(ResponseLimits.RESPONSE_PREVIEW_MAX_BYTES);
    }

    @Test
    void invalidUrlReturnsInvalidUrlError() {
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);
        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                "foo",
                200,
                null,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.ERROR);
        assertThat(outcome.errorCode()).isEqualTo("INVALID_URL");
        assertThat(outcome.latencyMs()).isNull();
    }

    @Test
    void hostlessHttpUrlReturnsInvalidUrlError() {
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);
        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                "http:///health",
                200,
                null,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.ERROR);
        assertThat(outcome.errorCode()).isEqualTo("INVALID_URL");
        assertThat(outcome.latencyMs()).isNull();
    }

    @Test
    void redirectsAreNotFollowed() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(302)
                        .addHeader("Location", server.url("/target").toString()));
        HttpCheckExecutor executor = new HttpCheckExecutor(defaultClient(), mapper);

        HttpCheckRequest request = new HttpCheckRequest(
                null,
                "k",
                "n",
                HttpMethod.GET,
                server.url("/redirect").toString(),
                200,
                null,
                null,
                null);

        HttpCheckOutcome outcome = executor.execute(request);
        assertThat(outcome.actualStatus()).isEqualTo(302);
        assertThat(outcome.status()).isEqualTo(CheckResultStatus.FAILED);
        assertThat(outcome.errorCode()).isEqualTo("STATUS_MISMATCH");
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
