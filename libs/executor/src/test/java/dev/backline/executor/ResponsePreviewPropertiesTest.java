package dev.backline.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.check.HttpMethod;
import dev.backline.core.constants.ResponseLimits;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsePreviewPropertiesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Property
    void previewRespectsConfiguredByteLimit(@ForAll @StringLength(max = 12_000) String body) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(body));
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
                    null,
                    null,
                    null);

            HttpCheckOutcome outcome = executor.execute(request);
            String preview = outcome.responsePreview();
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

            if (bodyBytes.length <= ResponseLimits.RESPONSE_PREVIEW_MAX_BYTES) {
                assertThat(preview).isEqualTo(body);
            } else {
                assertThat(preview).endsWith("...[truncated]");
                byte[] previewBytes =
                        preview.replace("...[truncated]", "").getBytes(StandardCharsets.UTF_8);
                assertThat(previewBytes.length).isLessThanOrEqualTo(ResponseLimits.RESPONSE_PREVIEW_MAX_BYTES);
            }
        }
    }
}
