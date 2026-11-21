package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface BatchProjectionRepositoryPort {

    Optional<BatchProjection> findByIdForUpdate(UUID batchId);

    Optional<BatchProjection> findById(UUID batchId);

    PageResult<BatchProjection> findQualityPageByCompany(UUID companyId, PageRequest request);

    void insert(BatchProjection batch);

    void update(BatchProjection batch);
}
