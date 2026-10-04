package com.nodotextil.trazatex.quality.application.contract;

import com.nodotextil.trazatex.quality.domain.QualityStatus;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface QualityStatusQuery {

    Optional<QualityStatus> findStatus(UUID batchId);

    Map<UUID, QualityStatus> findStatuses(Collection<UUID> batchIds);
}
