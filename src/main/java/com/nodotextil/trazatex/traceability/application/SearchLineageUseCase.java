package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.application.port.PrivacySettingsPort;
import com.nodotextil.trazatex.traceability.application.view.LotView;

import java.util.List;
import java.util.UUID;

public class SearchLineageUseCase {

    private final BatchInfoPort batchInfo;
    private final PrivacySettingsPort privacySettings;

    public SearchLineageUseCase(BatchInfoPort batchInfo, PrivacySettingsPort privacySettings) {
        this.batchInfo = batchInfo;
        this.privacySettings = privacySettings;
    }

    public List<LotView> byQrCode(String qrCode, UUID requesterCompanyId) {
        return batchInfo.findByQrCode(qrCode)
                .map(lot -> LotViewAssembler.assemble(
                        lot,
                        requesterCompanyId,
                        privacySettings.sharedConfigurableFields(lot.responsibleCompanyId())))
                .stream()
                .toList();
    }
}
