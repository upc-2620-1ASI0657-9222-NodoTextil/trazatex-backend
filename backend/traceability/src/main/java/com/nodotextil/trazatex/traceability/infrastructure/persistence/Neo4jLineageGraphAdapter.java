package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.application.port.out.LineageGraphPort;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El grafo en Neo4j con Cypher parametrizado. Todo es {@code MERGE} por la clave única de cada nodo: el resultado no depende
 * del orden de llegada de los eventos ni de que se repitan (el contrato está en {@link LineageGraphPort}). Los nodos y las
 * relaciones se crean por separado, nodo primero: un {@code MERGE} de un patrón completo crearía duplicados si el nodo ya existe.
 * Los lotes de un evento se procesan en orden de id para que dos consumidores no se bloqueen en orden cruzado.
 */
@Component
class Neo4jLineageGraphAdapter implements LineageGraphPort {

    private static final Logger log = LoggerFactory.getLogger(Neo4jLineageGraphAdapter.class);

    private enum Kind { TEXT, DECIMAL, DATETIME, BOOLEAN }

    /** Un componente de la composición tal como se guarda (JSON). */
    private record Part(String name, BigDecimal percentage) {
    }

    /** Un dato de identidad: nunca se sobrescribe, solo se completa. */
    private record Field(String name, Kind kind, Object value) {
    }

    private final Neo4jClient neo4j;
    private final ObjectMapper objectMapper;

    Neo4jLineageGraphAdapter(Neo4jClient neo4j, ObjectMapper objectMapper) {
        this.neo4j = neo4j;
        this.objectMapper = objectMapper;
    }

    // ---- BatchRegistered ----

    @Override
    public void registerBatch(RegisterBatchCommand c) {
        completeBatch(c.eventId(), c.batchId(), batchFields(c.traceabilityId(), c.qrCode(), c.materialType(), c.composition(),
                c.quantityKg(), c.origin(), c.supplierName(), c.receptionCharacteristics(), c.createdAt(), false, c.name()),
                c.companyId(), QualityStatus.NOT_REVIEWED);
    }

    // ---- BatchSplit ----

    @Override
    public void applyBatchSplit(ApplyBatchSplitCommand c) {
        placeholderBatch(c.parentBatchId());
        List<ApplyBatchSplitCommand.Child> children = new ArrayList<>(c.children());
        children.sort(Comparator.comparing(child -> child.batchId().toString()));
        for (ApplyBatchSplitCommand.Child child : children) {
            completeBatch(c.eventId(), child.batchId(), batchFields(child.traceabilityId(), child.qrCode(), child.materialType(),
                    child.composition(), child.quantityKg(), child.origin(), child.supplierName(), child.receptionCharacteristics(),
                    child.createdAt(), false, child.name()), c.companyId(), child.qualityStatus());
            neo4j.query("""
                            MATCH (p:Batch {batchId: $parent}), (b:Batch {batchId: $child})
                            MERGE (p)-[:SPLIT_INTO]->(b)
                            """)
                    .bind(c.parentBatchId().toString()).to("parent").bind(child.batchId().toString()).to("child").run();
        }
    }

    // ---- TransformationCompleted ----

    @Override
    public void applyTransformationCompleted(ApplyTransformationCompletedCommand c) {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("companyId", Kind.TEXT, c.companyId().toString()));
        fields.add(new Field("type", Kind.TEXT, c.type().name()));
        fields.add(new Field("startedAt", Kind.DATETIME, c.startedAt().toString()));
        fields.add(new Field("completedAt", Kind.DATETIME, c.completedAt().toString()));
        fields.add(new Field("machineName", Kind.TEXT, c.machineName()));
        fields.add(new Field("machineType", Kind.TEXT, c.machineType()));
        fields.add(new Field("shrinkageKg", Kind.DECIMAL, c.shrinkageKg().toPlainString()));
        fields.add(new Field("wasteKg", Kind.DECIMAL, c.wasteKg().toPlainString()));
        fields.add(new Field("wasteReason", Kind.TEXT, c.wasteReason()));
        Map<String, Object> stored = completeIdentity("Transformation", "transformationId", c.transformationId(), fields, "");
        logConflicts(c.eventId(), "Transformation", c.transformationId(), fields, stored);

