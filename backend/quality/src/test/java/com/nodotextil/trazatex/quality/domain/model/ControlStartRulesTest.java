package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.nodotextil.trazatex.quality.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.INSPECTOR;
import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.batch;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US23 y US34: qué tipo de control corresponde a cada lote y cuáles no se pueden controlar. */
class ControlStartRulesTest {

    /** Estado de calidad × ¿tuvo algún control? → tipo de control, o el código del 409. Con el lote {@code AVAILABLE}. */
    static Stream<Arguments> byQualityStatus() {
        return Stream.of(
                Arguments.of(QualityStatus.NOT_REVIEWED, false, ControlType.EVALUATION, null),
                Arguments.of(QualityStatus.PENDING_REEVALUATION, true, ControlType.REEVALUATION, null),
                Arguments.of(QualityStatus.PENDING_REEVALUATION, false, ControlType.REEVALUATION, null),
                // US34 esc. 1 y 2
                Arguments.of(QualityStatus.POTENTIAL_DERIVED_FAILURE, false, ControlType.EVALUATION, null),
                Arguments.of(QualityStatus.POTENTIAL_DERIVED_FAILURE, true, ControlType.REEVALUATION, null),
                // decisiones del usuario, 2026-10-23
                Arguments.of(QualityStatus.CONFORMING, true, null, ErrorCodes.BATCH_ALREADY_CONFORMING),
                Arguments.of(QualityStatus.FAILED, true, null, ErrorCodes.BATCH_FAILED_REQUIRES_REEVALUATION));
    }

    @ParameterizedTest(name = "{0}, tuvo control: {1} → {2} {3}")
    @MethodSource("byQualityStatus")
    void theQualityStatusDecidesTheControlTypeOrRefusesTheBatch(QualityStatus status, boolean everControlled, ControlType type, String errorCode) {
        BatchProjection batch = batch(BatchPhase.AVAILABLE, status, status.retainsBatch(), everControlled);

        if (errorCode == null) {
            assertThat(batch.controlTypeToStart()).isEqualTo(type);
            QualityControl control = QualityControl.start(id(), batch, INSPECTOR, null, NOW);
            assertThat(control.controlType()).isEqualTo(type);
            assertThat(control.batchId()).isEqualTo(batch.batchId());
            assertThat(control.companyId()).isEqualTo(COMPANY);
        } else {
            assertThatThrownBy(batch::controlTypeToStart).isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(errorCode));
            assertThatThrownBy(() -> QualityControl.start(id(), batch, INSPECTOR, null, NOW)).isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void theQualityStatusTableCoversEveryStatus() {
        assertThat(byQualityStatus().map(a -> a.get()[0]).distinct().count()).isEqualTo(QualityStatus.values().length);
    }

    @ParameterizedTest
    @EnumSource(value = BatchPhase.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void onlyAnAvailableBatchCanBeControlledWhateverItsQualityStatus(BatchPhase phase) {
        for (QualityStatus status : QualityStatus.values()) {
            BatchProjection batch = batch(phase, status, status.retainsBatch(), false);
            String expected = phase == BatchPhase.IN_QUALITY_CONTROL ? ErrorCodes.CONTROL_ALREADY_ACTIVE : ErrorCodes.BATCH_NOT_AVAILABLE_FOR_CONTROL;

            assertThatThrownBy(batch::controlTypeToStart).as("%s / %s", phase, status)
                    .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(expected));
        }
    }

    @Test
    void aBatchWithAnActiveControlCannotStartAnotherOne_US23Scenario3() {
        BatchProjection inControl = batch(BatchPhase.IN_QUALITY_CONTROL, QualityStatus.NOT_REVIEWED, false, false);

        assertThatThrownBy(() -> QualityControl.start(id(), inControl, INSPECTOR, null, NOW))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.CONTROL_ALREADY_ACTIVE));
    }

    @Test
    void aBatchWhoseQualityStatusIsNotKnownYetCannotBeControlled() {
        BatchProjection partial = BatchProjection.partial(id(), COMPANY, BatchPhase.AVAILABLE, false, 3, NOW);

        assertThatThrownBy(partial::controlTypeToStart)
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.BATCH_QUALITY_UNKNOWN));
    }
}
