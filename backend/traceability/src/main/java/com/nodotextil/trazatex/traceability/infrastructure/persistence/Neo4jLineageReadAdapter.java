package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransferRecord;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransformationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura del grafo para las consultas (ver {@link LineageQueries}: texto fijo y todo por parámetro). Devuelve datos <b>sin filtrar</b>, también
 * los incompletos: decidir qué se puede devolver es de {@code LineageAuthorizationPolicy}. Un dato que no se puede leer (una fase o un tipo
 * desconocido, una composición ilegible) llega nulo y el nodo se trata como incompleto: ante la duda, no se muestra.
 */
@Component
class Neo4jLineageReadAdapter implements LineageReadPort {

    private static final Logger log = LoggerFactory.getLogger(Neo4jLineageReadAdapter.class);

    private final Neo4jClient neo4j;
    private final ObjectMapper objectMapper;

    Neo4jLineageReadAdapter(Neo4jClient neo4j, ObjectMapper objectMapper) {
        this.neo4j = neo4j;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<BatchData> findBatch(UUID batchId) {
        return neo4j.query(LineageQueries.FIND_BATCH).bind(batchId.toString()).to("id").fetch().one().map(row -> batch(map(row.get("batch"))));
    }

    @Override
    public List<Link> parentsOf(Collection<UUID> batchIds) {
        List<Link> links = new ArrayList<>(links(LineageQueries.PARENTS_BY_SPLIT, batchIds));
        links.addAll(links(LineageQueries.PARENTS_BY_TRANSFORMATION, batchIds));
        return links;
    }

    @Override
    public List<Link> childrenOf(Collection<UUID> batchIds) {
        List<Link> links = new ArrayList<>(links(LineageQueries.CHILDREN_BY_SPLIT, batchIds));
        links.addAll(links(LineageQueries.CHILDREN_BY_TRANSFORMATION, batchIds));
        return links;
    }

    private List<Link> links(String query, Collection<UUID> frontier) {
        if (frontier.isEmpty()) {
            return List.of();
        }
        return neo4j.query(query).bind(ids(frontier)).to("ids").fetch().all().stream().map(row -> new Link(UUID.fromString((String) row.get("frontier")),
                batch(map(row.get("neighbor"))), row.get("via") == null ? null : transformation(map(row.get("via"))))).toList();
    }

    @Override
    public Map<UUID, List<TransferRecord>> transfersOf(Collection<UUID> batchIds) {
        Map<UUID, List<TransferRecord>> result = new LinkedHashMap<>();
        if (batchIds.isEmpty()) {
            return result;
        }
        neo4j.query(LineageQueries.TRANSFERS).bind(ids(batchIds)).to("ids").fetch().all().forEach(row -> {
            UUID batch = UUID.fromString((String) row.get("batchId"));
            result.computeIfAbsent(batch, key -> new ArrayList<>()).add(new TransferRecord(UUID.fromString((String) row.get("transferId")), batch,
                    UUID.fromString((String) row.get("toCompanyId")), instant(row.get("at"))));
        });
        return result;
    }

    @Override
    public Map<UUID, String> businessNames(Collection<UUID> companyIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        if (companyIds.isEmpty()) {
            return names;
        }
        neo4j.query(LineageQueries.BUSINESS_NAMES).bind(ids(companyIds)).to("ids").fetch().all()
                .forEach(row -> names.put(UUID.fromString((String) row.get("companyId")), (String) row.get("businessName")));
        return names;
    }

    @Override
    public Map<UUID, CompanyPrivacy> privacyOf(Collection<UUID> companyIds) {
        Map<UUID, CompanyPrivacy> privacy = new LinkedHashMap<>();
        if (companyIds.isEmpty()) {
            return privacy;
        }
        neo4j.query(LineageQueries.COMPANY_PRIVACY).bind(ids(companyIds)).to("ids").fetch().all().forEach(row -> {
            UUID company = UUID.fromString((String) row.get("companyId"));
            try {
                Map<String, String> raw = objectMapper.readValue((String) row.get("privacy"), new TypeReference<Map<String, String>>() {
                });
                CompanyPrivacy.Parsed parsed = CompanyPrivacy.fromRaw(raw);
                parsed.warnings().forEach(warning -> log.warn("Privacidad de la empresa {}: {}", company, warning));
                privacy.put(company, parsed.privacy());
            } catch (RuntimeException e) {
                // Lo que no se puede leer no se comparte: sin configuración legible, todo PRIVATE
                log.warn("La privacidad guardada de la empresa {} no se puede leer: se trata como todo PRIVATE ({})", company, e.toString());
            }
        });
        return privacy;
    }

    @Override
    public Optional<BatchData> findByQrCode(String qrCode) {
        return neo4j.query(LineageQueries.FIND_BY_QR_CODE).bind(qrCode).to("qr").fetch().one().map(row -> batch(map(row.get("batch"))));
    }

    @Override
    public Optional<BatchData> findByTraceabilityId(String traceabilityId) {
        return neo4j.query(LineageQueries.FIND_BY_TRACEABILITY_ID).bind(traceabilityId).to("traceabilityId").fetch().one().map(row -> batch(map(row.get("batch"))));
    }

    @Override
    public BatchPage searchByTraceabilityPrefix(UUID companyId, String prefix, int offset, int limit) {
        Map<String, Object> parameters = Map.of("company", companyId.toString(), "prefix", prefix, "offset", (long) offset, "limit", (long) limit);
        long total = (Long) neo4j.query(LineageQueries.COUNT_BY_PREFIX).bindAll(parameters).fetch().one().orElseThrow().get("total");
        if (limit == 0 || total == 0) {
            return new BatchPage(List.of(), total);
        }
        List<BatchData> items = neo4j.query(LineageQueries.SEARCH_BY_PREFIX).bindAll(parameters).fetch().all().stream().map(row -> batch(map(row.get("batch")))).toList();
        return new BatchPage(items, total);
    }

    // ---- del nodo a los datos ----

    private static List<String> ids(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private BatchData batch(Map<String, Object> p) {
        return new BatchData(UUID.fromString((String) p.get("batchId")), (String) p.get("traceabilityId"), (String) p.get("qrCode"), uuid(p.get("companyId")),
                (String) p.get("materialType"), parse(BatchPhase.class, p.get("phase")), parse(QualityStatus.class, p.get("qualityStatus")), (Boolean) p.get("qualityHold"),
                (Boolean) p.get("finalProduct"), instant(p.get("createdAt")), (String) p.get("supplierName"), (String) p.get("origin"), decimal(p.get("quantityKg")),
                composition(p.get("composition")), (String) p.get("receptionCharacteristics"), Boolean.TRUE.equals(p.get("provisional")),
                (String)p.get("failureCause"), qualityTests(p.get("qualityTests")), (String) p.get("name"));
    }

    private List<com.nodotextil.trazatex.traceability.domain.model.graph.QualityTestValue> qualityTests(Object value) {
        if (value == null) return List.of();
        return objectMapper.readValue((String)value,new TypeReference<List<com.nodotextil.trazatex.traceability.domain.model.graph.QualityTestValue>>() { });
    }

    private TransformationData transformation(Map<String, Object> p) {
        return new TransformationData(UUID.fromString((String) p.get("transformationId")), uuid(p.get("companyId")), parse(TransformationType.class, p.get("type")),
                instant(p.get("startedAt")), instant(p.get("completedAt")), (String) p.get("machineName"), (String) p.get("machineType"), decimal(p.get("shrinkageKg")),
                decimal(p.get("wasteKg")), (String) p.get("wasteReason"), Boolean.TRUE.equals(p.get("provisional")));
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString((String) value);
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal((String) value);
    }

    private static Instant instant(Object value) {
        if (value instanceof ZonedDateTime zoned) {
            return zoned.toInstant();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        return null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, (String) value);
        } catch (IllegalArgumentException e) {
            log.warn("Valor desconocido de {} en el grafo: el nodo se trata como incompleto", type.getSimpleName());
            return null;
        }
    }

    private List<CompositionComponent> composition(Object json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue((String) json, new TypeReference<List<Part>>() { }).stream().map(part -> new CompositionComponent(part.name(), part.percentage())).toList();
        } catch (RuntimeException e) {
            log.warn("La composición guardada de un lote no se puede leer: se omite");
            return null;
        }
    }

    private record Part(String name, BigDecimal percentage) {
    }
}
