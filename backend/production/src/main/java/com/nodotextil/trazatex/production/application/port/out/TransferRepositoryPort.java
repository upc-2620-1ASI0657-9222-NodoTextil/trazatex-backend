package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.Transfer;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepositoryPort {

    void save(Transfer transfer);

    Optional<Transfer> findById(UUID id);

    Optional<Transfer> findByIdForUpdate(UUID id);

    Page<Transfer> search(TransferSearchCriteria criteria);
}
