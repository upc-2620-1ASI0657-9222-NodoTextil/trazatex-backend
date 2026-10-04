package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.GetBatchByQrCodeUseCase;
import com.nodotextil.trazatex.production.application.GetBatchUseCase;
import com.nodotextil.trazatex.production.application.MarkFinalProductUseCase;
import com.nodotextil.trazatex.production.application.ProductionEvidenceService;
import com.nodotextil.trazatex.production.application.RegisterBatchUseCase;
import com.nodotextil.trazatex.production.application.SearchBatchesUseCase;
import com.nodotextil.trazatex.production.application.SplitBatchUseCase;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchSearchCriteria;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.InvalidProductionEvidenceException;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import com.nodotextil.trazatex.shared.audit.AuditService;
import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/batches")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'OPERATOR')")
public class BatchController {

    private final RegisterBatchUseCase registerBatch;
    private final GetBatchUseCase getBatch;
    private final GetBatchByQrCodeUseCase getBatchByQrCode;
    private final SearchBatchesUseCase searchBatches;
    private final MarkFinalProductUseCase markFinalProduct;
    private final SplitBatchUseCase splitBatch;
    private final ProductionEvidenceService evidenceService;
    private final BatchResponseAssembler responseAssembler;
    private final AuditService auditService;

    public BatchController(
            RegisterBatchUseCase registerBatch,
            GetBatchUseCase getBatch,
            GetBatchByQrCodeUseCase getBatchByQrCode,
            SearchBatchesUseCase searchBatches,
            MarkFinalProductUseCase markFinalProduct,
            SplitBatchUseCase splitBatch,
            ProductionEvidenceService evidenceService,
            BatchResponseAssembler responseAssembler,
            AuditService auditService) {
        this.registerBatch = registerBatch;
        this.getBatch = getBatch;
        this.getBatchByQrCode = getBatchByQrCode;
        this.searchBatches = searchBatches;
        this.markFinalProduct = markFinalProduct;
        this.splitBatch = splitBatch;
        this.evidenceService = evidenceService;
        this.responseAssembler = responseAssembler;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<BatchResponse> register(
            @Valid @RequestBody RegisterBatchRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        UUID companyId = user.requireCompanyId();
        Batch batch = registerBatch.execute(new RegisterBatchUseCase.Command(
                companyId,
                request.supplierName(),
                request.geographicOrigin(),
                request.materialType(),
                request.quantityKg(),
                request.composition().stream()
                        .map(component -> new CompositionComponent(
                                component.material(), component.percentage()))
                        .toList(),
                request.receptionCharacteristics()));
        auditService.record(user, "BATCH_REGISTERED", "BATCH", batch.id(), batch.traceabilityId());
        return ResponseEntity.created(URI.create("/api/batches/" + batch.id()))
                .body(responseAssembler.assemble(batch, companyId));
    }

    @GetMapping("/{id}")
    public BatchResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return responseAssembler.assemble(getBatch.execute(id), user.requireCompanyId());
    }

    @GetMapping("/qr/{qrCode}")
    public BatchResponse getByQrCode(@PathVariable String qrCode, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return responseAssembler.assemble(getBatchByQrCode.execute(qrCode), user.requireCompanyId());
    }

    @GetMapping
    public List<BatchResponse> search(
            @RequestParam(required = false) String traceabilityId,
            @RequestParam(required = false) MaterialType materialType,
            @RequestParam(required = false) OperationalPhase operationalPhase,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime registeredFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime registeredTo,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        UUID companyId = user.requireCompanyId();
        BatchSearchCriteria criteria = new BatchSearchCriteria(
                companyId,
                traceabilityId,
                materialType,
                operationalPhase,
                registeredFrom,
                registeredTo);
        return searchBatches.execute(criteria).stream()
                .map(batch -> responseAssembler.assemble(batch, companyId))
                .toList();
    }


    @PostMapping("/{batchId}/final-product")
    @PreAuthorize("hasRole('OPERATOR')")
    public BatchResponse markFinalProduct(
            @PathVariable UUID batchId,
            @RequestBody MarkFinalProductRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        UUID companyId = user.requireCompanyId();
        Batch batch = markFinalProduct.execute(
                batchId,
                companyId,
                new MarkFinalProductUseCase.Command(
                        request.buyerOrDistributor(),
                        request.price(),
                        request.currency(),
                        request.commercialDate(),
                        request.commercialReference()));
        auditService.record(user, "BATCH_MARKED_FINAL_PRODUCT", "BATCH", batchId, batch.traceabilityId());
        return responseAssembler.assemble(batch, companyId);
    }

    @PostMapping("/{batchId}/split")
    @PreAuthorize("hasRole('OPERATOR')")
    public SplitBatchResponse split(
            @PathVariable UUID batchId,
            @Valid @RequestBody SplitBatchRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        SplitBatchUseCase.Result result = splitBatch.execute(
                batchId, user.requireCompanyId(), request.quantitiesKg());
        auditService.record(user, "BATCH_SPLIT", "BATCH", batchId,
                "children=" + result.children().size());
        return SplitBatchResponse.from(result);
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
                    ProductionEvidenceOwnerType.BATCH,
                    id,
                    user.requireCompanyId(),
                    user.userId(),
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()));
            auditService.record(user, "BATCH_EVIDENCE_ADDED", "BATCH", id, response.url());
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
                        ProductionEvidenceOwnerType.BATCH,
                        id,
                        user.requireCompanyId()).stream()
                .map(ProductionEvidenceResponse::from)
                .toList();
    }
}
