package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class BatchLocks {

    private final BatchRepositoryPort batches;

    BatchLocks(BatchRepositoryPort batches) {
        this.batches = batches;
    }

    List<Batch> lockOwned(List<UUID> ids, UUID companyId) {
        List<Batch> locked = lock(ids);
        for (Batch batch : locked) {
            if (!batch.companyId().equals(companyId)) {
                throw notFound();
            }
        }
        return locked;
    }

    List<Batch> lock(List<UUID> ids) {
        Map<UUID, Batch> byId = batches.findAllForUpdate(ids).stream().collect(Collectors.toMap(Batch::id, Function.identity()));
        return ids.stream().map(id -> {
            Batch batch = byId.get(id);
            if (batch == null) {
                throw notFound();
            }
            return batch;
        }).toList();
    }

    static List<UUID> requireDistinct(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(ErrorCodes.NO_BATCHES_SELECTED, "Seleccione al menos un lote.", null);
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (id == null) {
                throw new ValidationException(ErrorCodes.BATCH_NOT_FOUND, "Hay un identificador de lote vacío.", null);
            }
            if (!seen.add(id)) {
                throw new ValidationException(ErrorCodes.DUPLICATE_BATCH, "Un mismo lote está repetido en la selección.", null);
            }
        }
        return ids;
    }

    private static NotFoundException notFound() {
        return new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "Uno de los lotes no existe.");
    }
}
