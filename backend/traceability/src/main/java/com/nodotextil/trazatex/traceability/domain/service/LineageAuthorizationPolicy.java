package com.nodotextil.trazatex.traceability.domain.service;

import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.ConfigurableField;
import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.GraphEdge;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage;
import com.nodotextil.trazatex.traceability.domain.model.graph.QualityTestValue;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransferRecord;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransformationData;
import com.nodotextil.trazatex.traceability.domain.model.view.Access;
import com.nodotextil.trazatex.traceability.domain.model.view.NodeKind;
import com.nodotextil.trazatex.traceability.domain.model.view.TransferEntry;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatch;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchPage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchSummary;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleEdge;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleNode;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleQualityTest;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleTransformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Privacidad B2B (ADR-006, RNF-07): <b>la única forma de convertir lo que hay en el grafo en algo que se puede devolver</b>. Es la única
 * clase que construye los tipos de {@code domain.model.view}; una regla de ArchUnit lo exige, de modo que ninguna respuesta de consulta
 * sale sin pasar por aquí. El filtro se aplica en el servidor, nunca en el frontend.
 * <ul>
 *   <li>Los nodos de la <b>propia</b> empresa salen completos (incluido {@code qualityHold}).</li>
 *   <li>De los nodos de <b>otra</b> empresa salen los campos siempre compartidos y, de los configurables, solo los que esa empresa marcó
 *       {@code SHARED}; lo que no se configuró es {@code PRIVATE}. {@code qualityHold} <b>nunca</b>.</li>
 *   <li>{@code name} (el nombre libre del lote) sale <b>solo</b> en los lotes de la propia empresa ({@code OWN}): no es un campo
 *       configurable, así que ninguna configuración de privacidad lo destapa en una vista {@code SHARED}.</li>
 *   <li>Las aristas y el historial de transferencias siempre salen.</li>
 * </ul>
 */
public final class LineageAuthorizationPolicy {

    public VisibleLineage view(Lineage lineage, UUID requesterCompanyId) {
        Map<UUID, String> names = lineage.companyNames();
        Map<UUID, CompanyPrivacy> privacy = lineage.companyPrivacy();
        List<VisibleNode> nodes = new ArrayList<>();
        // el nodo consultado primero (nivel 0), luego por nivel
        for (Lineage.LeveledBatch leveled : lineage.batches()) {
            // En la vista completa todo lote lleva su historial (vacío si nunca se transfirió); en las demás no hay historial: nulo y no sale en el JSON
            List<TransferEntry> history = lineage.direction() == LineageDirection.BOTH
                    ? transferEntries(lineage.transfers().getOrDefault(leveled.data().batchId(), List.of()), names) : null;
            nodes.add(batch(leveled.data(), leveled.level(), requesterCompanyId, names, privacy, history));
        }
        for (Lineage.LeveledTransformation leveled : lineage.transformations()) {
            nodes.add(transformation(leveled.data(), leveled.level(), requesterCompanyId, names, privacy));
        }
        List<VisibleEdge> edges = new ArrayList<>();
        for (GraphEdge edge : lineage.edges()) {
            edges.add(new VisibleEdge(edge.type(), edge.from(), edge.to()));
        }
        return new VisibleLineage(lineage.startBatchId(), lineage.direction(), Lineage.MAX_LEVELS, lineage.complete(), lineage.truncated(),
                List.copyOf(nodes), List.copyOf(edges));
    }

    public VisibleBatchSummary summary(BatchData batch, String businessName, UUID requesterCompanyId) {
        return new VisibleBatchSummary(batch.batchId(), batch.traceabilityId(), batch.qrCode(), batch.companyId(), businessName, batch.materialType(),
                batch.phase(), batch.qualityStatus(), batch.createdAt(), batch.finalProduct(), accessOf(batch.companyId(), requesterCompanyId));
    }

    /** Una página de resultados de búsqueda: cada lote pasa por {@link #summary}; la página la arma también la política, nunca el servicio. */
    public VisibleBatchPage page(List<BatchData> batches, Map<UUID, String> companyNames, UUID requesterCompanyId, int page, int size, long totalElements) {
        List<VisibleBatchSummary> items = batches.stream().map(batch -> summary(batch, companyNames.get(batch.companyId()), requesterCompanyId)).toList();
        return new VisibleBatchPage(items, page, size, totalElements, (int) ((totalElements + size - 1) / size));
    }

