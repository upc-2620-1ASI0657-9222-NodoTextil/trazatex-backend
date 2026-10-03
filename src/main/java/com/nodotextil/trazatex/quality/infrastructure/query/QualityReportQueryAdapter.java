package com.nodotextil.trazatex.quality.infrastructure.query;

import com.nodotextil.trazatex.quality.application.contract.QualityReportQueries;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class QualityReportQueryAdapter implements QualityReportQueries {

    private final QualityControlRepository controls;

    public QualityReportQueryAdapter(QualityControlRepository controls) {
        this.controls = controls;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompletedControlSnapshot> findCompletedControl(UUID controlId) {
        return controls.findById(controlId)
                .filter(control -> control.getCompletedAt() != null)
                .map(control -> new CompletedControlSnapshot(
                        control.getId(),
                        control.getBatchId(),
                        control.getTests().stream()
                                .map(test -> new TestSnapshot(
                                        test.criterion(),
                                        test.expectedValue(),
                                        test.actualValue(),
                                        test.unit(),
                                        test.result().name(),
                                        test.observations()
                                ))
                                .toList()
                ));
    }
}
