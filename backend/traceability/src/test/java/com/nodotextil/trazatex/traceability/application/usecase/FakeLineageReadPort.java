package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;
import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransferRecord;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransformationData;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Un grafo en memoria con el contrato de {@link LineageReadPort} (sin filtrar, con incompletos), para probar el recorrido y la búsqueda
 * sin Neo4j. Cuenta las llamadas de nivel para comprobar que el recorrido no enumera caminos.
 */
final class FakeLineageReadPort implements LineageReadPort {

    static final Instant T0 = Instant.parse("2026-10-21T10:00:00Z");
    static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID COMPANY_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private record Split(UUID parent, UUID child) {
    }

    private record Mix(UUID transformation, List<UUID> inputs, List<UUID> outputs) {
    }

    final Map<UUID, BatchData> batches = new LinkedHashMap<>();
    final Map<UUID, TransformationData> transformations = new LinkedHashMap<>();
    final Map<UUID, List<TransferRecord>> transfers = new LinkedHashMap<>();
    final Map<UUID, String> names = new LinkedHashMap<>();
    final Map<UUID, CompanyPrivacy> privacy = new LinkedHashMap<>();
    private final List<Split> splits = new ArrayList<>();
    private final List<Mix> mixes = new ArrayList<>();
    int parentCalls;
    int childCalls;
    int batchLookups;

