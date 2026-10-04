package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.CompleteTransformationUseCase;
import com.nodotextil.trazatex.production.application.GetTransformationUseCase;
import com.nodotextil.trazatex.production.application.ProductionEvidenceService;
import com.nodotextil.trazatex.production.application.StartTransformationUseCase;
import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.production.domain.InvalidProductionEvidenceException;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transformations")
public class TransformationController {

    private final StartTransformationUseCase startUseCase;
    private final CompleteTransformationUseCase completeUseCase;
    private final GetTransformationUseCase getUseCase;
    private final ProductionEvidenceService evidenceService;

    public TransformationController(
            StartTransformationUseCase startUseCase,
            CompleteTransformationUseCase completeUseCase,
            GetTransformationUseCase getUseCase,
            ProductionEvidenceService evidenceService) {
        this.startUseCase = startUseCase;
        this.completeUseCase = completeUseCase;
        this.getUseCase = getUseCase;
        this.evidenceService = evidenceService;
    }

    @PostMapping
    public ResponseEntity<TransformationResponse> start(
            @Valid @RequestBody StartTransformationRequest request) {
        Transformation transformation = startUseCase.execute(
                new StartTransformationUseCase.Command(
                        request.companyId(),
                        request.operatorId(),
                        request.machineId(),
                        request.type(),
                        request.inputBatchIds()));
        return ResponseEntity
                .created(URI.create("/api/transformations/" + transformation.id()))
                .body(TransformationResponse.from(transformation));
    }

    @PostMapping("/{id}/complete")
    public CompleteTransformationResponse complete(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteTransformationRequest request) {
        CompleteTransformationUseCase.Command command =
                new CompleteTransformationUseCase.Command(
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
        return CompleteTransformationResponse.from(completeUseCase.execute(id, command));
    }

    @GetMapping("/{id}")
    public TransformationResponse getById(@PathVariable UUID id) {
        return TransformationResponse.from(getUseCase.execute(id));
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
                    ProductionEvidenceOwnerType.TRANSFORMATION,
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
        return evidenceService.list(ProductionEvidenceOwnerType.TRANSFORMATION, id).stream()
                .map(ProductionEvidenceResponse::from)
                .toList();
    }
}
