package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.contract.CompanyPrivacyAccess;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.quality.application.contract.QualityStatusQuery;
import com.nodotextil.trazatex.quality.domain.QualityStatus;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class BatchResponseAssembler {

    private final CompanyPrivacyAccess privacyAccess;
    private final OrganizationAccess organizationAccess;
    private final QualityStatusQuery qualityStatusQuery;

    BatchResponseAssembler(
            CompanyPrivacyAccess privacyAccess,
            OrganizationAccess organizationAccess,
            QualityStatusQuery qualityStatusQuery) {
        this.privacyAccess = privacyAccess;
        this.organizationAccess = organizationAccess;
        this.qualityStatusQuery = qualityStatusQuery;
    }

    BatchResponse assemble(Batch batch, UUID requesterCompanyId) {
        boolean ownBatch = batch.responsibleCompanyId().equals(requesterCompanyId);
        if (!ownBatch && !organizationAccess.companiesShareLicense(
                batch.responsibleCompanyId(), requesterCompanyId)) {
            throw new AccessDeniedException("Batch is outside the authenticated textile chain");
        }

        QualityStatus qualityStatus = qualityStatusQuery.findStatus(batch.id())
                .orElse(QualityStatus.NOT_REVIEWED);

        return new BatchResponse(
                batch.id(),
                batch.traceabilityId(),
                ownBatch ? batch.qrCode() : null,
                batch.responsibleCompanyId(),
                visible(batch, requesterCompanyId, PrivacyField.SUPPLIER) ? batch.supplierName() : null,
                visible(batch, requesterCompanyId, PrivacyField.GEOGRAPHIC_ORIGIN) ? batch.geographicOrigin() : null,
                batch.materialType(),
                visible(batch, requesterCompanyId, PrivacyField.QUANTITY) ? batch.quantityKg() : null,
                visible(batch, requesterCompanyId, PrivacyField.COMPOSITION)
                        ? batch.composition().stream()
                                .map(component -> new BatchResponse.CompositionComponentResponse(
                                        component.material(), component.percentage()))
                                .toList()
                        : null,
                batch.operationalPhase(),
                qualityStatus,
                batch.registeredAt(),
                visible(batch, requesterCompanyId, PrivacyField.RECEPTION_CHARACTERISTICS)
                        ? batch.receptionCharacteristics()
                        : null,
                batch.finalProduct(),
                ownBatch ? batch.buyerOrDistributor() : null,
                ownBatch ? batch.price() : null,
                ownBatch ? batch.currency() : null,
                ownBatch ? batch.commercialDate() : null,
                ownBatch ? batch.commercialReference() : null);
    }

    private boolean visible(Batch batch, UUID requesterCompanyId, PrivacyField field) {
        return batch.responsibleCompanyId().equals(requesterCompanyId)
                || privacyAccess.visibilityOf(batch.responsibleCompanyId(), field)
                        == PrivacyVisibility.SHARED;
    }
}
