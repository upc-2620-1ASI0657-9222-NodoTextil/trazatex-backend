package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchPage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;

import java.util.UUID;

public interface QueryLineageUseCase {

    VisibleLineage ancestors(UUID batchId, UUID requesterCompanyId);

    VisibleLineage descendants(UUID batchId, UUID requesterCompanyId);

    VisibleLineage lineage(UUID batchId, UUID requesterCompanyId);

    VisibleBatchPage search(UUID requesterCompanyId, String qrCode, String traceabilityId, Integer page, Integer size);
}
