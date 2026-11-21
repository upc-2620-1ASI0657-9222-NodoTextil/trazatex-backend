package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.QrCode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BatchRepositoryPort {

    long save(Batch batch);

    default Map<UUID, Long> saveAll(Collection<Batch> batches) {
        Map<UUID, Long> versions = new LinkedHashMap<>();
        batches.forEach(batch -> versions.put(batch.id(), save(batch)));
        return versions;
    }

    Optional<Batch> findById(UUID id);

    Optional<Batch> findByIdForUpdate(UUID id);

    List<Batch> findAllForUpdate(Collection<UUID> ids);

    List<Batch> findAllById(Collection<UUID> ids);

    Optional<Batch> findByQrCode(QrCode qrCode);

    Page<Batch> search(BatchSearchCriteria criteria);

    boolean isCodeTaken(String code);
}
