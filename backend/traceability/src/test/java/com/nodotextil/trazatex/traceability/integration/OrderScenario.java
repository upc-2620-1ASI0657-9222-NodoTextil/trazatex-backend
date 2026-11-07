package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyPrivacyCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Un caso con una división, una transformación y una transferencia, repartido en flujos de eventos (cada flujo es un tópico y
 * una clave de Kafka: su orden interno se conserva, entre flujos no hay orden) y con el grafo que debe quedar escrito a mano.
 * Cada corrida usa ids propios (derivados de su número) para poder ejecutarse en paralelo sin tocar los nodos de otra.
 *
 * <p>El caso pequeño ({@link #small}) tiene 8 eventos: se recorren todas sus mezclas. El grande ({@link #large}) tiene 19, con
 * un lote registrado que se mezcla en la transformación, estados de calidad, privacidad y una segunda transformación
 * encadenada: se recorren mezclas al azar de semilla fija.
 */
final class OrderScenario {

    /** Una entrega de un evento: se aplica por el manejador idempotente, como lo haría su consumidor. */
    record Ev(String name, String stream, UUID eventId, Runnable deliver) {
    }

    /** Beans con los que se aplican los eventos. */
    record Beans(IdempotentEventHandler handler, RegisterBatchUseCase register, ApplyBatchSplitUseCase split,
                 ApplyTransformationCompletedUseCase transformation, ApplyBatchPhaseChangedUseCase phase,
                 ApplyTransferAcceptedUseCase transfer, ApplyQualityStatusUseCase quality, ApplyCompanyPrivacyUseCase privacy) {
    }

    /** Una corrida lista para ejecutarse: sus eventos planos (los flujos uno tras otro), el grafo esperado y sus ids. */
    record Instance(List<String> streamNames, int[] streamLengths, List<Ev> events, List<String> expected, List<UUID> entityIds,
                    Map<String, String> names, Map<String, EventMessages.Message> messages) {

        List<UUID> eventIds() {
            return events.stream().map(Ev::eventId).toList();
        }

        String describe(int[] order) {
            StringBuilder text = new StringBuilder();
            for (int index : order) {
                text.append(text.length() == 0 ? "" : " > ").append(events.get(index).name());
            }
            return text.toString();
        }
    }

    /** Los órdenes difíciles del caso pequeño, por nombre de evento; los comparten la prueba directa y la de Kafka real. */
    static final List<List<String>> NAMED_ORDERS = List.of(
            // el orden en que ocurrió
            List.of("BR(L1)", "BS(L1>H1,H2)", "BPC(L1 SPLIT v1)", "BPC(H1 IN_TRANSFORMATION v1)", "BPC(H1 PROCESSED v2)", "TC(T1)",
                    "BPC(H2 AVAILABLE cB v2)", "TA(TR1>cB)"),
            // el primer cambio de fase del hijo antes que el BatchSplit que lo crea
            List.of("BPC(H1 IN_TRANSFORMATION v1)", "BS(L1>H1,H2)", "BR(L1)", "BPC(H1 PROCESSED v2)", "BPC(L1 SPLIT v1)", "TC(T1)",
                    "BPC(H2 AVAILABLE cB v2)", "TA(TR1>cB)"),
            // el hijo ya transferido a B: el BatchSplit tardío no le devuelve la empresa A
            List.of("BPC(H2 AVAILABLE cB v2)", "TA(TR1>cB)", "BR(L1)", "BS(L1>H1,H2)", "BPC(L1 SPLIT v1)", "BPC(H1 IN_TRANSFORMATION v1)",
                    "BPC(H1 PROCESSED v2)", "TC(T1)"),
            // la transformación y la transferencia antes que todo lo demás
            List.of("TC(T1)", "TA(TR1>cB)", "BPC(H1 PROCESSED v2)", "BPC(H2 AVAILABLE cB v2)", "BS(L1>H1,H2)", "BPC(H1 IN_TRANSFORMATION v1)",
                    "BPC(L1 SPLIT v1)", "BR(L1)"),
            // los cambios de fase del padre antes de que exista, y el registro al final
            List.of("BPC(L1 SPLIT v1)", "BPC(H1 PROCESSED v2)", "BPC(H2 AVAILABLE cB v2)", "BPC(H1 IN_TRANSFORMATION v1)", "TC(T1)",
                    "TA(TR1>cB)", "BS(L1>H1,H2)", "BR(L1)"));

    static final Instant T0 = Instant.parse("2026-10-21T10:00:00Z");
    private static final List<CompositionComponent> COTTON = List.of(new CompositionComponent("Algodón", new BigDecimal("100")));
    private static final String COTTON_JSON = "[{\"name\":\"Algodón\",\"percentage\":100.00}]";

    private final int run;
    private final Beans beans;
    private final boolean large;
    private final Map<String, UUID> ids = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Map<String, List<Ev>> streams = new LinkedHashMap<>();
    private final List<String> expected = new ArrayList<>();
    private final Map<String, EventMessages.Message> messages = new LinkedHashMap<>();

    private OrderScenario(int run, Beans beans, boolean large) {
        this.run = run;
        this.beans = beans;
        this.large = large;
    }

    static Instance small(int run, Beans beans) {
        return new OrderScenario(run, beans, false).build();
    }

    static Instance large(int run, Beans beans) {
        return new OrderScenario(run, beans, true).build();
    }

    // ---- ids ----

    private UUID id(String name) {
        return ids.computeIfAbsent(name, key -> {
            UUID id = UUID.nameUUIDFromBytes(("orden-" + run + "-" + key).getBytes(StandardCharsets.UTF_8));
            names.put(id.toString(), key);
            return id;
        });
    }

    private String trace(String name) {
        return "TT-" + run + "-" + name;
    }

    private String qr(String name) {
        return "QR-" + run + "-" + name;
    }

    private void add(String stream, String name, Consumer<UUID> apply) {
        UUID eventId = UUID.nameUUIDFromBytes(("evento-" + run + "-" + name).getBytes(StandardCharsets.UTF_8));
        BaseDomainEvent envelope = new BaseDomainEvent(eventId, "Prueba", name, 1, T0, Map.of());
        streams.computeIfAbsent(stream, key -> new ArrayList<>())
                .add(new Ev(name, stream, eventId, () -> beans.handler().handle(envelope, e -> apply.accept(eventId))));
    }

    /** El mismo evento como lo publica Kafka (solo el caso pequeño lo necesita). */
    private void message(String name, java.util.function.Function<UUID, EventMessages.Message> message) {
        messages.put(name, message.apply(UUID.nameUUIDFromBytes(("evento-" + run + "-" + name).getBytes(StandardCharsets.UTF_8))));
    }

    // ---- el caso ----

    private Instance build() {
        UUID cA = id("cA");
        UUID cB = id("cB");
        UUID l1 = id("L1");
        UUID h1 = id("H1");
        UUID h2 = id("H2");
        UUID s = id("S");
        UUID t1 = id("T1");
        UUID transfer = id("TR1");
        UUID l2 = id("L2");
        UUID s2 = id("S2");
        UUID t2 = id("T2");

        // production.batch / L1
        add("batch/L1", "BR(L1)", e -> beans.register().apply(new RegisterBatchCommand(e, l1, trace("L1"), qr("L1"), cA, "Algodón", COTTON,
                new BigDecimal("100"), "Piura", "Textiles del Norte", "Fibra limpia", T0)));
        add("batch/L1", "BS(L1>H1,H2)", e -> beans.split().apply(new ApplyBatchSplitCommand(e, l1, cA, List.of(
                child(h1, "H1", "40"), child(h2, "H2", "60")))));
        add("batch/L1", "BPC(L1 SPLIT v1)", e -> beans.phase().apply(new ApplyBatchPhaseChangedCommand(e, l1, cA, BatchPhase.SPLIT, false, 1L)));
        // production.batch / H1 (el primer cambio de fase del hijo puede llegar antes que el BatchSplit que lo crea)
        add("batch/H1", "BPC(H1 IN_TRANSFORMATION v1)", e -> beans.phase().apply(
                new ApplyBatchPhaseChangedCommand(e, h1, cA, BatchPhase.IN_TRANSFORMATION, false, 1L)));
        add("batch/H1", "BPC(H1 PROCESSED v2)", e -> beans.phase().apply(new ApplyBatchPhaseChangedCommand(e, h1, cA, BatchPhase.PROCESSED, false, 2L)));
        // production.batch / H2: la aceptación de la transferencia le cambia el dueño (la versión puede saltar)
        add("batch/H2", "BPC(H2 AVAILABLE cB v2)", e -> beans.phase().apply(new ApplyBatchPhaseChangedCommand(e, h2, cB, BatchPhase.AVAILABLE, false, 2L)));
        // production.transformation / T1
        List<UUID> inputs = large ? List.of(l2, h1) : List.of(h1);
        String outputKg = large ? "80" : "35";
        add("transformation/T1", "TC(T1)", e -> beans.transformation().apply(new ApplyTransformationCompletedCommand(e, t1, TransformationType.DYEING, cA,
                "Cardadora", "Cardado", inputs, List.of(new ApplyTransformationCompletedCommand.Output(s, trace("S"), qr("S"), "Tela teñida",
                new BigDecimal(outputKg), COTTON, true)), new BigDecimal(large ? "5" : "2"), "Orillo", new BigDecimal(large ? "5" : "3"),
                T0, T0.plusSeconds(3600))));
        // production.transfer / TR1
        add("transfer/TR1", "TA(TR1>cB)", e -> beans.transfer().apply(new ApplyTransferAcceptedCommand(e, transfer, List.of(h2), cB, T0.plusSeconds(120))));

        if (!large) {
            message("BR(L1)", e -> EventMessages.batchRegistered(e, l1, trace("L1"), qr("L1"), cA, "Algodón", 100, "Piura", "Textiles del Norte", "Fibra limpia", T0));
            message("BS(L1>H1,H2)", e -> EventMessages.batchSplit(e, l1, cA, List.of(
                    EventMessages.splitChild(h1, trace("H1"), qr("H1"), "Algodón", 40, "Piura", "Textiles del Norte", "Fibra limpia", "NOT_REVIEWED", T0.plusSeconds(60)),
                    EventMessages.splitChild(h2, trace("H2"), qr("H2"), "Algodón", 60, "Piura", "Textiles del Norte", "Fibra limpia", "NOT_REVIEWED", T0.plusSeconds(60)))));
            message("BPC(L1 SPLIT v1)", e -> EventMessages.phaseChanged(e, l1, cA, "AVAILABLE", "SPLIT", false, 1));
            message("BPC(H1 IN_TRANSFORMATION v1)", e -> EventMessages.phaseChanged(e, h1, cA, "AVAILABLE", "IN_TRANSFORMATION", false, 1));
            message("BPC(H1 PROCESSED v2)", e -> EventMessages.phaseChanged(e, h1, cA, "IN_TRANSFORMATION", "PROCESSED", false, 2));
            message("BPC(H2 AVAILABLE cB v2)", e -> EventMessages.phaseChanged(e, h2, cB, "IN_TRANSFER", "AVAILABLE", false, 2));
            message("TC(T1)", e -> EventMessages.transformationCompleted(e, t1, "DYEING", cA, "Cardadora", "Cardado", List.of(h1), s, trace("S"), qr("S"),
                    "Tela teñida", 35, true, 2, "Orillo", 3, T0, T0.plusSeconds(3600)));
            message("TA(TR1>cB)", e -> EventMessages.transferAccepted(e, transfer, List.of(h2), cA, cB, T0.plusSeconds(120)));
        }
        if (large) {
            // un lote registrado que se mezcla en la transformación, con sus dos cambios de fase
            add("batch/L2", "BR(L2)", e -> beans.register().apply(new RegisterBatchCommand(e, l2, trace("L2"), qr("L2"), cA, "Algodón", COTTON,
                    new BigDecimal("50"), "Piura", "Textiles del Norte", "Fibra limpia", T0)));
            add("batch/L2", "BPC(L2 IN_TRANSFORMATION v1)", e -> beans.phase().apply(
                    new ApplyBatchPhaseChangedCommand(e, l2, cA, BatchPhase.IN_TRANSFORMATION, false, 1L)));
            add("batch/L2", "BPC(L2 PROCESSED v2)", e -> beans.phase().apply(new ApplyBatchPhaseChangedCommand(e, l2, cA, BatchPhase.PROCESSED, false, 2L)));
            // quality.control / H1: dos estados seguidos; el último es el que queda
            add("quality/H1", "QSC(H1 CONFORMING)", e -> beans.quality().apply(new ApplyQualityStatusCommand(e, h1, QualityStatus.CONFORMING)));
            add("quality/H1", "QSC(H1 FAILED)", e -> beans.quality().apply(new ApplyQualityStatusCommand(e, h1, QualityStatus.FAILED)));
            // orgaccess.company: dos cambios de privacidad de A (el segundo reemplaza al primero) y uno de B
            add("company/cA", "CPU(cA 1)", e -> beans.privacy().apply(new ApplyCompanyPrivacyCommand(e, cA, Map.of("SUPPLIER", "SHARED"))));
            add("company/cA", "CPU(cA 2)", e -> beans.privacy().apply(new ApplyCompanyPrivacyCommand(e, cA,
                    Map.of("SUPPLIER", "SHARED", "ORIGIN", "SHARED", "MACHINERY", "SHARED"))));
            add("company/cB", "CPU(cB 1)", e -> beans.privacy().apply(new ApplyCompanyPrivacyCommand(e, cB, Map.of("WASTE", "SHARED"))));
            // una segunda transformación encadenada: consume la salida de la primera
            add("transformation/T2", "TC(T2)", e -> beans.transformation().apply(new ApplyTransformationCompletedCommand(e, t2, TransformationType.DYEING, cA,
                    "Estampadora", "Estampado", List.of(s), List.of(new ApplyTransformationCompletedCommand.Output(s2, trace("S2"), qr("S2"),
                    "Tela estampada", new BigDecimal("70"), COTTON, true)), new BigDecimal("4"), "Recorte", new BigDecimal("6"),
                    T0.plusSeconds(4000), T0.plusSeconds(5000))));
            add("batch/S", "BPC(S IN_TRANSFORMATION v1)", e -> beans.phase().apply(
                    new ApplyBatchPhaseChangedCommand(e, s, cA, BatchPhase.IN_TRANSFORMATION, false, 1L)));
            add("batch/S", "BPC(S PROCESSED v2)", e -> beans.phase().apply(new ApplyBatchPhaseChangedCommand(e, s, cA, BatchPhase.PROCESSED, false, 2L)));
        }
        expectedGraph();

        List<String> streamNames = new ArrayList<>(streams.keySet());
        int[] lengths = streamNames.stream().mapToInt(name -> streams.get(name).size()).toArray();
        List<Ev> flat = new ArrayList<>();
        streamNames.forEach(name -> flat.addAll(streams.get(name)));
        return new Instance(streamNames, lengths, flat, List.copyOf(expected), new ArrayList<>(ids.values()), Map.copyOf(names), Map.copyOf(messages));
    }

    private ApplyBatchSplitCommand.Child child(UUID batch, String name, String kg) {
        return new ApplyBatchSplitCommand.Child(batch, trace(name), qr(name), "Algodón", new BigDecimal(kg), COTTON, "Piura", "Textiles del Norte",
                "Fibra limpia", QualityStatus.NOT_REVIEWED, T0.plusSeconds(60));
    }

    // ---- el grafo que debe quedar, escrito a mano ----

    private void node(String label, String name, Object... keysAndValues) {
        expected.add(GraphSnapshot.node(label, name, props(name, label, keysAndValues)));
    }

    private Map<String, Object> props(String name, String label, Object... keysAndValues) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(label.equals("Batch") ? "batchId" : label.equals("Transformation") ? "transformationId" : "companyId", name);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            props.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return props;
    }

    private void rel(String type, String from, String to, Object... keysAndValues) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            props.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        expected.add(GraphSnapshot.relationship(type, from, to, props));
    }

    private Object[] born(String name, String kg, Instant createdAt, String material, boolean registered, boolean finalProduct) {
        List<Object> props = new ArrayList<>(List.of("traceabilityId", trace(name), "qrCode", qr(name), "materialType", material,
                "composition", COTTON_JSON, "quantityKg", kg, "createdAt", createdAt.toString(), "finalProduct", finalProduct,
                "provisional", false, "qualityHold", false, "companyId", "cA", "qualityStatus", "NOT_REVIEWED"));
        if (registered) {
            props.addAll(List.of("origin", "Piura", "supplierName", "Textiles del Norte", "receptionCharacteristics", "Fibra limpia"));
        }
        return props.toArray();
    }

    private static Object[] with(Object[] base, Object... overrides) {
        Map<Object, Object> merged = new LinkedHashMap<>();
        for (int i = 0; i < base.length; i += 2) {
            merged.put(base[i], base[i + 1]);
        }
        for (int i = 0; i < overrides.length; i += 2) {
            merged.put(overrides[i], overrides[i + 1]);
        }
        List<Object> flat = new ArrayList<>();
        merged.forEach((key, value) -> {
            flat.add(key);
            flat.add(value);
        });
        return flat.toArray();
    }

    private void expectedGraph() {
        String childCreated = T0.plusSeconds(60).toString();
        Instant firstEnd = T0.plusSeconds(3600);
        node("Batch", "L1", with(born("L1", "100.000", T0, "Algodón", true, false), "phase", "SPLIT", "batchVersion", 1L));
        node("Batch", "H1", with(born("H1", "40.000", T0.plusSeconds(60), "Algodón", true, false), "phase", "PROCESSED", "batchVersion", 2L,
                "qualityStatus", large ? "FAILED" : "NOT_REVIEWED"));
        node("Batch", "H2", with(born("H2", "60.000", T0.plusSeconds(60), "Algodón", true, false), "phase", "AVAILABLE", "batchVersion", 2L,
                "companyId", "cB"));
        node("Batch", "S", large
                ? with(born("S", "80.000", firstEnd, "Tela teñida", false, true), "phase", "PROCESSED", "batchVersion", 2L)
                : with(born("S", "35.000", firstEnd, "Tela teñida", false, true), "phase", "AVAILABLE"));
        node("Transformation", "T1", "companyId", "cA", "type", "DYEING", "startedAt", T0.toString(), "completedAt", firstEnd.toString(),
                "machineName", "Cardadora", "machineType", "Cardado", "shrinkageKg", large ? "5.000" : "3.000", "wasteKg", large ? "5.000" : "2.000",
                "wasteReason", "Orillo", "provisional", false);
        node("Company", "cA", large ? new Object[]{"privacy", privacy("SUPPLIER", "ORIGIN", "MACHINERY")} : new Object[0]);
        node("Company", "cB", large ? new Object[]{"privacy", privacy("WASTE")} : new Object[0]);
        rel("OWNS_BATCH", "cA", "L1");
        rel("OWNS_BATCH", "cA", "H1");
        rel("OWNS_BATCH", "cB", "H2");
        rel("OWNS_BATCH", "cA", "S");
        rel("SPLIT_INTO", "L1", "H1");
        rel("SPLIT_INTO", "L1", "H2");
        rel("INPUT_OF", "H1", "T1");
        rel("PRODUCED", "T1", "S");
        rel("TRANSFERRED_VIA", "H2", "cB", "transferId", "TR1", "at", T0.plusSeconds(120).toString());
        if (large) {
            node("Batch", "L2", with(born("L2", "50.000", T0, "Algodón", true, false), "phase", "PROCESSED", "batchVersion", 2L));
            node("Batch", "S2", with(born("S2", "70.000", T0.plusSeconds(5000), "Tela estampada", false, true), "phase", "AVAILABLE"));
            node("Transformation", "T2", "companyId", "cA", "type", "DYEING", "startedAt", T0.plusSeconds(4000).toString(),
                    "completedAt", T0.plusSeconds(5000).toString(), "machineName", "Estampadora", "machineType", "Estampado", "shrinkageKg", "6.000",
                    "wasteKg", "4.000", "wasteReason", "Recorte", "provisional", false);
            rel("OWNS_BATCH", "cA", "L2");
            rel("OWNS_BATCH", "cA", "S2");
            rel("INPUT_OF", "L2", "T1");
            rel("INPUT_OF", "S", "T2");
            rel("PRODUCED", "T2", "S2");
        }
        expected.sort(String::compareTo);
    }

    /** El JSON de privacidad con los 11 campos: los indicados {@code SHARED} y el resto {@code PRIVATE}. */
    private static String privacy(String... shared) {
        List<String> sharedFields = List.of(shared);
        StringBuilder json = new StringBuilder("{");
        for (var field : com.nodotextil.trazatex.traceability.domain.model.ConfigurableField.values()) {
            json.append(json.length() > 1 ? "," : "").append('"').append(field).append("\":\"")
                    .append(sharedFields.contains(field.name()) ? "SHARED" : "PRIVATE").append('"');
        }
        return json.append('}').toString();
    }
}
