package com.nodotextil.trazatex.quality.domain;

import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.ControlOutcome;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import com.nodotextil.trazatex.quality.domain.model.TestResult;

import java.time.Instant;
import java.util.UUID;

public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    public static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    public static final UUID OTHER_COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    public static final UUID INSPECTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private Fixtures() {
    }

    public static UUID id() {
        return UUID.randomUUID();
    }

    public static BatchProjection batch(BatchPhase phase, QualityStatus status, boolean hold, boolean everControlled) {
        return BatchProjection.restore(id(), COMPANY, phase, hold, status, everControlled, 1, NOW);
    }

    /** Un lote disponible con el estado de calidad dado y la retención que ese estado implica. */
    public static BatchProjection available(QualityStatus status) {
        return batch(BatchPhase.AVAILABLE, status, status.retainsBatch(), status == QualityStatus.CONFORMING);
    }

    public static QualityControl startedControl(BatchProjection batch) {
        QualityControl control = QualityControl.start(id(), batch, INSPECTOR, null, NOW);
        control.confirmStarted();
        return control;
    }

    /** Un control en curso al que se le registraron pruebas con los resultados dados y que se finalizó. */
    public static ControlOutcome finished(BatchProjection batch, TestResult... results) {
        QualityControl control = startedControl(batch);
        for (TestResult result : results) {
            control.addTest(id(), "Criterio", "100", "98", "%", result, null);
        }
        return control.finish(NOW.plusSeconds(60));
    }
}
