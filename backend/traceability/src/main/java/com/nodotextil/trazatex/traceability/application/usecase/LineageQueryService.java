package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import com.nodotextil.trazatex.traceability.application.port.in.QueryLineageUseCase;
import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort;
import com.nodotextil.trazatex.traceability.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;
import com.nodotextil.trazatex.traceability.domain.model.TraceabilityIdQuery;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.GraphEdge;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage.LeveledBatch;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage.LeveledTransformation;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransferRecord;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchPage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;
import com.nodotextil.trazatex.traceability.domain.service.LineageAuthorizationPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Consultas de genealogía (US30–US32, US36). Recorre el grafo con {@link LineageTraversal} y entrega el resultado a
 * {@link LineageAuthorizationPolicy}: nada sale de aquí sin haber pasado por la política. Lectura en una sola transacción.
 */
@Service
@Transactional(readOnly = true)
class LineageQueryService implements QueryLineageUseCase {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final LineageReadPort port;
    private final LineageAuthorizationPolicy policy;
    private final LineageTraversal traversal;

    LineageQueryService(LineageReadPort port, LineageAuthorizationPolicy policy) {
        this.port = port;
        this.policy = policy;
        this.traversal = new LineageTraversal(port);
    }

    @Override
    public VisibleLineage ancestors(UUID batchId, UUID requesterCompanyId) {
        return query(batchId, requesterCompanyId, LineageDirection.ANCESTORS);
    }

    @Override
    public VisibleLineage descendants(UUID batchId, UUID requesterCompanyId) {
        return query(batchId, requesterCompanyId, LineageDirection.DESCENDANTS);
    }

    @Override
    public VisibleLineage lineage(UUID batchId, UUID requesterCompanyId) {
        return query(batchId, requesterCompanyId, LineageDirection.BOTH);
    }

    private VisibleLineage query(UUID batchId, UUID requesterCompanyId, LineageDirection direction) {
        require(requesterCompanyId, "requesterCompanyId");
        // Desconocido e incompleto responden igual: no se revela que el lote existe
        BatchData start = port.findBatch(batchId).filter(BatchData::isComplete)
                .orElseThrow(() -> new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "El lote no existe."));
        Map<UUID, LeveledBatch> batches = new LinkedHashMap<>();
        batches.put(start.batchId(), new LeveledBatch(start, 0));
        Map<UUID, LeveledTransformation> transformations = new LinkedHashMap<>();
        Set<GraphEdge> edges = new LinkedHashSet<>();
        boolean complete = true;
        boolean truncated = false;
        if (direction != LineageDirection.DESCENDANTS) {
            LineageTraversal.Walk walk = traversal.walk(start, true);
            walk.batches().forEach(b -> batches.putIfAbsent(b.data().batchId(), b));
            walk.transformations().forEach(t -> transformations.putIfAbsent(t.data().transformationId(), t));
            edges.addAll(walk.edges());
            complete &= walk.complete();
            truncated |= walk.truncated();
        }
        if (direction != LineageDirection.ANCESTORS) {
            LineageTraversal.Walk walk = traversal.walk(start, false);
            walk.batches().forEach(b -> batches.putIfAbsent(b.data().batchId(), b));
            walk.transformations().forEach(t -> transformations.putIfAbsent(t.data().transformationId(), t));
            edges.addAll(walk.edges());
            complete &= walk.complete();
            truncated |= walk.truncated();
        }
        Map<UUID, List<TransferRecord>> transfers = direction == LineageDirection.BOTH ? port.transfersOf(batches.keySet()) : Map.of();
        Set<UUID> companies = new HashSet<>();
        batches.values().forEach(b -> companies.add(b.data().companyId()));
        transformations.values().forEach(t -> companies.add(t.data().companyId()));
        transfers.values().forEach(list -> list.forEach(t -> companies.add(t.toCompanyId())));
        // La privacidad que importa es la de las empresas AJENAS (la propia lo ve todo): una sola consulta, con la configuración vigente de cada una
        Set<UUID> foreign = new HashSet<>(companies);
        foreign.remove(requesterCompanyId);
        Lineage lineage = new Lineage(start.batchId(), direction, List.copyOf(batches.values()), List.copyOf(transformations.values()),
                List.copyOf(edges), transfers, port.businessNames(companies), port.privacyOf(foreign), complete, truncated);
        return policy.view(lineage, requesterCompanyId);
    }

    // ---- búsqueda ----

    @Override
    public VisibleBatchPage search(UUID requesterCompanyId, String qrCode, String traceabilityId, Integer page, Integer size) {
        require(requesterCompanyId, "requesterCompanyId");
        String qr = TraceabilityIdQuery.normalize(qrCode);
        String trace = TraceabilityIdQuery.normalize(traceabilityId);
        if (qr == null && trace == null) {
            throw new ValidationException(ErrorCodes.MISSING_SEARCH_CRITERIA, "Indique el código QR o el Traceability ID a buscar.",
                    List.of("qrCode", "traceabilityId"));
        }
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1) {
            throw new ValidationException(ErrorCodes.INVALID_PAGINATION, "page debe ser 0 o más y size 1 o más.", List.of("page", "size"));
        }
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        List<BatchData> items;
        long total;
        if (qr != null) {
            // Por QR: coincidencia exacta, de cualquier empresa; con el Traceability ID también, debe cumplirse la regla de arriba
            Optional<BatchData> found = port.findByQrCode(qr).filter(BatchData::isComplete)
                    .filter(batch -> trace == null || matchesTraceability(batch, trace, requesterCompanyId));
            total = found.isPresent() ? 1 : 0;
            items = pageNumber == 0 ? found.stream().toList() : List.of();
        } else {
            Optional<BatchData> foreign = port.findByTraceabilityId(trace).filter(BatchData::isComplete)
                    .filter(batch -> !batch.companyId().equals(requesterCompanyId));
            int foreignCount = foreign.isPresent() ? 1 : 0;
            long start = (long) pageNumber * pageSize;
            boolean foreignInPage = foreignCount == 1 && start == 0;
            int ownOffset = (int) Math.max(0, start - foreignCount);
            int ownLimit = pageSize - (foreignInPage ? 1 : 0);
            LineageReadPort.BatchPage own = port.searchByTraceabilityPrefix(requesterCompanyId, trace, ownOffset, ownLimit);
            List<BatchData> window = new ArrayList<>();
            if (foreignInPage) {
                window.add(foreign.get());
            }
            window.addAll(own.items());
            items = window;
            total = own.total() + foreignCount;
        }
        Map<UUID, String> names = port.businessNames(items.stream().map(BatchData::companyId).collect(java.util.stream.Collectors.toSet()));
        return policy.page(items, names, requesterCompanyId, pageNumber, pageSize, total);
    }

    /** Un lote de la propia empresa se encuentra por prefijo; uno de otra, solo por el Traceability ID exacto. */
    private static boolean matchesTraceability(BatchData batch, String normalized, UUID requesterCompanyId) {
        return batch.companyId().equals(requesterCompanyId) ? batch.traceabilityId().startsWith(normalized) : batch.traceabilityId().equals(normalized);
    }

    private static void require(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
    }
}
