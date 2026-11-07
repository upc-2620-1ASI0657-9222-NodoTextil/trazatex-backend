package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;

import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** La proyección en memoria: devuelve y guarda copias, como una base real (el servicio no puede depender de compartir instancias). */
public class InMemoryBatchProjections implements BatchProjectionRepositoryPort {

    private final Map<UUID, BatchProjection> rows = new HashMap<>();

    @Override
    public Optional<BatchProjection> findByIdForUpdate(UUID batchId) {
        return findById(batchId);
    }

    @Override
    public Optional<BatchProjection> findById(UUID batchId) {
        return Optional.ofNullable(rows.get(batchId)).map(InMemoryBatchProjections::copy);
    }

    @Override
    public PageResult<BatchProjection> findQualityPageByCompany(UUID companyId, PageRequest request) {
        var all = rows.values().stream().filter(b -> b.companyId().equals(companyId) && b.qualityStatus() != null)
                .sorted(Comparator.comparing(BatchProjection::batchId)).map(InMemoryBatchProjections::copy).toList();
        int from = (int) Math.min(request.offset(), all.size());
        return PageResult.of(all.subList(from, Math.min(all.size(), from + request.size())), request, all.size());
    }

    @Override
    public void insert(BatchProjection batch) {
        if (rows.containsKey(batch.batchId())) {
            throw new IllegalStateException("clave primaria duplicada: " + batch.batchId());
        }
        rows.put(batch.batchId(), copy(batch));
    }

    @Override
    public void update(BatchProjection batch) {
        if (!rows.containsKey(batch.batchId())) {
            throw new IllegalStateException("no existe: " + batch.batchId());
        }
        rows.put(batch.batchId(), copy(batch));
    }

    public int size() {
        return rows.size();
    }

    private static BatchProjection copy(BatchProjection b) {
        return BatchProjection.restore(b.batchId(), b.companyId(), b.phase(), b.qualityHold(), b.qualityStatus(), b.everControlled(), b.batchVersion(), b.updatedAt(), b.traceabilityId());
    }
}
