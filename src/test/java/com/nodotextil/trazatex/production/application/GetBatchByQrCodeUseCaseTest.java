package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.MaterialType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetBatchByQrCodeUseCaseTest {

    private InMemoryBatchRepository repository;
    private GetBatchByQrCodeUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBatchRepository();
        useCase = new GetBatchByQrCodeUseCase(repository);
    }

    @Test
    void returnsBatchForQrCode() {
        Batch batch = registerBatch();

        Batch result = useCase.execute(batch.qrCode());

        assertThat(result).isEqualTo(batch);
    }

    @Test
    void rejectsUnknownQrCode() {
        String unknownQrCode = "QR-unknown";

        assertThatThrownBy(() -> useCase.execute(unknownQrCode))
                .isInstanceOf(BatchNotFoundException.class)
                .hasMessageContaining(unknownQrCode);
    }

    private Batch registerBatch() {
        RegisterBatchUseCase registerUseCase = new RegisterBatchUseCase(
                repository,
                Clock.fixed(Instant.parse("2026-05-01T10:00:00Z"), ZoneOffset.UTC));
        return registerUseCase.execute(new RegisterBatchUseCase.Command(
                UUID.fromString("ed67c7ba-9ec1-4c20-8e8c-4fe42eefaa42"),
                "Supplier",
                "Lima, Peru",
                MaterialType.FIBER,
                new BigDecimal("25"),
                List.of(new CompositionComponent("Cotton", new BigDecimal("100"))),
                "Accepted"));
    }
}
