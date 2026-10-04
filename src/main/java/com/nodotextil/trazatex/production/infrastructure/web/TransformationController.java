package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.CompleteTransformationUseCase;
import com.nodotextil.trazatex.production.application.GetTransformationUseCase;
import com.nodotextil.trazatex.production.application.ProductionEvidenceService;
import com.nodotextil.trazatex.production.application.StartTransformationUseCase;
import com.nodotextil.trazatex.production.domain.InvalidProductionEvidenceException;
import com.nodotextil.trazatex.production.domain.InvalidTransformationException;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.shared.audit.AuditService;
import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transformations")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'OPERATOR')")
public class TransformationController {

    private final StartTransformationUseCase startTransformation;
    private final CompleteTransformationUseCase completeTransformation;
    private final GetTransformationUseCase getTransformation;
    private final ProductionEvidenceService evidenceService;
    private final AuditService auditService;

    public TransformationController(
            StartTransformationUseCase startTransformation,
            CompleteTransformationUseCase completeTransformation,
            GetTransformationUseCase getTransformation,
            ProductionEvidenceService evidenceService,
            AuditService auditService) {
        this.startTransformation = startTransformation;
        this.completeTransformation = completeTransformation;
        this.getTransformation = getTransformation;
        this.evidenceService = evidenceService;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<TransformationResponse> start(
            @Valid @RequestBody StartTransformationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        Transformation transformation = startTransformation.execute(
                new StartTransformationUseCase.Command(
                        user.requireCompanyId(),
                        user.userId(),
                        request.machineId(),
                        request.type(),
                        request.inputBatchIds()));
        auditService.record(user, "TRANSFORMATION_STARTED", "TRANSFORMATION", transformation.id(),
                transformation.type().name());
        return ResponseEntity.created(URI.create("/api/transformations/" + transformation.id()))
                .body(TransformationResponse.from(transformation));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('OPERATOR')")
    public CompleteTransformationResponse complete(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteTransformationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        CompleteTransformationUseCase.Command command = new CompleteTransformationUseCase.Command(
                request.outputs().stream()
                        .map(output -> new CompleteTransformationUseCase.OutputCommand(
                                output.quantityKg(),
                                output.materialType(),
                                output.geographicOrigin(),
                                output.receptionCharacteristics(),
                                output.finalProduct(),
                                output.buyerOrDistributor(),
                                output.price(),
                                output.currency(),
                                output.commercialDate(),
                                output.commercialReference()))
                        .toList(),
                request.wasteKg(),
                request.wasteReason());
        CompleteTransformationUseCase.Result result = completeTransformation.execute(
                id, user.requireCompanyId(), command);
        auditService.record(user, "TRANSFORMATION_COMPLETED", "TRANSFORMATION", id,
                "outputs=" + result.outputs().size());
        return CompleteTransformationResponse.from(result);
    }

    @GetMapping("/{id}")
    public TransformationResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        Transformation transformation = getTransformation.execute(id);
        requireCompany(transformation, user.requireCompanyId());
        return TransformationResponse.from(transformation);
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OPERATOR')")
    public ProductionEvidenceResponse uploadEvidence(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        try {
            ProductionEvidenceResponse response = ProductionEvidenceResponse.from(evidenceService.upload(
                    ProductionEvidenceOwnerType.TRANSFORMATION,
                    id,
                    user.requireCompanyId(),
                    user.userId(),
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()));
            auditService.record(user, "TRANSFORMATION_EVIDENCE_ADDED", "TRANSFORMATION", id, response.url());
            return response;
        } catch (IOException exception) {
            throw new InvalidProductionEvidenceException("Evidence image cannot be read", exception);
        }
    }

    @GetMapping("/{id}/evidence")
    public List<ProductionEvidenceResponse> listEvidence(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return evidenceService.list(
                        ProductionEvidenceOwnerType.TRANSFORMATION,
                        id,
                        user.requireCompanyId()).stream()
                .map(ProductionEvidenceResponse::from)
                .toList();
    }

    private static void requireCompany(Transformation transformation, UUID companyId) {
        if (!transformation.companyId().equals(companyId)) {
            throw new InvalidTransformationException("Transformation belongs to another company");
        }
    }
}
