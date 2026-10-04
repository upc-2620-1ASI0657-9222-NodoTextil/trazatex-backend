package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.traceability.application.port.LotInfo;
import com.nodotextil.trazatex.traceability.application.view.LotView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LotViewAssembler {

    private LotViewAssembler() {
    }

    public static LotView assemble(LotInfo lot, UUID requesterCompanyId, Set<String> sharedFields) {
        boolean ownLot = lot.responsibleCompanyId().equals(requesterCompanyId);
        Map<String, Object> visibleData = new LinkedHashMap<>();
        lot.configurableData().forEach((field, value) -> {
            if (ownLot || sharedFields.contains(field)) {
                visibleData.put(field, value);
            }
        });

        return new LotView(
                lot.batchId(),
                lot.traceabilityId(),
                lot.responsibleCompanyId(),
                lot.materialType(),
                lot.operationalPhase(),
                lot.finalProduct(),
                lot.registeredAt(),
                visibleData);
    }
}
