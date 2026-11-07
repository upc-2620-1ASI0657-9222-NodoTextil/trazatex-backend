package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyFailureCauseCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityTestsCommand;

/**
 * Casos de uso de los detalles de calidad que Traceability muestra en la genealogía, filtrados por la política de privacidad
 * ({@code QUALITY_TEST_VALUES} y {@code FAILURE_CAUSE}): el listener los llama dentro de la transacción idempotente.
 */
public interface ApplyQualityDetailsUseCase {

    /** {@code QualityControlCompleted} con pruebas. */
    void applyTests(ApplyQualityTestsCommand command);

    /** {@code QualityFailureUpdated}. */
    void applyFailureCause(ApplyFailureCauseCommand command);
}
