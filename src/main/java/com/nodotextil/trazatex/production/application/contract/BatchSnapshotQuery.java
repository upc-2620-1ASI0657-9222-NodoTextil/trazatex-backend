package com.nodotextil.trazatex.production.application.contract;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchSnapshotQuery {

    Optional<BatchSnapshot> findById(UUID batchId);

    List<BatchSnapshot> findAllByIds(Collection<UUID> batchIds);

    Optional<BatchSnapshot> findByQrCode(String qrCode);
}
