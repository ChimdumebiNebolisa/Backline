package dev.backline.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Worker runtime tuning: polling, retries, and HTTP client timeouts.
 */
@ConfigurationProperties(prefix = "backline.worker")
public class WorkerProperties {

    private String id = "worker-local";
    private long pollIntervalMs = 1000L;
    private int maxAttempts = 3;
    private long retryBackoffMs = 5000L;
    private long httpConnectTimeoutMs = 5000L;
    private long httpRequestTimeoutMs = 30000L;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public long getHttpConnectTimeoutMs() {
        return httpConnectTimeoutMs;
    }

    public void setHttpConnectTimeoutMs(long httpConnectTimeoutMs) {
        this.httpConnectTimeoutMs = httpConnectTimeoutMs;
    }

    public long getHttpRequestTimeoutMs() {
        return httpRequestTimeoutMs;
    }

    public void setHttpRequestTimeoutMs(long httpRequestTimeoutMs) {
        this.httpRequestTimeoutMs = httpRequestTimeoutMs;
    }
}
