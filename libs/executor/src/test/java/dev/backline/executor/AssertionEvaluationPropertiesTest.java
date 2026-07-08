package dev.backline.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.core.api.dto.AssertionDto;
import dev.backline.core.check.CheckResultStatus;
import dev.backline.core.check.HttpMethod;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionEvaluationPropertiesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Property
    void equalsAssertionPassesForGeneratedStringValue(
            @ForAll @StringLength(min = 0, max = 80) @AlphaChars String value) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("field", value));
        HttpCheckOutcome outcome = executeWithBody(body, List.of(new AssertionDto("$.field", value, null)));

        assertThat(outcome.status()).isEqualTo(CheckResultStatus.PASSED);
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isTrue();
    }

    @Property
    void equalsAssertionPassesForGeneratedInteger(@ForAll int value) throws Exception {
        String body = "{\"n\":" + value + "}";
        HttpCheckOutcome outcome = executeWithBody(body, List.of(new AssertionDto("$.n", value, null)));

        assertThat(outcome.status()).isEqualTo(CheckResultStatus.PASSED);
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isTrue();
    }

    @Property
    void existsTruePassesWhenPathPresent(
            @ForAll @StringLength(min = 1, max = 20) @AlphaChars String fieldName, @ForAll int value)
            throws Exception {
        String body = "{\"" + fieldName + "\":" + value + "}";
        HttpCheckOutcome outcome =
                executeWithBody(body, List.of(new AssertionDto("$." + fieldName, null, true)));

        assertThat(outcome.status()).isEqualTo(CheckResultStatus.PASSED);
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isTrue();
    }

    @Property
    void existsFalsePassesWhenPathAbsent(@ForAll @StringLength(min = 1, max = 20) @AlphaChars String fieldName)
            throws Exception {
        String body = "{\"other\":1}";
        HttpCheckOutcome outcome =
                executeWithBody(body, List.of(new AssertionDto("$." + fieldName, null, false)));

        assertThat(outcome.status()).isEqualTo(CheckResultStatus.PASSED);
        assertThat(outcome.assertionResults()).hasSize(1);
        assertThat(outcome.assertionResults().getFirst().passed()).isTrue();
    }

    private HttpCheckOutcome executeWithBody(String body, List<AssertionDto> assertions) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
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
                    assertions,
                    null);
            return executor.execute(request);
        }
    }
}
