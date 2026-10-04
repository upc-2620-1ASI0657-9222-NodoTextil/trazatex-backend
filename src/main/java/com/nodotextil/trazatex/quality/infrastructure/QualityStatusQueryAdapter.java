package com.nodotextil.trazatex.quality.infrastructure;

import com.nodotextil.trazatex.quality.application.contract.QualityStatusQuery;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.QualityStatus;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class QualityStatusQueryAdapter implements QualityStatusQuery {

    private final BatchQualityRepository repository;

    QualityStatusQueryAdapter(BatchQualityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<QualityStatus> findStatus(UUID batchId) {
        return repository.findByBatchId(batchId).map(BatchQuality::getStatus);
    }

    @Override
    public Map<UUID, QualityStatus> findStatuses(Collection<UUID> batchIds) {
        Map<UUID, QualityStatus> statuses = new LinkedHashMap<>();
        batchIds.forEach(batchId -> findStatus(batchId)
                .ifPresent(status -> statuses.put(batchId, status)));
        return statuses;
    }
}
