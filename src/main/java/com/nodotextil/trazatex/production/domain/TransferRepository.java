package com.nodotextil.trazatex.production.domain;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(UUID id);

    default Optional<Transfer> findByIdForUpdate(UUID id) {
        return findById(id);
    }
}
