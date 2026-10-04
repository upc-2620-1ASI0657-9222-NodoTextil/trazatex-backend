package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.GetBatchUseCase;
import com.nodotextil.trazatex.production.application.GetBatchByQrCodeUseCase;
import com.nodotextil.trazatex.production.application.RegisterBatchUseCase;
import com.nodotextil.trazatex.production.application.ProductionEvidenceService;
import com.nodotextil.trazatex.production.application.SearchBatchesUseCase;
import com.nodotextil.trazatex.production.application.SplitBatchUseCase;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchSearchCriteria;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import com.nodotextil.trazatex.production.domain.InvalidProductionEvidenceException;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
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
public class BatchController {

    private final RegisterBatchUseCase registerBatchUseCase;
    private final GetBatchUseCase getBatchUseCase;
    private final GetBatchByQrCodeUseCase getBatchByQrCodeUseCase;
    private final SearchBatchesUseCase searchBatchesUseCase;
    private final SplitBatchUseCase splitBatchUseCase;
    private final ProductionEvidenceService evidenceService;

    public BatchController(
            RegisterBatchUseCase registerBatchUseCase,
            GetBatchUseCase getBatchUseCase,
            GetBatchByQrCodeUseCase getBatchByQrCodeUseCase,
            SearchBatchesUseCase searchBatchesUseCase,
            SplitBatchUseCase splitBatchUseCase,
            ProductionEvidenceService evidenceService) {
        this.registerBatchUseCase = registerBatchUseCase;
        this.getBatchUseCase = getBatchUseCase;
        this.getBatchByQrCodeUseCase = getBatchByQrCodeUseCase;
        this.searchBatchesUseCase = searchBatchesUseCase;
        this.splitBatchUseCase = splitBatchUseCase;
        this.evidenceService = evidenceService;
    }

    @PostMapping
    public ResponseEntity<BatchResponse> register(
            @Valid @RequestBody RegisterBatchRequest request) {
        Batch batch = registerBatchUseCase.execute(new RegisterBatchUseCase.Command(
                request.responsibleCompanyId(),
                request.supplierName(),
                request.geographicOrigin(),
                request.materialType(),
                request.quantityKg(),
                request.composition().stream()
                        .map(component -> new CompositionComponent(
                                component.material(), component.percentage()))
                        .toList(),
                request.receptionCharacteristics()));

        return ResponseEntity
                .created(URI.create("/api/batches/" + batch.id()))
                .body(BatchResponse.from(batch));
    }

    @GetMapping("/{id}")
    public BatchResponse getById(@PathVariable UUID id) {
        return BatchResponse.from(getBatchUseCase.execute(id));
    }

    @GetMapping("/qr/{qrCode}")
    public BatchResponse getByQrCode(@PathVariable String qrCode) {
        return BatchResponse.from(getBatchByQrCodeUseCase.execute(qrCode));
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
                    LocalDateTime registeredTo) {
        BatchSearchCriteria criteria = new BatchSearchCriteria(
                traceabilityId,
                materialType,
                operationalPhase,
                registeredFrom,
                registeredTo);
        return searchBatchesUseCase.execute(criteria).stream()
                .map(BatchResponse::from)
                .toList();
    }

    @PostMapping("/{batchId}/split")
    public SplitBatchResponse split(
            @PathVariable UUID batchId,
            @Valid @RequestBody SplitBatchRequest request) {
        return SplitBatchResponse.from(
                splitBatchUseCase.execute(batchId, request.quantitiesKg()));
    }

    @PostMapping(
            value = "/{id}/evidence",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductionEvidenceResponse uploadEvidence(
            @PathVariable UUID id,
            @RequestParam("uploadedByUserId") UUID uploadedByUserId,
            @RequestParam("file") MultipartFile file) {
        try {
            return ProductionEvidenceResponse.from(evidenceService.upload(
                    ProductionEvidenceOwnerType.BATCH,
                    id,
                    uploadedByUserId,
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()));
        } catch (IOException exception) {
            throw new InvalidProductionEvidenceException(
                    "Evidence image cannot be read", exception);
        }
    }

    @GetMapping("/{id}/evidence")
    public List<ProductionEvidenceResponse> listEvidence(@PathVariable UUID id) {
        return evidenceService.list(ProductionEvidenceOwnerType.BATCH, id).stream()
                .map(ProductionEvidenceResponse::from)
                .toList();
    }
}
