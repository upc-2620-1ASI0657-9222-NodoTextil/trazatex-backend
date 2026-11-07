package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.dto.AffectedBatch;
import com.nodotextil.trazatex.quality.application.port.in.RecordDerivedFailureUseCase;
import com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * Las cuatro fuentes de comandos pendientes (inicio, finalización, descarte y retención) son independientes: si una falla, o Production no se puede
 * llamar (circuito abierto), las otras tres se siguen atendiendo.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false",
        "trazatex.production.read-timeout=PT0.5S", "trazatex.production.connect-timeout=PT0.5S",
        "trazatex.production.retry.wait-duration=PT0.01S"})
@ExtendWith(OutputCaptureExtension.class)
class PendingCommandIsolationIT extends FlowTestBase {

    @Autowired
    RecordDerivedFailureUseCase derived;
    @MockitoSpyBean
    QualityControlRepositoryPort controlsSpy;
    @MockitoSpyBean
    QualityFailureRepositoryPort failuresSpy;
    @MockitoSpyBean
    ImpactedBatchRepositoryPort noticesSpy;

    /** Una pieza pendiente de cada fuente, todas vencidas dentro de la gracia. */
    record Pending(UUID startBatch, UUID startControl, UUID finishBatch, UUID finishControl, UUID discardBatch, UUID discardFailure, UUID holdBatch, UUID holdFailure) {
    }