    private static Access accessOf(UUID ownerCompanyId, UUID requesterCompanyId) {
        return ownerCompanyId.equals(requesterCompanyId) ? Access.OWN : Access.SHARED;
    }

    /** Lo que la empresa dueña tiene configurado AHORA (el mapa que llegó en su último {@code CompanyPrivacyUpdated}): vale para todos sus nodos, también los antiguos. Sin configuración, todo es privado. */
    private static CompanyPrivacy privacyOf(Map<UUID, CompanyPrivacy> privacy, UUID owner) {
        return privacy.getOrDefault(owner, CompanyPrivacy.ALL_PRIVATE);
    }

    private static boolean canSee(Map<UUID, CompanyPrivacy> privacy, UUID ownerCompanyId, UUID requesterCompanyId, ConfigurableField field) {
        return ownerCompanyId.equals(requesterCompanyId) || privacyOf(privacy, ownerCompanyId).isShared(field);
    }

    private static VisibleBatch batch(BatchData data, int level, UUID requester, Map<UUID, String> names, Map<UUID, CompanyPrivacy> privacy,
                                      List<TransferEntry> transferHistory) {
        UUID owner = data.companyId();
        boolean own = owner.equals(requester);
        CompanyPrivacy shared = own ? null : privacyOf(privacy, owner);
        boolean supplier = own || shared.isShared(ConfigurableField.SUPPLIER);
        boolean origin = own || shared.isShared(ConfigurableField.ORIGIN);
        boolean quantity = own || shared.isShared(ConfigurableField.QUANTITY);
        boolean composition = own || shared.isShared(ConfigurableField.COMPOSITION);
        boolean reception = own || shared.isShared(ConfigurableField.RECEPTION_CHARACTERISTICS);
        return new VisibleBatch(data.batchId(), NodeKind.BATCH, level, accessOf(owner, requester), owner, names.get(owner), data.traceabilityId(),
                data.qrCode(), data.materialType(), data.phase(), data.qualityStatus(), data.createdAt(), data.finalProduct(),
                supplier ? data.supplierName() : null, origin ? data.origin() : null, quantity ? data.quantityKg() : null,
                composition ? data.composition() : null, reception ? data.receptionCharacteristics() : null,
                own ? Boolean.TRUE.equals(data.qualityHold()) : null, transferHistory,
                canSee(privacy,owner,requester,ConfigurableField.FAILURE_CAUSE) ? data.failureCause() : null,
                canSee(privacy,owner,requester,ConfigurableField.QUALITY_TEST_VALUES) ? qualityTests(data.qualityTests()) : null,
                own ? data.name() : null);
    }

    private static List<VisibleQualityTest> qualityTests(List<QualityTestValue> tests) {
        return tests == null ? null : tests.stream()
                .map(t -> new VisibleQualityTest(t.criterionName(), t.expectedValue(), t.obtainedValue(), t.unit(), t.testResult())).toList();
    }

    private static VisibleTransformation transformation(TransformationData data, int level, UUID requester, Map<UUID, String> names,
                                                        Map<UUID, CompanyPrivacy> privacy) {
        UUID owner = data.companyId();
        return new VisibleTransformation(data.transformationId(), NodeKind.TRANSFORMATION, level, accessOf(owner, requester), owner, names.get(owner),
                data.type(), data.startedAt(), data.completedAt(),
                canSee(privacy, owner, requester, ConfigurableField.MACHINERY) ? data.machineName() : null,
                canSee(privacy, owner, requester, ConfigurableField.MACHINERY) ? data.machineType() : null,
                canSee(privacy, owner, requester, ConfigurableField.SHRINKAGE) ? data.shrinkageKg() : null,
                canSee(privacy, owner, requester, ConfigurableField.WASTE) ? data.wasteKg() : null,
                canSee(privacy, owner, requester, ConfigurableField.WASTE_REASON) ? data.wasteReason() : null);
    }

    private static List<TransferEntry> transferEntries(List<TransferRecord> transfers, Map<UUID, String> names) {
        return transfers.stream().map(t -> new TransferEntry(t.transferId(), t.toCompanyId(), names.get(t.toCompanyId()), t.at())).toList();
    }
}
