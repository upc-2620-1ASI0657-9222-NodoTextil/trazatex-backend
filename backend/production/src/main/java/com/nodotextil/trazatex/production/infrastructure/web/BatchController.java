package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.BatchResult;
import com.nodotextil.trazatex.production.application.dto.BatchSummaryResult;
import com.nodotextil.trazatex.production.application.dto.CommercialDataInput;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.QrLookupResult;
import com.nodotextil.trazatex.production.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.production.application.dto.SearchBatchesQuery;
import com.nodotextil.trazatex.production.application.dto.SplitBatchCommand;
import com.nodotextil.trazatex.production.application.dto.SplitResult;
import com.nodotextil.trazatex.production.application.port.in.ManageBatchesUseCase;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import com.nodotextil.trazatex.production.infrastructure.web.security.RequiresRole;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/batches")
class BatchController {

    private final ManageBatchesUseCase batches;

    BatchController(ManageBatchesUseCase batches) {
        this.batches = batches;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.OPERATOR)
    BatchResult register(@RequestBody RegisterBatchCommand request, Actor actor) {
        return batches.register(request, actor);
    }

    /** US13. {@code size} máximo 100 (si piden más se usa 100); orden: {@code createdAt} descendente, luego {@code id}. */
    @GetMapping
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    PageResult<BatchSummaryResult> search(@RequestParam(required = false) String traceabilityId,
                                          @RequestParam(required = false) String materialType,
                                          @RequestParam(required = false) BatchPhase phase,
                                          @RequestParam(required = false) QualityStatus qualityStatus,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          Actor actor) {
        return batches.search(new SearchBatchesQuery(traceabilityId, materialType, phase, qualityStatus, createdFrom, createdTo,
                page, size), actor);
    }

    @GetMapping("/{id}")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    BatchResult get(@PathVariable UUID id, Actor actor) {
        return batches.get(id, actor);
    }

    @GetMapping("/qr/{qrCode}")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    QrLookupResult byQr(@PathVariable String qrCode, Actor actor) {
        return batches.findByQr(qrCode, actor);
    }

    @PostMapping("/{batchId}/split")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.OPERATOR)
    SplitResult split(@PathVariable UUID batchId, @RequestBody SplitBatchCommand request, Actor actor) {
        return batches.split(batchId, request, actor);
    }

    /** Reemplaza los datos comerciales del producto final por los enviados (los omitidos quedan vacíos). */
    @PutMapping("/{id}/commercial-data")
    @RequiresRole(Role.OPERATOR)
    BatchResult updateCommercialData(@PathVariable UUID id, @RequestBody CommercialDataInput request, Actor actor) {
        return batches.updateCommercialData(id, request, actor);
    }
}
