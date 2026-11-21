package com.nodotextil.trazatex.production.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Evidence {

    private final UUID id;
    private final EvidenceOwnerType ownerType;
    private final UUID ownerId;
    private final UUID companyId;
    private EvidenceStatus status;
    private String url;
    private final String originalFilename;
    private final String contentType;
    private final long sizeBytes;
    private final UUID uploadedBy;
    private final Instant createdAt;
    private Instant uploadedAt;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;

    private Evidence(UUID id, EvidenceOwnerType ownerType, UUID ownerId, UUID companyId, EvidenceStatus status, String url,
                     String originalFilename, String contentType, long sizeBytes, UUID uploadedBy, Instant createdAt,
                     Instant uploadedAt, int attempts, Instant nextAttemptAt, String lastError) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        this.status = Objects.requireNonNull(status, "status");
        this.url = url;
        this.originalFilename = originalFilename;
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.sizeBytes = sizeBytes;
        this.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.uploadedAt = uploadedAt;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
    }

    public static Evidence uploaded(UUID id, EvidenceOwnerType ownerType, UUID ownerId, UUID companyId, String originalFilename,
                                    ImageFormat format, long sizeBytes, UUID uploadedBy, String url, Instant now) {
        return new Evidence(id, ownerType, ownerId, companyId, EvidenceStatus.UPLOADED, Objects.requireNonNull(url, "url"),
                originalFilename, format.contentType(), sizeBytes, uploadedBy, now, now, 0, null, null);
    }

    public static Evidence pending(UUID id, EvidenceOwnerType ownerType, UUID ownerId, UUID companyId, String originalFilename,
                                   ImageFormat format, long sizeBytes, UUID uploadedBy, Instant now, Duration firstDelay,
                                   String lastError) {
        return new Evidence(id, ownerType, ownerId, companyId, EvidenceStatus.PENDING, null, originalFilename, format.contentType(),
                sizeBytes, uploadedBy, now, null, 0, now.plus(firstDelay), lastError);
    }

    public static Evidence restore(UUID id, EvidenceOwnerType ownerType, UUID ownerId, UUID companyId, EvidenceStatus status,
                                   String url, String originalFilename, String contentType, long sizeBytes, UUID uploadedBy,
                                   Instant createdAt, Instant uploadedAt, int attempts, Instant nextAttemptAt, String lastError) {
        return new Evidence(id, ownerType, ownerId, companyId, status, url, originalFilename, contentType, sizeBytes, uploadedBy,
                createdAt, uploadedAt, attempts, nextAttemptAt, lastError);
    }

    public void markUploaded(String url, Instant now) {
        this.status = EvidenceStatus.UPLOADED;
        this.url = Objects.requireNonNull(url, "url");
        this.uploadedAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void recordTransientFailure(String error, Instant now, EvidenceRetryPolicy policy) {
        this.attempts++;
        this.lastError = error;
        if (attempts >= policy.maxAttempts()) {
            this.status = EvidenceStatus.FAILED;
            this.nextAttemptAt = null;
        } else {
            this.nextAttemptAt = now.plus(policy.delayAfter(attempts));
        }
    }

    public void recordPermanentFailure(String error) {
        this.attempts++;
        this.status = EvidenceStatus.FAILED;
        this.lastError = error;
        this.nextAttemptAt = null;
    }

    public void deferWithoutAttempt(String reason, Instant until) {
        this.lastError = reason;
        this.nextAttemptAt = until;
    }

    public UUID id() {
        return id;
    }

    public EvidenceOwnerType ownerType() {
        return ownerType;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID companyId() {
        return companyId;
    }

    public EvidenceStatus status() {
        return status;
    }

    public String url() {
        return url;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public UUID uploadedBy() {
        return uploadedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant uploadedAt() {
        return uploadedAt;
    }

    public int attempts() {
        return attempts;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public String lastError() {
        return lastError;
    }
}