        List<UUID> inputs = new ArrayList<>(c.inputBatchIds());
        inputs.sort(Comparator.comparing(UUID::toString));
        for (UUID input : inputs) {
            placeholderBatch(input);
            neo4j.query("""
                            MATCH (b:Batch {batchId: $batch}), (t:Transformation {transformationId: $transformation})
                            MERGE (b)-[:INPUT_OF]->(t)
                            """)
                    .bind(input.toString()).to("batch").bind(c.transformationId().toString()).to("transformation").run();
        }
        List<ApplyTransformationCompletedCommand.Output> outputs = new ArrayList<>(c.outputs());
        outputs.sort(Comparator.comparing(output -> output.batchId().toString()));
        for (ApplyTransformationCompletedCommand.Output output : outputs) {
            // una salida nace sin origen, proveedor ni recepción (decisión de 04a) y con la fecha en que terminó la transformación
            completeBatch(c.eventId(), output.batchId(), batchFields(output.traceabilityId(), output.qrCode(), output.materialType(),
                    output.composition(), output.quantityKg(), null, null, null, c.completedAt(), output.finalProduct(), output.name()),
                    c.companyId(), QualityStatus.NOT_REVIEWED);
            neo4j.query("""
                            MATCH (t:Transformation {transformationId: $transformation}), (b:Batch {batchId: $batch})
                            MERGE (t)-[:PRODUCED]->(b)
                            """)
                    .bind(c.transformationId().toString()).to("transformation").bind(output.batchId().toString()).to("batch").run();
        }
    }

    // ---- BatchPhaseChanged ----

    @Override
    public boolean applyBatchPhaseChanged(ApplyBatchPhaseChangedCommand c) {
        long applied = neo4j.query("""
                        MERGE (b:Batch {batchId: $batchId}) ON CREATE SET b.provisional = true
                        WITH b WHERE b.batchVersion IS NULL OR b.batchVersion < $version
                        SET b.phase = $phase, b.qualityHold = $hold, b.companyId = $companyId, b.batchVersion = $version
                        RETURN count(b) AS applied
                        """)
                .bind(c.batchId().toString()).to("batchId").bind(c.batchVersion()).to("version")
                .bind(c.newPhase().name()).to("phase").bind(c.qualityHold()).to("hold").bind(c.companyId().toString()).to("companyId")
                .fetch().one().map(row -> (Long) row.get("applied")).orElse(0L);
        if (applied == 0) {
            return false;
        }
        syncOwner(c.batchId());
        return true;
    }

    // ---- TransferAccepted ----

    @Override
    public void applyTransferAccepted(ApplyTransferAcceptedCommand c) {
        neo4j.query("MERGE (c:Company {companyId: $company})").bind(c.toCompanyId().toString()).to("company").run();
        List<UUID> batches = new ArrayList<>(c.batchIds());
        batches.sort(Comparator.comparing(UUID::toString));
        for (UUID batch : batches) {
            placeholderBatch(batch);
            neo4j.query("""
                            MATCH (b:Batch {batchId: $batch}), (c:Company {companyId: $company})
                            MERGE (b)-[r:TRANSFERRED_VIA {transferId: $transfer}]->(c)
                            ON CREATE SET r.at = datetime($at)
                            """)
                    .bind(batch.toString()).to("batch").bind(c.toCompanyId().toString()).to("company")
                    .bind(c.transferId().toString()).to("transfer").bind(c.acceptedAt().toString()).to("at").run();
        }
    }

    // ---- QualityStatusChanged ----

    @Override
    public void applyQualityStatus(ApplyQualityStatusCommand c) {
        neo4j.query("""
                        MERGE (b:Batch {batchId: $batchId}) ON CREATE SET b.provisional = true
                        SET b.qualityStatus = $status
                        """)
                .bind(c.batchId().toString()).to("batchId").bind(c.newStatus().name()).to("status").run();
    }

    // ---- CompanyRegistered ----

    @Override
    public void registerCompany(ApplyCompanyRegisteredCommand c) {
        // El nombre se completa si falta y nunca se pisa; solo se toca businessName, así que la privacidad (que puede haber llegado antes) queda intacta
        Object stored = neo4j.query("""
                        MERGE (c:Company {companyId: $company})
                        WITH c, c.businessName AS stored
                        SET c.businessName = coalesce(c.businessName, $name)
                        RETURN stored
                        """)
                .bind(c.companyId().toString()).to("company").bind(c.businessName()).to("name")
                .fetch().one().map(row -> row.get("stored")).orElse(null);
        if (stored != null && !stored.equals(c.businessName())) {
            log.error("Dato en conflicto, se conserva el guardado: eventId={} companyId={} campo=businessName", c.eventId(), c.companyId());
        }
    }

    // ---- CompanyPrivacyUpdated ----

    @Override
    public void saveCompanyPrivacy(UUID companyId, CompanyPrivacy privacy) {
        Map<String, String> asText = new LinkedHashMap<>();
        privacy.asMap().forEach((field, visibility) -> asText.put(field.name(), visibility.name()));
        neo4j.query("MERGE (c:Company {companyId: $company}) SET c.privacy = $privacy")
                .bind(companyId.toString()).to("company").bind(objectMapper.writeValueAsString(asText)).to("privacy").run();
    }

    // ---- piezas comunes ----

    private List<Field> batchFields(String traceabilityId, String qrCode, String materialType, List<CompositionComponent> composition,
                                    BigDecimal quantityKg, String origin, String supplierName, String receptionCharacteristics,
                                    Instant createdAt, boolean finalProduct, String name) {
        // Jackson escribe los componentes de un record siempre en el mismo orden: el mismo dato da siempre el mismo texto
        List<Part> parts = composition.stream().map(part -> new Part(part.name(), part.percentage())).toList();
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("name", Kind.TEXT, name));
        fields.add(new Field("traceabilityId", Kind.TEXT, traceabilityId));
        fields.add(new Field("qrCode", Kind.TEXT, qrCode));
        fields.add(new Field("materialType", Kind.TEXT, materialType));
        fields.add(new Field("composition", Kind.TEXT, objectMapper.writeValueAsString(parts)));
        fields.add(new Field("quantityKg", Kind.DECIMAL, quantityKg.toPlainString()));
        fields.add(new Field("origin", Kind.TEXT, origin));
        fields.add(new Field("supplierName", Kind.TEXT, supplierName));
        fields.add(new Field("receptionCharacteristics", Kind.TEXT, receptionCharacteristics));
        fields.add(new Field("createdAt", Kind.DATETIME, createdAt.toString()));
        fields.add(new Field("finalProduct", Kind.BOOLEAN, finalProduct));
        return fields;
    }

    /**
     * Completa un lote que nace (por {@code BatchRegistered}, {@code BatchSplit} o {@code TransformationCompleted}): los datos de
     * identidad solo si faltan y el estado inicial —{@code AVAILABLE}, sin retención, empresa del evento— solo si el nodo aún no
     * tiene {@code batchVersion}. El {@code qualityStatus} inicial, solo si aún no hay uno.
     */
    private void completeBatch(UUID eventId, UUID batchId, List<Field> fields, UUID companyId, QualityStatus initialQualityStatus) {
        String initialState = """
                , n.phase = CASE WHEN n.batchVersion IS NULL THEN $initialPhase ELSE n.phase END
                , n.qualityHold = CASE WHEN n.batchVersion IS NULL THEN false ELSE n.qualityHold END
                , n.companyId = CASE WHEN n.batchVersion IS NULL THEN $initialCompanyId ELSE n.companyId END
                , n.qualityStatus = coalesce(n.qualityStatus, $initialQualityStatus)
                """;
        Map<String, Object> extra = Map.of("initialPhase", BatchPhase.AVAILABLE.name(), "initialCompanyId", companyId.toString(),
                "initialQualityStatus", initialQualityStatus.name());
        Map<String, Object> stored = completeIdentity("Batch", "batchId", batchId, fields, initialState, extra);
        logConflicts(eventId, "Batch", batchId, fields, stored);
        syncOwner(batchId);
    }

    private Map<String, Object> completeIdentity(String label, String key, UUID id, List<Field> fields, String extraSet) {
        return completeIdentity(label, key, id, fields, extraSet, Map.of());
    }

    /**
     * {@code MERGE} del nodo y, en la misma sentencia, completa los datos que faltan con {@code coalesce(guardado, nuevo)}.
     * Devuelve lo que estaba guardado <b>antes</b> (una proyección tomada antes del {@code SET}) para detectar conflictos.
     */
    private Map<String, Object> completeIdentity(String label, String key, UUID id, List<Field> fields, String extraSet,
                                                 Map<String, Object> extraParams) {
        StringBuilder projection = new StringBuilder();
        StringBuilder sets = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>(extraParams);
        params.put("id", id.toString());
        for (Field field : fields) {
            projection.append(projection.length() == 0 ? "" : ", ").append('.').append(field.name());
            String parameter = "p_" + field.name();
            String incoming = field.kind() == Kind.DATETIME ? "datetime($" + parameter + ")" : "$" + parameter;
            sets.append(sets.length() == 0 ? "" : ", ").append("n.").append(field.name()).append(" = coalesce(n.")
                    .append(field.name()).append(", ").append(incoming).append(')');
            params.put(parameter, field.value());
        }
        String cypher = "MERGE (n:" + label + " {" + key + ": $id}) ON CREATE SET n.provisional = true\n"
                + "WITH n, n {" + projection + "} AS stored\n"
                + "SET " + sets + ", n.provisional = false\n" + extraSet.replace("\n, ", ", ") + "\n"
                + "RETURN stored";
        return neo4j.query(cypher).bindAll(params).fetch().one().map(row -> castMap(row.get("stored"))).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    /** Un dato ya guardado que el evento contradice: se conserva el guardado y se registra un ERROR (el evento no va a la DLT). */
    private void logConflicts(UUID eventId, String label, UUID id, List<Field> fields, Map<String, Object> stored) {
        for (Field field : fields) {
            Object before = stored.get(field.name());
            if (before != null && field.value() != null && !same(field, before)) {
                log.error("Dato en conflicto, se conserva el guardado: eventId={} {}Id={} campo={}", eventId, label.toLowerCase(), id, field.name());
            }
        }
    }

    private static boolean same(Field field, Object stored) {
        return switch (field.kind()) {
            case TEXT -> stored.toString().equals(field.value());
            case BOOLEAN -> stored.equals(field.value());
            case DECIMAL -> new BigDecimal(stored.toString()).compareTo(new BigDecimal(field.value().toString())) == 0;
            case DATETIME -> toInstant(stored).equals(Instant.parse(field.value().toString()));
        };
    }

    private static Instant toInstant(Object value) {
        if (value instanceof ZonedDateTime zoned) {
            return zoned.toInstant();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        return Instant.parse(value.toString());
    }

    /** Un lote que un evento nombra pero cuyo evento de creación aún no llegó: solo su id, marcado provisional. */
    private void placeholderBatch(UUID batchId) {
        neo4j.query("MERGE (b:Batch {batchId: $batchId}) ON CREATE SET b.provisional = true").bind(batchId.toString()).to("batchId").run();
    }

    /** {@code OWNS_BATCH} siempre sigue al {@code companyId} del nodo: una sola relación, desde su empresa actual. */
    private void syncOwner(UUID batchId) {
        neo4j.query("""
                        MATCH (b:Batch {batchId: $batchId}) WHERE b.companyId IS NOT NULL
                        OPTIONAL MATCH (:Company)-[stale:OWNS_BATCH]->(b) WHERE startNode(stale).companyId <> b.companyId
                        DELETE stale
                        WITH DISTINCT b
                        MERGE (c:Company {companyId: b.companyId})
                        MERGE (c)-[:OWNS_BATCH]->(b)
                        """)
                .bind(batchId.toString()).to("batchId").run();
    }
}
