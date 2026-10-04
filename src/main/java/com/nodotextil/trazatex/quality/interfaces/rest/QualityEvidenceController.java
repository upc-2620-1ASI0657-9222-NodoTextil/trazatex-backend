package com.nodotextil.trazatex.quality.interfaces.rest;

import com.nodotextil.trazatex.quality.application.QualityAccessService;
import com.nodotextil.trazatex.quality.application.UploadQualityEvidenceUseCase;
import com.nodotextil.trazatex.quality.domain.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityEvidence;
import com.nodotextil.trazatex.shared.audit.AuditService;
import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/quality/evidence")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class QualityEvidenceController {

    private final UploadQualityEvidenceUseCase uploadEvidence;
    private final QualityAccessService accessService;
    private final AuditService auditService;

    public QualityEvidenceController(
            UploadQualityEvidenceUseCase uploadEvidence,
            QualityAccessService accessService,
            AuditService auditService) {
        this.uploadEvidence = uploadEvidence;
        this.accessService = accessService;
        this.auditService = auditService;
    }


    @GetMapping
    public List<QualityEvidence> list(
            @RequestParam EvidenceOwnerType ownerType,
            @RequestParam UUID ownerId,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        UUID companyId = user.requireCompanyId();
        if (ownerType == EvidenceOwnerType.CONTROL) {
            accessService.requireControlCompany(ownerId, companyId);
        } else {
            accessService.requireFailureCompany(ownerId, companyId);
        }
        return uploadEvidence.list(ownerType, ownerId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QualityEvidence upload(
            @RequestParam EvidenceOwnerType ownerType,
            @RequestParam UUID ownerId,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        UUID companyId = user.requireCompanyId();
        if (ownerType == EvidenceOwnerType.CONTROL) {
            accessService.requireControlCompany(ownerId, companyId);
        } else {
            accessService.requireFailureCompany(ownerId, companyId);
        }
        try {
            QualityEvidence evidence = uploadEvidence.execute(
                    ownerType,
                    ownerId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes());
            auditService.record(user, "QUALITY_EVIDENCE_ADDED", ownerType.name(), ownerId,
                    evidence.url());
            return evidence;
        } catch (IOException exception) {
            throw new InvalidQualityControlException("The evidence file could not be read");
        }
    }
}
