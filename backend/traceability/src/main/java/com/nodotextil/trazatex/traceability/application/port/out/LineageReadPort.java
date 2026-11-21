package com.nodotextil.trazatex.traceability.application.port.out;

import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransferRecord;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransformationData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LineageReadPort {

    record Link(UUID frontierBatchId, BatchData neighbor, TransformationData via) {
    }

    record BatchPage(List<BatchData> items, long total) {
    }

    Optional<BatchData> findBatch(UUID batchId);

    List<Link> parentsOf(Collection<UUID> batchIds);

    List<Link> childrenOf(Collection<UUID> batchIds);

    Map<UUID, List<TransferRecord>> transfersOf(Collection<UUID> batchIds);

    Map<UUID, String> businessNames(Collection<UUID> companyIds);

    Map<UUID, CompanyPrivacy> privacyOf(Collection<UUID> companyIds);

    Optional<BatchData> findByQrCode(String qrCode);

    Optional<BatchData> findByTraceabilityId(String traceabilityId);

    BatchPage searchByTraceabilityPrefix(UUID companyId, String prefix, int offset, int limit);
}
