package com.nodotextil.trazatex.quality.interfaces.rest;

import com.nodotextil.trazatex.quality.application.FinishQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.ManageFailureUseCase;
import com.nodotextil.trazatex.quality.application.MarkPotentialDerivedFailureUseCase;
import com.nodotextil.trazatex.quality.application.RegisterQualityTestUseCase;
import com.nodotextil.trazatex.quality.application.StartQualityControlUseCase;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.Failure;
import com.nodotextil.trazatex.quality.domain.FailureDecision;
import com.nodotextil.trazatex.quality.domain.QualityControl;
import com.nodotextil.trazatex.quality.domain.QualityTest;
import com.nodotextil.trazatex.quality.domain.TestResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/quality")
public class QualityControlController {

    private final StartQualityControlUseCase startControl;
    private final RegisterQualityTestUseCase registerTest;
    private final FinishQualityControlUseCase finishControl;
    private final ManageFailureUseCase manageFailure;
    private final MarkPotentialDerivedFailureUseCase markPotentialDerivedFailure;

    public QualityControlController(
            StartQualityControlUseCase startControl,
            RegisterQualityTestUseCase registerTest,
            FinishQualityControlUseCase finishControl,
            ManageFailureUseCase manageFailure,
            MarkPotentialDerivedFailureUseCase markPotentialDerivedFailure) {

        this.startControl = startControl;
        this.registerTest = registerTest;
        this.finishControl = finishControl;
        this.manageFailure = manageFailure;
        this.markPotentialDerivedFailure = markPotentialDerivedFailure;
    }

    @PostMapping("/batches/{batchId}/controls")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityControl start(@PathVariable UUID batchId) {
        return startControl.execute(batchId);
    }

    @PostMapping("/controls/{controlId}/tests")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityTest addTest(
            @PathVariable UUID controlId,
            @RequestBody RegisterTestRequest request) {

        return registerTest.execute(
                controlId,
                request.criterion(),
                request.expectedValue(),
                request.actualValue(),
                request.unit(),
                request.result(),
                request.observations()
        );
    }

    @PostMapping("/controls/{controlId}/finish")
    public QualityControl finish(@PathVariable UUID controlId) {
        return finishControl.execute(controlId);
    }

    @PutMapping("/failures/{failureId}")
    public Failure manageFailure(
            @PathVariable UUID failureId,
            @RequestBody ManageFailureRequest request) {

        return manageFailure.execute(
                failureId,
                request.cause(),
                request.affectedQuantityKg(),
                request.observations(),
                request.decision()
        );
    }

    @PostMapping("/batches/{batchId}/potential-derived-failure")
    public BatchQuality markPotentialDerivedFailure(@PathVariable UUID batchId) {
        return markPotentialDerivedFailure.execute(batchId);
    }

    public record RegisterTestRequest(
            String criterion,
            String expectedValue,
            String actualValue,
            String unit,
            TestResult result,
            String observations) {
    }

    public record ManageFailureRequest(
            String cause,
            BigDecimal affectedQuantityKg,
            String observations,
            FailureDecision decision) {
    }
}
