package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.TransferService;
import com.nodotextil.trazatex.production.domain.Transfer;
import com.nodotextil.trazatex.shared.audit.AuditService;
import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'OPERATOR')")
public class TransferController {

    private final TransferService transferService;
    private final AuditService auditService;

    public TransferController(TransferService transferService, AuditService auditService) {
        this.transferService = transferService;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<TransferResponse> start(
            @Valid @RequestBody StartTransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        Transfer transfer = transferService.start(
                user.requireCompanyId(), request.destinationCompanyId(), request.batchIds());
        auditService.record(user, "TRANSFER_STARTED", "TRANSFER", transfer.id(),
                "destination=" + transfer.destinationCompanyId());
        return ResponseEntity.created(URI.create("/api/transfers/" + transfer.id()))
                .body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return TransferResponse.from(transferService.get(id, user.requireCompanyId()));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public TransferResponse accept(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        Transfer transfer = transferService.accept(id, user.requireCompanyId());
        auditService.record(user, "TRANSFER_ACCEPTED", "TRANSFER", id, null);
        return TransferResponse.from(transfer);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public TransferResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectTransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        Transfer transfer = transferService.reject(
                id, user.requireCompanyId(), request.rejectionReason());
        auditService.record(user, "TRANSFER_REJECTED", "TRANSFER", id, request.rejectionReason());
        return TransferResponse.from(transfer);
    }
}
