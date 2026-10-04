package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.TransferService;
import com.nodotextil.trazatex.production.domain.Transfer;
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
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> start(
            @Valid @RequestBody StartTransferRequest request) {
        Transfer transfer = transferService.start(
                request.sourceCompanyId(),
                request.destinationCompanyId(),
                request.batchIds());
        return ResponseEntity.created(URI.create("/api/transfers/" + transfer.id()))
                .body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse getById(@PathVariable UUID id) {
        return TransferResponse.from(transferService.get(id));
    }

    @PostMapping("/{id}/accept")
    public TransferResponse accept(@PathVariable UUID id) {
        return TransferResponse.from(transferService.accept(id));
    }

    @PostMapping("/{id}/reject")
    public TransferResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectTransferRequest request) {
        return TransferResponse.from(transferService.reject(id, request.rejectionReason()));
    }
}
