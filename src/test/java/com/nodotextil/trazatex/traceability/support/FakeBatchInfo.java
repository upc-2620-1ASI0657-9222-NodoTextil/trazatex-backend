package com.nodotextil.trazatex.traceability.support;

import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.application.port.LotInfo;

import java.time.LocalDateTime;
import java.util.*;

public class FakeBatchInfo implements BatchInfoPort {

    private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 3, 20, 14, 0);

    private final Map<UUID, LotInfo> lots = new HashMap<>();
    private final UUID companyId = UUID.randomUUID();

    public void put(UUID batchId, String operationalPhase) {
        lots.put(batchId, new LotInfo(
                batchId,
                "TRZ-" + batchId,
                "QR-" + batchId,
                companyId,
                "YARN",
                operationalPhase,
                false,
                REGISTERED_AT,
                Map.of()));
    }

    @Override
    public Optional<LotInfo> findById(UUID batchId) {
        return Optional.ofNullable(lots.get(batchId));
    }

    @Override
    public Map<UUID, LotInfo> findAllByIds(Collection<UUID> batchIds) {
        Map<UUID, LotInfo> result = new HashMap<>();
        batchIds.forEach(id -> {
            if (lots.containsKey(id)) {
                result.put(id, lots.get(id));
            }
        });
        return result;
    }

    @Override
    public Optional<LotInfo> findByQrCode(String qrCode) {
        return lots.values().stream().filter(lot -> lot.qrCode().equals(qrCode)).findFirst();
    }
}
