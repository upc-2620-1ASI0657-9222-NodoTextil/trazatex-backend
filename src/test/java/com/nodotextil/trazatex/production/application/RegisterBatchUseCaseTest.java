package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.InvalidBatchException;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterBatchUseCaseTest {

    private static final Instant REGISTRATION_INSTANT = Instant.parse("2026-01-15T10:30:00Z");

    private InMemoryBatchRepository repository;
    private RegisterBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBatchRepository();
        useCase = new RegisterBatchUseCase(
                repository,
                Clock.fixed(REGISTRATION_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void registersAvailableBatchWithGeneratedIdentifiers() {
        Batch batch = useCase.execute(validCommand(
                new BigDecimal("125.50"),
                List.of(
                        new CompositionComponent("Cotton", new BigDecimal("60")),
                        new CompositionComponent("Polyester", new BigDecimal("40")))));

        assertThat(batch.id()).isNotNull();
        assertThat(batch.traceabilityId()).startsWith("TRZ-");
        assertThat(batch.qrCode()).startsWith("QR-");
        assertThat(batch.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
        assertThat(batch.registeredAt())
                .isEqualTo(LocalDateTime.ofInstant(REGISTRATION_INSTANT, ZoneOffset.UTC));
        assertThat(repository.findById(batch.id())).contains(batch);
    }

    @Test
    void rejectsCompositionThatDoesNotAddUpToOneHundred() {
        RegisterBatchUseCase.Command command = validCommand(
                new BigDecimal("25"),
                List.of(
                        new CompositionComponent("Cotton", new BigDecimal("70")),
                        new CompositionComponent("Polyester", new BigDecimal("20"))));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("exactly 100");
        assertThat(repository.size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void rejectsNonPositiveQuantity(String quantity) {
        RegisterBatchUseCase.Command command = validCommand(
                new BigDecimal(quantity),
                List.of(new CompositionComponent("Cotton", new BigDecimal("100"))));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("greater than zero");
        assertThat(repository.size()).isZero();
    }

    private RegisterBatchUseCase.Command validCommand(
            BigDecimal quantity,
            List<CompositionComponent> composition) {
        return new RegisterBatchUseCase.Command(
                UUID.fromString("85702952-5ef0-49a6-af27-32d867e07e19"),
                null,
                "Piura, Peru",
                MaterialType.FIBER,
                quantity,
                composition,
                "Clean and dry on reception");
    }
}
