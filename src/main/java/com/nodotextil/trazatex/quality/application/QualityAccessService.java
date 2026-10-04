package com.nodotextil.trazatex.quality.application;

import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import com.nodotextil.trazatex.quality.application.port.FailureRepository;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.domain.Failure;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityControl;
import java.util.UUID;

public final class QualityAccessService {

    private final QualityControlRepository controls;
    private final FailureRepository failures;
    private final ProductionQualityPort production;

    public QualityAccessService(
            QualityControlRepository controls,
            FailureRepository failures,
            ProductionQualityPort production) {
        this.controls = controls;
        this.failures = failures;
        this.production = production;
    }

    public void requireBatchCompany(UUID batchId, UUID companyId) {
        if (!production.companyIdOf(batchId).equals(companyId)) {
            throw new InvalidQualityControlException("Batch belongs to another company");
        }
    }

    public QualityControl requireControlCompany(UUID controlId, UUID companyId) {
        QualityControl control = controls.findById(controlId)
                .orElseThrow(() -> new InvalidQualityControlException("Quality control was not found"));
        requireBatchCompany(control.getBatchId(), companyId);
        return control;
    }

    public Failure requireFailureCompany(UUID failureId, UUID companyId) {
        Failure failure = failures.findById(failureId)
                .orElseThrow(() -> new InvalidQualityControlException("Failure was not found"));
        requireBatchCompany(failure.getBatchId(), companyId);
        return failure;
    }
}
