package com.nodotextil.trazatex.traceability.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BatchInfoPort {

    Optional<LotInfo> findById(UUID batchId);

    Map<UUID, LotInfo> findAllByIds(Collection<UUID> batchIds);

    Optional<LotInfo> findByQrCode(String qrCode);
}
