package dev.backline.api.api;

import dev.backline.api.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSmokeTest extends PostgresTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void v3ApiDocsContainsGetRunById() {
        ResponseEntity<String> res = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("getRunById");
    }
}
