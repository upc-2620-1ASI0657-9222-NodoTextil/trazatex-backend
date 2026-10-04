package com.nodotextil.trazatex.quality.interfaces.rest;

import com.nodotextil.trazatex.quality.application.FinishQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.ManageFailureUseCase;
import com.nodotextil.trazatex.quality.application.QualityAccessService;
import com.nodotextil.trazatex.quality.application.RegisterQualityTestUseCase;
import com.nodotextil.trazatex.quality.application.StartQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.contract.QualityStatusQuery;
import com.nodotextil.trazatex.quality.domain.Failure;
import com.nodotextil.trazatex.quality.domain.FailureDecision;
import com.nodotextil.trazatex.quality.domain.QualityControl;
import com.nodotextil.trazatex.quality.domain.QualityStatus;
import com.nodotextil.trazatex.quality.domain.QualityTest;
import com.nodotextil.trazatex.quality.domain.TestResult;
import com.nodotextil.trazatex.shared.audit.AuditService;
import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quality")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class QualityControlController {

    private final StartQualityControlUseCase startControl;
    private final RegisterQualityTestUseCase registerTest;
    private final FinishQualityControlUseCase finishControl;
    private final ManageFailureUseCase manageFailure;
    private final QualityAccessService accessService;
    private final QualityStatusQuery qualityStatusQuery;
    private final AuditService auditService;

    public QualityControlController(
            StartQualityControlUseCase startControl,
            RegisterQualityTestUseCase registerTest,
            FinishQualityControlUseCase finishControl,
            ManageFailureUseCase manageFailure,
            QualityAccessService accessService,
            QualityStatusQuery qualityStatusQuery,
            AuditService auditService) {
        this.startControl = startControl;
        this.registerTest = registerTest;
        this.finishControl = finishControl;
        this.manageFailure = manageFailure;
        this.accessService = accessService;
        this.qualityStatusQuery = qualityStatusQuery;
        this.auditService = auditService;
    }

    @GetMapping("/batches/{batchId}/status")
    public QualityStatus status(@PathVariable UUID batchId, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        accessService.requireBatchCompany(batchId, user.requireCompanyId());
        return qualityStatusQuery.findStatus(batchId).orElse(QualityStatus.NOT_REVIEWED);
    }

    @GetMapping("/controls/{controlId}")
    public QualityControl getControl(@PathVariable UUID controlId, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return accessService.requireControlCompany(controlId, user.requireCompanyId());
    }

    @GetMapping("/failures/{failureId}")
    public Failure getFailure(@PathVariable UUID failureId, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return accessService.requireFailureCompany(failureId, user.requireCompanyId());
    }

    @PostMapping("/batches/{batchId}/controls")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityControl start(@PathVariable UUID batchId, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        accessService.requireBatchCompany(batchId, user.requireCompanyId());
        QualityControl control = startControl.execute(batchId);
        auditService.record(user, "QUALITY_CONTROL_STARTED", "QUALITY_CONTROL", control.getId(),
                "batch=" + batchId);
        return control;
    }

    @PostMapping("/controls/{controlId}/tests")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityTest addTest(
            @PathVariable UUID controlId,
            @RequestBody RegisterTestRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        accessService.requireControlCompany(controlId, user.requireCompanyId());
        QualityTest test = registerTest.execute(
                controlId,
                request.criterion(),
                request.expectedValue(),
                request.actualValue(),
                request.unit(),
                request.result(),
                request.observations());
        auditService.record(user, "QUALITY_TEST_ADDED", "QUALITY_CONTROL", controlId,
                request.criterion());
        return test;
    }

    @PostMapping("/controls/{controlId}/finish")
    public QualityControl finish(@PathVariable UUID controlId, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        accessService.requireControlCompany(controlId, user.requireCompanyId());
        QualityControl control = finishControl.execute(controlId);
        auditService.record(user, "QUALITY_CONTROL_FINISHED", "QUALITY_CONTROL", controlId, null);
        return control;
    }

    @PutMapping("/failures/{failureId}")
    public Failure manageFailure(
            @PathVariable UUID failureId,
            @RequestBody ManageFailureRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        accessService.requireFailureCompany(failureId, user.requireCompanyId());
        Failure failure = manageFailure.execute(
                failureId,
                request.cause(),
                request.affectedQuantityKg(),
                request.observations(),
                request.decision());
        auditService.record(user, "FAILURE_MANAGED", "FAILURE", failureId, request.decision().name());
        return failure;
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
