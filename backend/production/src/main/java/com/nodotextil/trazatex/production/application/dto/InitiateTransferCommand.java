package com.nodotextil.trazatex.production.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InitiateTransferCommand(UUID targetCompanyId, List<UUID> batchIds, Map<UUID, BigDecimal> quantitiesKg) {

    public InitiateTransferCommand(UUID targetCompanyId, List<UUID> batchIds) {
        this(targetCompanyId, batchIds, null);
    }
}
