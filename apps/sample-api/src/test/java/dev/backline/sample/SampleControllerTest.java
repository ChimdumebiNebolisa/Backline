package dev.backline.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SampleControllerTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void health_returnsUpPayload() {
        ResponseEntity<Map<String, Object>> r =
                restTemplate.exchange("/health", HttpMethod.GET, null, MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("status", "UP").containsEntry("service", "sample-api");
    }

    @Test
    void getUser_returnsSampleUser() {
        ResponseEntity<Map<String, Object>> r =
                restTemplate.exchange("/users/1", HttpMethod.GET, null, MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody())
                .containsEntry("id", 1)
                .containsEntry("email", "user1@example.com")
                .containsEntry("name", "Sample User");
    }

    @Test
    void postUsers_mergesId201() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"name\":\"Created\"}", headers);
        ResponseEntity<Map<String, Object>> r =
                restTemplate.exchange("/users", HttpMethod.POST, entity, MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).containsEntry("id", 2).containsEntry("name", "Created");
    }

    @Test
    void slow_returns200() {
        ResponseEntity<Map<String, Object>> r =
                restTemplate.exchange("/slow", HttpMethod.GET, null, MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("status");
    }

    @Test
    void broken_returns500() {
        ResponseEntity<Map<String, Object>> r =
                restTemplate.exchange("/broken", HttpMethod.GET, null, MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).containsEntry("error", "intentional failure");
    }

    @Test
    void schemaChange_returnsRenamedField() {
        ResponseEntity<Map<String, Object>> r =
                restTemplate.exchange("/schema-change", HttpMethod.GET, null, MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("id", 1).containsEntry("renamedField", "value");
    }
}
