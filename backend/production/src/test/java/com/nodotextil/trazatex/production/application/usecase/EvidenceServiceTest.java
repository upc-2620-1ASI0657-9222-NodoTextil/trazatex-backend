package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.EvidenceItems;
import com.nodotextil.trazatex.production.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.production.application.exception.StorageNotAvailableException;
import com.nodotextil.trazatex.production.application.exception.StoragePermanentException;
import com.nodotextil.trazatex.production.application.exception.StorageTransientException;
import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.EvidenceRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.EvidenceRepositoryPort.NewEvidence;
import com.nodotextil.trazatex.production.application.port.out.EvidenceStoragePort;
import com.nodotextil.trazatex.production.application.port.out.TransformationRepositoryPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.exception.FileTooLargeException;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.Evidence;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.model.EvidenceRetryPolicy;
import com.nodotextil.trazatex.production.domain.model.EvidenceStatus;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.batch;
import static com.nodotextil.trazatex.production.domain.SampleImages.jpeg;
import static com.nodotextil.trazatex.production.domain.SampleImages.png;
import static com.nodotextil.trazatex.production.domain.SampleImages.webp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceServiceTest {

    private static final Instant START = Instant.parse("2026-10-21T10:00:00Z");
    private static final Actor OPERATOR = new Actor(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), COMPANY, Role.OPERATOR);

    private final BatchRepositoryPort batches = mock(BatchRepositoryPort.class);
    private final TransformationRepositoryPort transformations = mock(TransformationRepositoryPort.class);
    private final AuditLogPort audit = mock(AuditLogPort.class);
    private final List<NewEvidence> saved = new ArrayList<>();
    private final List<String> uploadedIds = new ArrayList<>();
    private Supplier<EvidenceStoragePort.UploadedImage> storageBehavior = () -> new EvidenceStoragePort.UploadedImage("https://res.cloudinary.com/demo/x.jpg");

    private final EvidenceRepositoryPort repository = new EvidenceRepositoryPort() {
        @Override
        public void saveAll(List<NewEvidence> evidences) {
            saved.addAll(evidences);
        }

        @Override
        public List<Evidence> findByOwnerAndCompany(EvidenceOwnerType ownerType, UUID ownerId, UUID companyId) {
            return saved.stream().map(NewEvidence::evidence)
                    .filter(e -> e.ownerType() == ownerType && e.ownerId().equals(ownerId) && e.companyId().equals(companyId)).toList();
        }

        @Override
        public Optional<PendingUpload> claimNextDue(Instant now, Instant leaseUntil) {
            return Optional.empty();
        }

        @Override
        public void update(Evidence evidence) {
        }
    };

    private final EvidenceStoragePort storage = (publicId, filename, contentType, content) -> {
        uploadedIds.add(publicId);
        return storageBehavior.get();
    };

    private final EvidenceService service = new EvidenceService(batches, transformations, repository, storage, audit,
            new EvidenceSettings(5, 10L * 1024 * 1024, 5, Duration.ofMinutes(5), Duration.ofMinutes(5), EvidenceRetryPolicy.DEFAULT),
            new MutableClock(START), new RecordingTransactionManager());

    private Batch ownBatch() {
        Batch batch = batch(BatchPhase.AVAILABLE, false, false, "10");
        when(batches.findById(batch.id())).thenReturn(Optional.of(batch));
        return batch;
    }

    private static EvidenceUpload file(String name, byte[] content) {
        return new EvidenceUpload(name, content);
    }

    @Test
    void everyFileIsUploadedWithItsOwnIdAsThePublicIdAndStoredAsUploaded() {
        Batch batch = ownBatch();

        EvidenceItems items = service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("a.jpg", jpeg()), file("b.png", png()), file("c.webp", webp())), OPERATOR);

        assertThat(items.items()).extracting(i -> i.status()).containsOnly(EvidenceStatus.UPLOADED);
        assertThat(items.items()).extracting(i -> i.contentType()).containsExactly("image/jpeg", "image/png", "image/webp");
        assertThat(uploadedIds).containsExactlyElementsOf(items.items().stream().map(i -> i.id().toString()).toList());
        assertThat(saved).allMatch(n -> n.queuedContent() == null);
        assertThat(items.items().get(0).url()).isEqualTo("https://res.cloudinary.com/demo/x.jpg");
        verify(audit).record(any(), any(), org.mockito.ArgumentMatchers.eq("EVIDENCE_ATTACHED"), any(), any(), any());
    }

    @Test
    void theFormatComesFromTheBytesNotFromTheNameOrTheDeclaredType() {
        Batch batch = ownBatch();

        EvidenceItems items = service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("foto.txt", png())), OPERATOR);

        assertThat(items.items().get(0).contentType()).isEqualTo("image/png");
        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, batch.id(),
                List.of(file("falsa.jpg", "no soy una imagen".getBytes(StandardCharsets.UTF_8))), OPERATOR))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.UNSUPPORTED_FILE_FORMAT);
                    assertThat(e.getMessage()).contains("JPEG, PNG y WebP");
                    assertThat(e.details()).containsExactly("falsa.jpg");
                });
    }

    @Test
    void itIsAllOrNothingBeforeAnythingIsUploaded() {
        Batch batch = ownBatch();

        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, batch.id(),
                List.of(file("ok.jpg", jpeg()), file("mala.gif", "GIF89a".getBytes(StandardCharsets.UTF_8))), OPERATOR))
                .isInstanceOf(ValidationException.class);

        assertThat(uploadedIds).isEmpty();
        assertThat(saved).isEmpty();
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void thereMustBeBetweenOneAndFiveNonEmptyFilesNoneTooLarge() {
        Batch batch = ownBatch();
        List<EvidenceUpload> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(file("f" + i + ".jpg", jpeg()));
        }

        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(), OPERATOR))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.NO_FILES));
        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("vacio.jpg", new byte[0])), OPERATOR))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.NO_FILES));
        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, batch.id(), six, OPERATOR))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.TOO_MANY_FILES));
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(jpeg(), 0, big, 0, 4);
        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("enorme.jpg", big)), OPERATOR))
                .isInstanceOf(FileTooLargeException.class);
        assertThat(service.attach(EvidenceOwnerType.BATCH, batch.id(), six.subList(0, 5), OPERATOR).items()).hasSize(5);
    }

    @Test
    void whenTheStorageFailsTheEvidenceIsPendingAndTheRestOfTheRequestGoesStraightToTheQueue() {
        Batch batch = ownBatch();
        storageBehavior = () -> {
            throw new StorageTransientException("timeout", null);
        };

        EvidenceItems items = service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("a.jpg", jpeg()), file("b.jpg", jpeg()), file("c.png", png())), OPERATOR);

        assertThat(items.items()).extracting(i -> i.status()).containsOnly(EvidenceStatus.PENDING);
        assertThat(items.items()).allMatch(i -> i.url() == null && i.uploadedAt() == null);
        assertThat(uploadedIds).as("tras el primer fallo no se vuelve a intentar en esta petición").hasSize(1);
        assertThat(saved).allMatch(n -> n.queuedContent() != null && n.queuedContent().length > 0);
        assertThat(saved.get(0).evidence().lastError()).isEqualTo("timeout");
        assertThat(saved.get(0).evidence().nextAttemptAt()).isEqualTo(START.plusSeconds(30));
    }

    @Test
    void anUnconfiguredOrOpenStorageQueuesEverythingWithoutAnError() {
        Batch batch = ownBatch();
        storageBehavior = () -> {
            throw new StorageNotAvailableException("Cloudinary no está configurado");
        };

        EvidenceItems items = service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("a.jpg", jpeg())), OPERATOR);

        assertThat(items.items().get(0).status()).isEqualTo(EvidenceStatus.PENDING);
    }

    @Test
    void ifTheStorageRejectsTheImageNothingIsStoredAndItIsAFormatError() {
        Batch batch = ownBatch();
        storageBehavior = () -> {
            throw new StoragePermanentException("HTTP 400: Invalid image file");
        };

        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("rota.jpg", jpeg())), OPERATOR))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.UNSUPPORTED_FILE_FORMAT);
                    assertThat(e.getMessage()).contains("JPEG, PNG y WebP");
                });
        assertThat(saved).isEmpty();
    }

    @Test
    void aBatchOrTransformationOfAnotherCompanyOrUnknownIsNotFoundAndNothingIsUploaded() {
        Batch batch = ownBatch();
        Actor outsider = new Actor(UUID.randomUUID(), OTHER_COMPANY, Role.OPERATOR);
        when(transformations.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("a.jpg", jpeg())), outsider))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.BATCH_NOT_FOUND));
        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.BATCH, UUID.randomUUID(), List.of(file("a.jpg", jpeg())), OPERATOR))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.attach(EvidenceOwnerType.TRANSFORMATION, UUID.randomUUID(), List.of(file("a.jpg", jpeg())), OPERATOR))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.TRANSFORMATION_NOT_FOUND));
        assertThat(uploadedIds).isEmpty();
    }

    @Test
    void eachCompanyListsOnlyTheEvidenceItUploaded() {
        Batch batch = ownBatch();
        service.attach(EvidenceOwnerType.BATCH, batch.id(), List.of(file("a.jpg", jpeg()), file("b.png", png())), OPERATOR);

        assertThat(service.list(EvidenceOwnerType.BATCH, batch.id(), OPERATOR).items()).hasSize(2);
        // una evidencia que subió otra empresa (p. ej. antes de una transferencia) no se lista
        saved.add(new NewEvidence(Evidence.uploaded(UUID.randomUUID(), EvidenceOwnerType.BATCH, batch.id(), OTHER_COMPANY, "ajena.jpg",
                com.nodotextil.trazatex.production.domain.model.ImageFormat.JPEG, 10, UUID.randomUUID(), "https://res.cloudinary.com/demo/ajena.jpg", START), null));
        assertThat(service.list(EvidenceOwnerType.BATCH, batch.id(), OPERATOR).items()).extracting(i -> i.originalFilename()).containsExactly("a.jpg", "b.png");
    }

    @Test
    void theNameIsOnlyForDisplay() {
        assertThat(EvidenceService.sanitize("..\\..\\etc/pasaporte.jpg")).isEqualTo("pasaporte.jpg");
        assertThat(EvidenceService.sanitize("C:\\fotos\\lote 1.jpg")).isEqualTo("lote 1.jpg");
        assertThat(EvidenceService.sanitize(null)).isEqualTo("foto");
        assertThat(EvidenceService.sanitize("   ")).isEqualTo("foto");
        assertThat(EvidenceService.sanitize("a\u0000b\nc.jpg")).isEqualTo("abc.jpg");
        assertThat(EvidenceService.sanitize("x".repeat(400))).hasSize(255);
    }
}
