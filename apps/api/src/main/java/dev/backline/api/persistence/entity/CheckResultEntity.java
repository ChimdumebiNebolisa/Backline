package dev.backline.api.persistence.entity;

import dev.backline.core.check.CheckResultStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Outcome of executing one check within a single run (immutable after finalization at service layer). */
@Entity
@Table(name = "check_results")
public class CheckResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "check_id")
    private UUID checkId;

    @Column(name = "check_key", nullable = false, length = 120)
    private String checkKey;

    @Column(name = "check_name", nullable = false, length = 200)
    private String checkName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckResultStatus status;

    @Column(name = "actual_status")
    private Integer actualStatus;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "response_preview", columnDefinition = "text")
    private String responsePreview;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assertions_json", columnDefinition = "jsonb")
    private String assertionsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_contract_json", columnDefinition = "jsonb")
    private String responseContractJson;

    @Column(name = "response_contract_hash", length = 64)
    private String responseContractHash;

    @Column(name = "response_contract_status", length = 20)
    private String responseContractStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CheckResultEntity() {}

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public void setCheckId(UUID checkId) {
        this.checkId = checkId;
    }

    public String getCheckKey() {
        return checkKey;
    }

    public void setCheckKey(String checkKey) {
        this.checkKey = checkKey;
    }

    public String getCheckName() {
        return checkName;
    }

    public void setCheckName(String checkName) {
        this.checkName = checkName;
    }

    public CheckResultStatus getStatus() {
        return status;
    }

    public void setStatus(CheckResultStatus status) {
        this.status = status;
    }

    public Integer getActualStatus() {
        return actualStatus;
    }

    public void setActualStatus(Integer actualStatus) {
        this.actualStatus = actualStatus;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResponsePreview() {
        return responsePreview;
    }

    public void setResponsePreview(String responsePreview) {
        this.responsePreview = responsePreview;
    }

    public String getAssertionsJson() {
        return assertionsJson;
    }

    public void setAssertionsJson(String assertionsJson) {
        this.assertionsJson = assertionsJson;
    }

    public String getResponseContractJson() {
        return responseContractJson;
    }

    public void setResponseContractJson(String responseContractJson) {
        this.responseContractJson = responseContractJson;
    }

    public String getResponseContractHash() {
        return responseContractHash;
    }

    public void setResponseContractHash(String responseContractHash) {
        this.responseContractHash = responseContractHash;
    }

    public String getResponseContractStatus() {
        return responseContractStatus;
    }

    public void setResponseContractStatus(String responseContractStatus) {
        this.responseContractStatus = responseContractStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
