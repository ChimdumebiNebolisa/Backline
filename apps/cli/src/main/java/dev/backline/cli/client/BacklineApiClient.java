package dev.backline.cli.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.backline.core.api.DataResponse;
import dev.backline.core.api.ErrorResponse;
import dev.backline.core.api.ListResponse;
import dev.backline.core.api.dto.CheckDto;
import dev.backline.core.api.dto.CheckResultDto;
import dev.backline.core.api.dto.CheckSyncRequest;
import dev.backline.core.api.dto.CreateProjectRequest;
import dev.backline.core.api.dto.CreateRunRequest;
import dev.backline.core.api.dto.DiffBaselineStrategy;
import dev.backline.core.api.dto.ProjectDto;
import dev.backline.core.api.dto.ProjectSummaryDto;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDto;
import dev.backline.core.error.ErrorCode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP JSON client for the Backline API using {@link java.net.http.HttpClient}. API payloads use camelCase
 * Jackson defaults (no snake_case). Connect timeout 5s; request timeout 30s.
 */
public final class BacklineApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public BacklineApiClient(String baseUrl, ObjectMapper mapper, HttpClient http) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.mapper = mapper;
        this.http = http;
    }

    public BacklineApiClient(String baseUrl) {
        this(baseUrl, defaultMapper(), defaultHttpClient());
    }

    public BacklineApiClient() {
        this("http://localhost:8080");
    }

    private static ObjectMapper defaultMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        String t = url.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    public ProjectDto createProject(CreateProjectRequest request) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 409) {
            ErrorResponse err = parseError(response);
            throw new ApiClientException(
                    409, ErrorCode.CONFLICT, err.error().message(), err.error().field());
        }
        if (response.statusCode() != 201) {
            throw clientException(response);
        }
        return mapper.readValue(response.body(), new TypeReference<DataResponse<ProjectDto>>() {}).data();
    }

    public List<CheckDto> syncChecks(CheckSyncRequest request) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/checks/sync"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw clientException(response);
        }
        return mapper.readValue(response.body(), new TypeReference<DataResponse<List<CheckDto>>>() {}).data();
    }

    public RunDto submitRun(CreateRunRequest request) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/runs"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw clientException(response);
        }
        return mapper.readValue(response.body(), new TypeReference<DataResponse<RunDto>>() {}).data();
    }

    public RunDto getRun(UUID id) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/runs/" + id))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw clientException(response);
        }
        return mapper.readValue(response.body(), new TypeReference<DataResponse<RunDto>>() {}).data();
    }

    public List<CheckResultDto> getRunResults(UUID id) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/runs/" + id + "/results"))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw clientException(response);
        }
        return mapper.readValue(response.body(), new TypeReference<DataResponse<List<CheckResultDto>>>() {})
                .data();
    }

    public RunDiffDto getRunDiff(UUID id) throws IOException, InterruptedException {
        return getRunDiff(id, DiffBaselineStrategy.PREVIOUS_COMPLETED, null);
    }

    public RunDiffDto getRunDiff(UUID id, DiffBaselineStrategy baseline, UUID fixedRunId)
            throws IOException, InterruptedException {
        StringBuilder uri = new StringBuilder(baseUrl)
                .append("/api/runs/")
                .append(id)
                .append("/diff?baseline=")
                .append(urlEncode((baseline == null ? DiffBaselineStrategy.PREVIOUS_COMPLETED : baseline).name()));
        if (fixedRunId != null) {
            uri.append("&fixedRunId=").append(urlEncode(fixedRunId.toString()));
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(uri.toString()))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw clientException(response);
        }
        return mapper.readValue(response.body(), new TypeReference<DataResponse<RunDiffDto>>() {}).data();
    }

    public ProjectSummaryDto getProjectSummary(UUID projectId) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + projectId + "/summary"))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw clientException(response);
        }
        return mapper.readValue(response.body(), new TypeReference<DataResponse<ProjectSummaryDto>>() {})
                .data();
    }

    public List<RunDto> listRuns(RunListQuery query) throws IOException, InterruptedException {
        StringBuilder uri = new StringBuilder(baseUrl).append("/api/runs");
        char sep = '?';
        if (query.projectSlug() != null && !query.projectSlug().isBlank()) {
            uri.append(sep).append("projectSlug=").append(urlEncode(query.projectSlug()));
            sep = '&';
        }
        if (query.environment() != null && !query.environment().isBlank()) {
            uri.append(sep).append("environment=").append(urlEncode(query.environment()));
            sep = '&';
        }
        if (query.status() != null && !query.status().isBlank()) {
            uri.append(sep).append("status=").append(urlEncode(query.status()));
            sep = '&';
        }
        if (query.limit() != null) {
            uri.append(sep).append("limit=").append(query.limit());
            sep = '&';
        }
        if (query.offset() != null) {
            uri.append(sep).append("offset=").append(query.offset());
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(uri.toString()))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw clientException(response);
        }
        return mapper.readValue(response.body(), new TypeReference<ListResponse<RunDto>>() {}).data();
    }

    public Map<String, Object> getApiHealth() throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/health"))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw clientException(response);
        }
        JsonNode root = mapper.readTree(response.body());
        if (root.has("data")) {
            return mapper.convertValue(root.get("data"), new TypeReference<Map<String, Object>>() {});
        }
        return mapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private ApiClientException clientException(HttpResponse<String> response) {
        try {
            ErrorResponse err = parseError(response);
            ErrorCode code = parseCode(err.error().code());
            return new ApiClientException(response.statusCode(), code, err.error().message(), err.error().field());
        } catch (Exception e) {
            String body = response.body() == null ? "" : response.body();
            return new ApiClientException(
                    response.statusCode(),
                    ErrorCode.BAD_REQUEST,
                    "HTTP " + response.statusCode() + ": " + body,
                    null,
                    e);
        }
    }

    private ErrorResponse parseError(HttpResponse<String> response) throws IOException {
        return mapper.readValue(response.body(), ErrorResponse.class);
    }

    private static ErrorCode parseCode(String code) {
        if (code == null) {
            return ErrorCode.BAD_REQUEST;
        }
        try {
            return ErrorCode.valueOf(code);
        } catch (IllegalArgumentException ex) {
            return ErrorCode.BAD_REQUEST;
        }
    }
}
