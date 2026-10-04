package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.InvalidProductionEvidenceException;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.ProductionEvidence;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceRepository;
import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.production.domain.TransformationRepository;
import com.nodotextil.trazatex.production.domain.TransformationType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionEvidenceServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-04T18:30:00Z"), ZoneOffset.UTC);
    private static final UUID UPLOADER =
            UUID.fromString("91a97639-8e92-49ad-820a-fbe29bf93e89");
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
    };

    private InMemoryBatchRepository batchRepository;
    private InMemoryTransformationRepository transformationRepository;
    private InMemoryProductionEvidenceRepository evidenceRepository;
    private FakeProductionImageStorage imageStorage;
    private ProductionEvidenceService service;

    @BeforeEach
    void setUp() {
        batchRepository = new InMemoryBatchRepository();
        transformationRepository = new InMemoryTransformationRepository();
        evidenceRepository = new InMemoryProductionEvidenceRepository();
        imageStorage = new FakeProductionImageStorage();
        service = new ProductionEvidenceService(
                evidenceRepository,
                imageStorage,
                batchRepository,
                transformationRepository,
                FIXED_CLOCK);
    }

    @Test
    void uploadsAndPersistsBatchEvidenceThroughStoragePort() {
        Batch batch = saveBatch();

        ProductionEvidence evidence = service.upload(
                ProductionEvidenceOwnerType.BATCH,
                batch.id(),
                UPLOADER,
                PNG,
                "batch.png",
                "image/png");

        assertThat(evidence.ownerType()).isEqualTo(ProductionEvidenceOwnerType.BATCH);
        assertThat(evidence.ownerId()).isEqualTo(batch.id());
        assertThat(evidence.uploadedByUserId()).isEqualTo(UPLOADER);
        assertThat(evidence.uploadedAt())
                .isEqualTo(LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC));
        assertThat(evidence.url()).isEqualTo("https://images.example/" + batch.id());
        assertThat(evidence.publicId()).contains(batch.id().toString());
        assertThat(imageStorage.requests()).singleElement().satisfies(request -> {
            assertThat(request.ownerType()).isEqualTo(ProductionEvidenceOwnerType.BATCH);
            assertThat(request.ownerId()).isEqualTo(batch.id());
            assertThat(request.contentType()).isEqualTo("image/png");
        });
    }

    @Test
    void uploadsAndListsTransformationEvidence() {
        Transformation transformation = saveTransformation();
        ProductionEvidence uploaded = service.upload(
                ProductionEvidenceOwnerType.TRANSFORMATION,
                transformation.id(),
                UPLOADER,
                PNG,
                "transformation.png",
                "image/png");

        List<ProductionEvidence> evidence = service.list(
                ProductionEvidenceOwnerType.TRANSFORMATION,
                transformation.id());

        assertThat(evidence).containsExactly(uploaded);
        assertThat(uploaded.ownerType())
                .isEqualTo(ProductionEvidenceOwnerType.TRANSFORMATION);
    }

    @Test
    void rejectsEvidenceWhenOwnerDoesNotExist() {
        UUID missingBatchId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(
                ProductionEvidenceOwnerType.BATCH,
                missingBatchId,
                UPLOADER,
                PNG,
                "missing.png",
                "image/png"))
                .isInstanceOf(BatchNotFoundException.class);
        assertThat(imageStorage.requests()).isEmpty();
        assertThat(evidenceRepository.evidence).isEmpty();
    }

    @Test
    void rejectsUnsupportedOrSpoofedImageWithoutCallingStorage() {
        Batch batch = saveBatch();

        assertThatThrownBy(() -> service.upload(
                ProductionEvidenceOwnerType.BATCH,
                batch.id(),
                UPLOADER,
                new byte[] {0x01, 0x02, 0x03},
                "not-an-image.png",
                "image/png"))
                .isInstanceOf(InvalidProductionEvidenceException.class)
                .hasMessageContaining("supported");
        assertThat(imageStorage.requests()).isEmpty();
        assertThat(evidenceRepository.evidence).isEmpty();
    }

    private Batch saveBatch() {
        return batchRepository.save(Batch.register(
                UUID.randomUUID(),
                "TRZ-EVIDENCE",
                "QR-EVIDENCE",
                UUID.randomUUID(),
                "Supplier",
                "Cusco, Peru",
                MaterialType.FIBER,
                new BigDecimal("12"),
                List.of(new CompositionComponent("Alpaca", new BigDecimal("100"))),
                LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC),
                "Dry"));
    }

    private Transformation saveTransformation() {
        return transformationRepository.save(Transformation.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UPLOADER,
                UUID.randomUUID(),
                TransformationType.SPINNING,
                List.of(UUID.randomUUID()),
                LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC),
                new BigDecimal("12")));
    }

    private static final class InMemoryProductionEvidenceRepository
            implements ProductionEvidenceRepository {

        private final List<ProductionEvidence> evidence = new ArrayList<>();

        @Override
        public ProductionEvidence save(ProductionEvidence item) {
            evidence.add(item);
            return item;
        }

        @Override
        public List<ProductionEvidence> findByOwner(
                ProductionEvidenceOwnerType ownerType,
                UUID ownerId) {
            return evidence.stream()
                    .filter(item -> item.ownerType() == ownerType
                            && item.ownerId().equals(ownerId))
                    .toList();
        }
    }

    private static final class InMemoryTransformationRepository
            implements TransformationRepository {

        private final Map<UUID, Transformation> transformations = new HashMap<>();

        @Override
        public Transformation save(Transformation transformation) {
            transformations.put(transformation.id(), transformation);
            return transformation;
        }

        @Override
        public Optional<Transformation> findById(UUID id) {
            return Optional.ofNullable(transformations.get(id));
        }
    }
}