    Pending onePendingOfEach() throws Exception {
        // Inicio pendiente: Production no responde al crearlo
        UUID startBatch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        UUID startControl = UUID.fromString(read(postAs(ADMIN_OF_A, "/api/quality/batches/" + startBatch + "/controls").andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted())).path("id").asString());
        // Finalización pendiente
        UUID finishBatch = seedAvailable();
        UUID finishControl = startControl(finishBatch);
        addTest(finishControl, "CONFORMING");
        productionBreaker.reset();
        ProductionStub.reset();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        postAs(ADMIN_OF_A, "/api/quality/controls/" + finishControl + "/finish").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
        // Descarte pendiente
        UUID discardBatch = seedAvailable();
        UUID failedControl = startControl(discardBatch);
        addTest(failedControl, "FAILED");
        productionBreaker.reset();
        ProductionStub.reset();
        ProductionStub.answersOk();
        postAs(ADMIN_OF_A, "/api/quality/controls/" + failedControl + "/finish").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        UUID discardFailure = failureOfBatch(discardBatch);
        putAs(ADMIN_OF_A, "/api/quality/failures/" + discardFailure, Map.of("rootCause", "Humedad", "affectedQtyKg", 5)).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        productionBreaker.reset();
        ProductionStub.reset();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        postAs(ADMIN_OF_A, "/api/quality/failures/" + discardFailure + "/discard").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
        // Retención pendiente
        UUID holdBatch = seedAvailable();
        UUID holdFailure = UUID.randomUUID();
        derived.record(holdFailure, UUID.randomUUID(), List.of(new AffectedBatch(holdBatch, COMPANY_A, UUID.randomUUID())));
        ProductionStub.reset();
        productionBreaker.reset();
        return new Pending(startBatch, startControl, finishBatch, finishControl, discardBatch, discardFailure, holdBatch, holdFailure);
    }

    void makeEverythingDue() {
        clock.advance(Duration.ofSeconds(60));
    }

    boolean resolved(Pending p, PendingSource source) {
        return switch (source) {
            case START -> control(p.startControl(), "status", String.class).equals("IN_PROGRESS");
            case FINISH -> control(p.finishControl(), "status", String.class).equals("FINISHED");
            case DISCARD -> failure(p.discardFailure(), "status", String.class).equals("CONFIRMED");
            case HOLD -> notice(p.holdFailure(), p.holdBatch(), "lock_status", String.class).equals("APPLIED");
        };
    }

    enum PendingSource { START, FINISH, DISCARD, HOLD }

    @ParameterizedTest
    @EnumSource(PendingSource.class)
    void ifOneSourceFailsTheOtherThreeAreStillServedAndTheFailedOneIsRetriedOnTheNextPass(PendingSource failing, CapturedOutput output) throws Exception {
        Pending pending = onePendingOfEach();
        makeEverythingDue();
        ProductionStub.answersOk();
        switch (failing) {
            case START -> Mockito.doThrow(new IllegalStateException("la base no respondió")).doCallRealMethod().when(controlsSpy).claimPendingStarts(any(), anyInt(), any());
            case FINISH -> Mockito.doThrow(new IllegalStateException("la base no respondió")).doCallRealMethod().when(controlsSpy).claimPendingFinishes(any(), anyInt(), any());
            case DISCARD -> Mockito.doThrow(new IllegalStateException("la base no respondió")).doCallRealMethod().when(failuresSpy).claimPendingDiscards(any(), anyInt(), any());
            case HOLD -> Mockito.doThrow(new IllegalStateException("la base no respondió")).doCallRealMethod().when(noticesSpy).claimPending(any(), anyInt(), any());
        }

        int claimed = recovery.recoverAll();

        assertThat(claimed).as("solo las tres que no fallaron").isEqualTo(3);
        for (PendingSource source : PendingSource.values()) {
            assertThat(resolved(pending, source)).as("%s con %s fallando", source, failing).isEqualTo(source != failing);
        }
        assertThat(output.getAll()).contains("Falló la fuente " + failing);

        assertThat(recovery.recoverAll()).as("en la pasada siguiente la que falló se atiende").isEqualTo(1);
        for (PendingSource source : PendingSource.values()) {
            assertThat(resolved(pending, source)).as("%s tras la segunda pasada", source).isTrue();
        }
    }

    @Test
    void withTheCircuitOpenEverySourceReschedulesWithoutCountingAnAttemptAndWhatNeedsNoProductionStillGetsDone() throws Exception {
        Pending pending = onePendingOfEach();
        // Un aviso más que no necesita a Production: el lote es histórico
        UUID historicBatch = seed(BatchPhase.AVAILABLE, QualityStatus.NOT_REVIEWED, false, false);
        UUID historicFailure = UUID.randomUUID();
        derived.record(historicFailure, UUID.randomUUID(), List.of(new AffectedBatch(historicBatch, COMPANY_A, UUID.randomUUID())));
        projections.update(com.nodotextil.trazatex.quality.domain.model.BatchProjection.restore(historicBatch, COMPANY_A, BatchPhase.PROCESSED, false,
                QualityStatus.NOT_REVIEWED, false, 5, START));
        jdbc.sql("UPDATE impacted_batch SET next_attempt_at = :now WHERE batch_id = :b").param("b", historicBatch)
                .param("now", OffsetDateTime.ofInstant(START.plusSeconds(60), java.time.ZoneOffset.UTC)).update();
        makeEverythingDue();
        ProductionStub.answersOk();
        productionBreaker.transitionToOpenState();
        int attemptsBefore = control(pending.startControl(), "attempts", Integer.class);

        int claimed = recovery.recoverAll();

        assertThat(claimed).isEqualTo(5);
        assertThat(ProductionStub.allRequests()).as("con el circuito abierto no sale nada").isEmpty();
        assertThat(control(pending.startControl(), "status", String.class)).isEqualTo("STARTING");
        assertThat(control(pending.startControl(), "attempts", Integer.class)).as("no se llamó: no cuenta como intento").isEqualTo(attemptsBefore);
        assertThat(control(pending.finishControl(), "status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(failure(pending.discardFailure(), "status", String.class)).isEqualTo("OPEN");
        assertThat(notice(pending.holdFailure(), pending.holdBatch(), "lock_status", String.class)).isEqualTo("PENDING");
        assertThat(notice(historicFailure, historicBatch, "lock_status", String.class)).as("lo que no necesita a Production se hace igual").isEqualTo("NO_ACTION");
        for (OffsetDateTime next : List.of(control(pending.startControl(), "next_attempt_at", OffsetDateTime.class),
                control(pending.finishControl(), "next_attempt_at", OffsetDateTime.class),
                failure(pending.discardFailure(), "next_attempt_at", OffsetDateTime.class))) {
            assertThat(at(next)).as("reprogramado, no atrapado en el arrendamiento").isAfter(clock.instant()).isBefore(clock.instant().plus(Duration.ofMinutes(1)));
        }

        productionBreaker.transitionToHalfOpenState();
        productionBreaker.transitionToClosedState();
        clock.advance(Duration.ofSeconds(30));
        recovery.recoverAll();
        for (PendingSource source : PendingSource.values()) {
            assertThat(resolved(pending, source)).as("%s al volver Production", source).isTrue();
        }
    }
}
