package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.application.port.PrivacySettingsPort;
import com.nodotextil.trazatex.traceability.application.view.LotView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

public class SearchLineageUseCase {

    private final BatchInfoPort batchInfo;
    private final PrivacySettingsPort privacySettings;
    private final OrganizationAccess organizationAccess;

    public SearchLineageUseCase(
            BatchInfoPort batchInfo,
            PrivacySettingsPort privacySettings,
            OrganizationAccess organizationAccess) {
        this.batchInfo = batchInfo;
        this.privacySettings = privacySettings;
        this.organizationAccess = organizationAccess;
    }

    public List<LotView> byQrCode(String qrCode, UUID requesterCompanyId) {
        return batchInfo.findByQrCode(qrCode)
                .map(lot -> {
                    if (requesterCompanyId == null
                            || !organizationAccess.companiesShareLicense(
                                    requesterCompanyId, lot.responsibleCompanyId())) {
                        throw new AccessDeniedException("The batch does not belong to your traceability chain");
                    }
                    return LotViewAssembler.assemble(
                            lot,
                            requesterCompanyId,
                            privacySettings.sharedConfigurableFields(lot.responsibleCompanyId()));
                })
                .stream()
                .toList();
    }
}
