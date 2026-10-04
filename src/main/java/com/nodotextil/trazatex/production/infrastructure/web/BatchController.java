package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.GetBatchUseCase;
import com.nodotextil.trazatex.production.application.RegisterBatchUseCase;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final RegisterBatchUseCase registerBatchUseCase;
    private final GetBatchUseCase getBatchUseCase;

    public BatchController(
            RegisterBatchUseCase registerBatchUseCase,
            GetBatchUseCase getBatchUseCase) {
        this.registerBatchUseCase = registerBatchUseCase;
        this.getBatchUseCase = getBatchUseCase;
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
}
