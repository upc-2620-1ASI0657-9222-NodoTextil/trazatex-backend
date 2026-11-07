package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyPrivacyCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un evento al que le falta algo obligatorio es ilegible (va a la DLT): cada comando lo rechaza nombrando el campo. */
class EventCommandsTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final List<CompositionComponent> COMPOSITION = List.of(new CompositionComponent("Algodón", new BigDecimal("100")));

    private static void rejects(Supplier<?> command, String field) {
        assertThatThrownBy(command::get).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_EVENT);
            assertThat(e.details()).containsExactly(field);
        });
    }

    private static RegisterBatchCommand registered(UUID eventId, String qr, BigDecimal kg, List<CompositionComponent> composition) {
        return new RegisterBatchCommand(eventId, ID, "TT-AAAAAAAAAA", qr, ID, "Algodón", composition, kg, null, null, null, NOW);
    }

    @Test
    void aBatchRegisteredNeedsItsIdentityButNotItsOptionalFields() {
        assertThatCode(() -> registered(ID, "QR", new BigDecimal("1"), COMPOSITION)).doesNotThrowAnyException();
        rejects(() -> registered(null, "QR", new BigDecimal("1"), COMPOSITION), "eventId");
        rejects(() -> registered(ID, " ", new BigDecimal("1"), COMPOSITION), "qrCode");
        rejects(() -> registered(ID, "QR", BigDecimal.ZERO, COMPOSITION), "quantityKg");
        rejects(() -> registered(ID, "QR", new BigDecimal("-1"), COMPOSITION), "quantityKg");
        rejects(() -> registered(ID, "QR", null, COMPOSITION), "quantityKg");
        rejects(() -> registered(ID, "QR", new BigDecimal("1"), List.of()), "composition");
    }

    @Test
    void quantitiesAreKeptAtThreeDecimals() {
        assertThat(registered(ID, "QR", new BigDecimal("12.5"), COMPOSITION).quantityKg()).isEqualTo(new BigDecimal("12.500"));
    }

    @Test
    void aSplitNeedsChildrenAndEachChildNeedsItsData() {
        ApplyBatchSplitCommand.Child child = new ApplyBatchSplitCommand.Child(ID, "TT-BBBBBBBBBB", "QR2", "Algodón", new BigDecimal("4"),
                COMPOSITION, null, null, null, QualityStatus.NOT_REVIEWED, NOW);
        assertThatCode(() -> new ApplyBatchSplitCommand(ID, ID, ID, List.of(child))).doesNotThrowAnyException();
        rejects(() -> new ApplyBatchSplitCommand(ID, ID, ID, List.of()), "children");
        rejects(() -> new ApplyBatchSplitCommand(ID, null, ID, List.of(child)), "parentBatchId");
        rejects(() -> new ApplyBatchSplitCommand.Child(ID, "TT-BBBBBBBBBB", "QR2", null, new BigDecimal("4"), COMPOSITION, null, null, null,
                QualityStatus.NOT_REVIEWED, NOW), "children.materialType");
        rejects(() -> new ApplyBatchSplitCommand.Child(ID, "TT-BBBBBBBBBB", "QR2", "Algodón", new BigDecimal("4"), COMPOSITION, null, null,
                null, null, NOW), "children.qualityStatus");
    }

    @Test
    void aTransformationNeedsItsInputsDatesAndBalanceButOutputsMayBeEmpty() {
        assertThatCode(() -> transformation(List.of(ID), List.of(), "0", "0")).doesNotThrowAnyException();
        rejects(() -> transformation(List.of(), List.of(), "0", "0"), "inputs");
        rejects(() -> transformation(List.of(ID), null, "0", "0"), "outputs");
        rejects(() -> transformation(List.of(ID), List.of(), "-1", "0"), "wasteKg");
        rejects(() -> transformation(List.of(ID), List.of(), "0", null), "shrinkageKg");
    }

    private static ApplyTransformationCompletedCommand transformation(List<UUID> inputs, List<ApplyTransformationCompletedCommand.Output> outputs,
                                                                      String waste, String shrinkage) {
        return new ApplyTransformationCompletedCommand(ID, ID, TransformationType.DYEING, ID, null, null, inputs, outputs,
                waste == null ? null : new BigDecimal(waste), null, shrinkage == null ? null : new BigDecimal(shrinkage), NOW, NOW);
    }

    @Test
    void aCompanyRegisteredNeedsTheCompanyAndANonBlankNameWhichIsTrimmed() {
        assertThat(new com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand(ID, ID, "  Hilos SAC ").businessName()).isEqualTo("Hilos SAC");
        rejects(() -> new com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand(ID, ID, "   "), "businessName");
        rejects(() -> new com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand(ID, null, "Hilos SAC"), "companyId");
    }

    @Test
    void aPhaseChangeNeedsItsVersionAndItCannotBeNegative() {
        assertThatCode(() -> new ApplyBatchPhaseChangedCommand(ID, ID, ID, BatchPhase.AVAILABLE, false, 0L)).doesNotThrowAnyException();
        rejects(() -> new ApplyBatchPhaseChangedCommand(ID, ID, ID, BatchPhase.AVAILABLE, false, null), "batchVersion");
        rejects(() -> new ApplyBatchPhaseChangedCommand(ID, ID, ID, BatchPhase.AVAILABLE, false, -1L), "batchVersion");
        rejects(() -> new ApplyBatchPhaseChangedCommand(ID, ID, null, BatchPhase.AVAILABLE, false, 1L), "companyId");
        rejects(() -> new ApplyBatchPhaseChangedCommand(ID, ID, ID, null, false, 1L), "newPhase");
    }

    @Test
    void theRemainingEventsRejectMissingIdsAndStatuses() {
        rejects(() -> new ApplyQualityStatusCommand(ID, ID, null), "newStatus");
        rejects(() -> new ApplyQualityStatusCommand(ID, null, QualityStatus.CONFORMING), "batchId");
        rejects(() -> new ApplyTransferAcceptedCommand(ID, ID, List.of(), ID, NOW), "batchIds");
        rejects(() -> new ApplyTransferAcceptedCommand(ID, ID, List.of(ID), null, NOW), "toCompanyId");
        rejects(() -> new ApplyCompanyPrivacyCommand(ID, ID, null), "fields");
        assertThatCode(() -> new ApplyCompanyPrivacyCommand(ID, ID, Map.of())).as("un mapa vacío es válido: todo privado").doesNotThrowAnyException();
    }
}
