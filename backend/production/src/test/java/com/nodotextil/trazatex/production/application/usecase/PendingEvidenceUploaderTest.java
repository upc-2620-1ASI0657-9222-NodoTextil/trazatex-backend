package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.exception.StorageNotAvailableException;
import com.nodotextil.trazatex.production.application.exception.StoragePermanentException;
import com.nodotextil.trazatex.production.application.exception.StorageTransientException;
import com.nodotextil.trazatex.production.application.port.out.EvidenceRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.EvidenceStoragePort;
import com.nodotextil.trazatex.production.domain.model.Evidence;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.model.EvidenceRetryPolicy;
import com.nodotextil.trazatex.production.domain.model.EvidenceStatus;
import com.nodotextil.trazatex.production.domain.model.ImageFormat;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class PendingEvidenceUploaderTest {

    private static final Instant START = Instant.parse("2026-10-21T10:00:00Z");
    private static final EvidenceRetryPolicy POLICY = new EvidenceRetryPolicy(3, Duration.ofSeconds(30), 2.0, Duration.ofMinutes(10));

    private final MutableClock clock = new MutableClock(START);
    private final Deque<Evidence> queue = new ArrayDeque<>();
    private final List<Evidence> updates = new ArrayList<>();
    private final List<String> uploaded = new ArrayList<>();
    private Supplier<EvidenceStoragePort.UploadedImage> behavior = () -> new EvidenceStoragePort.UploadedImage("https://res.cloudinary.com/demo/ok.jpg");

    private final EvidenceRepositoryPort repository = new EvidenceRepositoryPort() {
        @Override
        public void saveAll(List<NewEvidence> evidences) {
        }

        @Override
        public List<Evidence> findByOwnerAndCompany(EvidenceOwnerType ownerType, UUID ownerId, UUID companyId) {
            return List.of();
        }

        @Override
        public Optional<PendingUpload> claimNextDue(Instant now, Instant leaseUntil) {
            Evidence next = queue.peekFirst();
            if (next == null || next.nextAttemptAt().isAfter(now)) {
                return Optional.empty();
            }
            queue.pollFirst();
            return Optional.of(new PendingUpload(next, new byte[]{1, 2, 3}));
        }

        @Override
        public void update(Evidence evidence) {
            updates.add(evidence);
        }
    };

    private final EvidenceStoragePort storage = (publicId, filename, contentType, content) -> {
        uploaded.add(publicId + "|" + filename + "|" + contentType);
        return behavior.get();
    };

    private final PendingEvidenceUploader uploader = new PendingEvidenceUploader(repository, storage,
            new EvidenceSettings(5, 10L << 20, 3, Duration.ofMinutes(5), Duration.ofMinutes(5), POLICY), clock, new RecordingTransactionManager());

    private Evidence pending() {
        Evidence evidence = Evidence.pending(UUID.randomUUID(), EvidenceOwnerType.BATCH, UUID.randomUUID(), UUID.randomUUID(), "a.jpg",
                ImageFormat.JPEG, 3, UUID.randomUUID(), START, Duration.ofSeconds(30), "timeout");
        queue.add(evidence);
        return evidence;
    }

    @Test
    void nothingIsAttemptedBeforeItsTime() {
        pending();

        assertThat(uploader.uploadDue()).isZero();
        assertThat(uploaded).isEmpty();
    }

    @Test
    void aSuccessfulUploadMarksItUploadedWithThePublicIdAndAFilenameWithItsExtension() {
        Evidence evidence = pending();
        clock.advance(Duration.ofSeconds(31));

        assertThat(uploader.uploadDue()).isEqualTo(1);

        assertThat(uploaded).containsExactly(evidence.id() + "|" + evidence.id() + ".jpg|image/jpeg");
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.UPLOADED);
        assertThat(evidence.url()).isEqualTo("https://res.cloudinary.com/demo/ok.jpg");
        assertThat(updates).containsExactly(evidence);
    }

    @Test
    void aTransientFailureCountsAnAttemptAndGrowsTheWaitUntilItFails() {
        Evidence evidence = pending();
        behavior = () -> {
            throw new StorageTransientException("HTTP 503", null);
        };
        Duration[] expectedWaits = {Duration.ofSeconds(30), Duration.ofSeconds(60)};

        for (int attempt = 1; attempt <= 2; attempt++) {
            clock.advance(Duration.ofMinutes(15));
            queue.clear();
            queue.add(evidence);
            uploader.uploadDue();
            assertThat(evidence.attempts()).isEqualTo(attempt);
            assertThat(evidence.nextAttemptAt()).isEqualTo(clock.instant().plus(expectedWaits[attempt - 1]));
            assertThat(evidence.status()).isEqualTo(EvidenceStatus.PENDING);
        }
        clock.advance(Duration.ofMinutes(15));
        queue.clear();
            queue.add(evidence);
        uploader.uploadDue();

        assertThat(evidence.status()).as("al agotar los 3 intentos").isEqualTo(EvidenceStatus.FAILED);
        assertThat(evidence.attempts()).isEqualTo(3);
    }

    @Test
    void aRejectedImageIsFailedImmediately() {
        Evidence evidence = pending();
        behavior = () -> {
            throw new StoragePermanentException("HTTP 400: Invalid image file");
        };
        clock.advance(Duration.ofMinutes(1));

        uploader.uploadDue();

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(evidence.attempts()).isEqualTo(1);
        assertThat(evidence.lastError()).contains("Invalid image file");
    }

    @Test
    void anUnavailableStorageNeverConsumesAttempts() {
        Evidence evidence = pending();
        behavior = () -> {
            throw new StorageNotAvailableException("el circuito de Cloudinary está abierto");
        };
        for (int i = 0; i < 50; i++) {
            clock.advance(Duration.ofMinutes(6));
            queue.clear();
            queue.add(evidence);
            uploader.uploadDue();
        }

        assertThat(evidence.attempts()).isZero();
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.PENDING);
        assertThat(evidence.nextAttemptAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
    }

    @Test
    void aSingleRunClaimsAtMostTheConfiguredNumberOfEvidences() {
        for (int i = 0; i < 5; i++) {
            pending();
        }
        clock.advance(Duration.ofMinutes(1));

        assertThat(uploader.uploadDue()).isEqualTo(3);
        assertThat(queue).hasSize(2);
    }

    @Test
    void anUnexpectedErrorIsTreatedAsTransientAndDoesNotStopTheRun() {
        Evidence first = pending();
        Evidence second = pending();
        int[] calls = {0};
        behavior = () -> {
            if (calls[0]++ == 0) {
                throw new IllegalStateException("algo inesperado");
            }
            return new EvidenceStoragePort.UploadedImage("https://res.cloudinary.com/demo/otra.jpg");
        };
        clock.advance(Duration.ofMinutes(1));

        assertThat(uploader.uploadDue()).isEqualTo(2);

        assertThat(first.attempts()).isEqualTo(1);
        assertThat(first.lastError()).contains("IllegalStateException");
        assertThat(second.status()).isEqualTo(EvidenceStatus.UPLOADED);
    }
}