    static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("lote-" + name).getBytes(StandardCharsets.UTF_8));
    }

    // ---- construcción ----

    FakeLineageReadPort batch(String... names) {
        for (String name : names) {
            batch(name, COMPANY_A);
        }
        return this;
    }

    FakeLineageReadPort batch(String name, UUID company) {
        batches.put(id(name), new BatchData(id(name), "TT-" + name, "QR-" + name, company, "Algodón", BatchPhase.AVAILABLE, QualityStatus.NOT_REVIEWED, false, false,
                T0, "Proveedor " + name, "Piura", new BigDecimal("10.000"), List.of(new CompositionComponent("Algodón", new BigDecimal("100"))), "Seco", false));
        return this;
    }

    /** Un lote que un evento nombró pero cuya creación no ha llegado: solo su id. */
    FakeLineageReadPort provisional(String name) {
        batches.put(id(name), new BatchData(id(name), null, null, null, null, null, null, null, null, null, null, null, null, null, null, true));
        return this;
    }

    /** Cambia la fase de un lote ya creado. */
    FakeLineageReadPort phase(String name, BatchPhase phase) {
        BatchData b = batches.get(id(name));
        batches.put(id(name), new BatchData(b.batchId(), b.traceabilityId(), b.qrCode(), b.companyId(), b.materialType(), phase, b.qualityStatus(), b.qualityHold(), b.finalProduct(),
                b.createdAt(), b.supplierName(), b.origin(), b.quantityKg(), b.composition(), b.receptionCharacteristics(), b.provisional()));
        return this;
    }

    /** Cambia el estado de calidad y la retención de un lote ya creado. */
    FakeLineageReadPort quality(String name, QualityStatus status, boolean hold) {
        BatchData b = batches.get(id(name));
        batches.put(id(name), new BatchData(b.batchId(), b.traceabilityId(), b.qrCode(), b.companyId(), b.materialType(), b.phase(), status, hold, b.finalProduct(),
                b.createdAt(), b.supplierName(), b.origin(), b.quantityKg(), b.composition(), b.receptionCharacteristics(), b.provisional()));
        return this;
    }

    FakeLineageReadPort replace(String name, BatchData data) {
        batches.put(id(name), data);
        return this;
    }

    FakeLineageReadPort split(String parent, String... children) {
        for (String child : children) {
            splits.add(new Split(id(parent), id(child)));
        }
        return this;
    }

    FakeLineageReadPort transform(String name, List<String> inputs, List<String> outputs) {
        return transform(name, COMPANY_A, inputs, outputs, false);
    }

    FakeLineageReadPort transform(String name, UUID company, List<String> inputs, List<String> outputs, boolean provisional) {
        transformations.put(id(name), new TransformationData(id(name), provisional ? null : company, provisional ? null : TransformationType.DYEING,
                provisional ? null : T0, provisional ? null : T0.plusSeconds(3600), "Cardadora", "Cardado", new BigDecimal("3.000"), new BigDecimal("2.000"),
                "Orillo", provisional));
        mixes.add(new Mix(id(name), inputs.stream().map(FakeLineageReadPort::id).toList(), outputs.stream().map(FakeLineageReadPort::id).toList()));
        return this;
    }

    FakeLineageReadPort transferred(String batch, UUID toCompany, String transfer) {
        transfers.computeIfAbsent(id(batch), key -> new ArrayList<>()).add(new TransferRecord(id(transfer), id(batch), toCompany, T0.plusSeconds(120)));
        return this;
    }

    FakeLineageReadPort companyName(UUID company, String name) {
        names.put(company, name);
        return this;
    }

    /** Una cadena n0 → n1 → … → n{length-1} por divisiones. */
    FakeLineageReadPort chain(String prefix, int length) {
        for (int i = 0; i < length; i++) {
            batch(prefix + i);
            if (i > 0) {
                split(prefix + (i - 1), prefix + i);
            }
        }
        return this;
    }

    int levelCalls() {
        return parentCalls + childCalls;
    }

    // ---- LineageReadPort ----

    @Override
    public Optional<BatchData> findBatch(UUID batchId) {
        batchLookups++;
        return Optional.ofNullable(batches.get(batchId));
    }

    @Override
    public List<Link> parentsOf(Collection<UUID> batchIds) {
        parentCalls++;
        List<Link> links = new ArrayList<>();
        for (UUID frontier : batchIds) {
            splits.stream().filter(s -> s.child().equals(frontier)).forEach(s -> links.add(new Link(frontier, batches.get(s.parent()), null)));
            for (Mix mix : mixes) {
                if (mix.outputs().contains(frontier)) {
                    mix.inputs().forEach(input -> links.add(new Link(frontier, batches.get(input), transformations.get(mix.transformation()))));
                }
            }
        }
        return links;
    }

    @Override
    public List<Link> childrenOf(Collection<UUID> batchIds) {
        childCalls++;
        List<Link> links = new ArrayList<>();
        for (UUID frontier : batchIds) {
            splits.stream().filter(s -> s.parent().equals(frontier)).forEach(s -> links.add(new Link(frontier, batches.get(s.child()), null)));
            for (Mix mix : mixes) {
                if (mix.inputs().contains(frontier)) {
                    mix.outputs().forEach(output -> links.add(new Link(frontier, batches.get(output), transformations.get(mix.transformation()))));
                }
            }
        }
        return links;
    }

    @Override
    public Map<UUID, List<TransferRecord>> transfersOf(Collection<UUID> batchIds) {
        Map<UUID, List<TransferRecord>> result = new LinkedHashMap<>();
        batchIds.forEach(id -> {
            if (transfers.containsKey(id)) {
                result.put(id, transfers.get(id));
            }
        });
        return result;
    }

    @Override
    public Map<UUID, String> businessNames(Collection<UUID> companyIds) {
        Map<UUID, String> result = new LinkedHashMap<>();
        companyIds.forEach(id -> {
            if (names.containsKey(id)) {
                result.put(id, names.get(id));
            }
        });
        return result;
    }

    @Override
    public Map<UUID, CompanyPrivacy> privacyOf(Collection<UUID> companyIds) {
        Map<UUID, CompanyPrivacy> result = new LinkedHashMap<>();
        companyIds.forEach(id -> {
            if (privacy.containsKey(id)) {
                result.put(id, privacy.get(id));
            }
        });
        return result;
    }

    @Override
    public Optional<BatchData> findByQrCode(String qrCode) {
        return batches.values().stream().filter(b -> qrCode.equals(b.qrCode())).findFirst();
    }

    @Override
    public Optional<BatchData> findByTraceabilityId(String traceabilityId) {
        return batches.values().stream().filter(b -> traceabilityId.equals(b.traceabilityId())).findFirst();
    }

    @Override
    public BatchPage searchByTraceabilityPrefix(UUID companyId, String prefix, int offset, int limit) {
        List<BatchData> all = batches.values().stream().filter(BatchData::isComplete).filter(b -> b.companyId().equals(companyId))
                .filter(b -> b.traceabilityId().startsWith(prefix))
                .sorted(Comparator.comparing(BatchData::createdAt).reversed().thenComparing(BatchData::batchId)).toList();
        return new BatchPage(all.stream().skip(offset).limit(limit).toList(), all.size());
    }
}
