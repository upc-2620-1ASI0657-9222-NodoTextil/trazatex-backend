package com.nodotextil.trazatex.quality.application;

import org.springframework.transaction.annotation.Transactional;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityControl;
import com.nodotextil.trazatex.quality.domain.QualityTest;
import com.nodotextil.trazatex.quality.domain.TestResult;

import java.util.Objects;
import java.util.UUID;

public class RegisterQualityTestUseCase {

    private final QualityControlRepository qualityControlRepository;

    public RegisterQualityTestUseCase(QualityControlRepository qualityControlRepository) {
        this.qualityControlRepository = Objects.requireNonNull(qualityControlRepository);
    }

    @Transactional
    public QualityTest execute(
            UUID controlId,
            String criterion,
            String expectedValue,
            String actualValue,
            String unit,
            TestResult result,
            String observations) {

        Objects.requireNonNull(controlId, "Control id is required");

        QualityControl control = qualityControlRepository.findById(controlId)
                .orElseThrow(() -> new InvalidQualityControlException(
                        "Quality control was not found"
                ));

        QualityTest test = new QualityTest(
                UUID.randomUUID(),
                criterion,
                expectedValue,
                actualValue,
                unit,
                result,
                observations
        );

        control.addTest(test);
        qualityControlRepository.save(control);

        return test;
    }
}
