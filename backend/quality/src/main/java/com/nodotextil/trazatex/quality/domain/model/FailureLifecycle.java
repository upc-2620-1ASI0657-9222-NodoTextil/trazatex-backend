package com.nodotextil.trazatex.quality.domain.model;

import java.util.UUID;

/**
 * Qué hace un control finalizado con las fallas (decisión del usuario, 2026-10-23: si una reevaluación vuelve a fallar se reutiliza
 * la falla existente). Solo una reevaluación de un lote {@code PENDING_REEVALUATION} —que viene de una falla {@code OPEN} propia—
 * reutiliza o resuelve esa falla. Una revisión de falla derivada ({@code POTENTIAL_DERIVED_FAILURE}) no tiene falla propia previa:
 * si falla, <b>genera la suya</b> (US34 esc. 4); si sale conforme, no hay falla que resolver.
 */
public final class FailureLifecycle {

    private FailureLifecycle() {
    }

    /**
     * @param statusBeforeControl el estado de calidad del lote <b>antes</b> de aplicar el resultado ({@link QualityStatusChange#previous()})
     * @param openFailure         la falla abierta del lote, o {@code null} si no tiene
     * @param newFailureId        el id para una falla nueva (solo se usa si la acción es {@link FailureAction#GENERATE})
     */
    public static FailureChange after(QualityStatus statusBeforeControl, ControlOutcome outcome, QualityFailure openFailure, UUID newFailureId) {
        boolean continuesAnOpenFailure = statusBeforeControl == QualityStatus.PENDING_REEVALUATION && openFailure != null
                && openFailure.status() == FailureStatus.OPEN;
        if (outcome.conforming()) {
            if (continuesAnOpenFailure) {
                openFailure.resolve(outcome);
                return new FailureChange(FailureAction.RESOLVE, openFailure);
            }
            return new FailureChange(FailureAction.NONE, null);
        }
        if (continuesAnOpenFailure) {
            openFailure.reopenAfterFailedReevaluation(outcome);
            return new FailureChange(FailureAction.REOPEN, openFailure);
        }
        return new FailureChange(FailureAction.GENERATE, QualityFailure.generate(newFailureId, outcome));
    }
}
