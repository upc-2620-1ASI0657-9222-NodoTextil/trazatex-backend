package com.nodotextil.trazatex.traceability.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyPrivacyCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada evento sobre el grafo (US29, RF-053), uno por uno y sobre Neo4j real. El orden entre eventos se prueba en el subpaso siguiente. */
class LineageGraphIT extends IntegrationTestBase {

    static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID COMPANY_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID L1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID H1 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID H2 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final UUID L2 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    static final UUID OUT = UUID.fromString("00000000-0000-0000-0000-000000000005");
    static final UUID T1 = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    static final Instant T0 = Instant.parse("2026-10-21T10:00:00Z");
    static final List<CompositionComponent> COTTON = List.of(new CompositionComponent("Algodón", new BigDecimal("100")));

    @Autowired
    RegisterBatchUseCase registerBatch;
    @Autowired
    ApplyBatchSplitUseCase split;
    @Autowired
    ApplyTransformationCompletedUseCase transformation;
    @Autowired
    ApplyBatchPhaseChangedUseCase phaseChanged;
    @Autowired
    ApplyTransferAcceptedUseCase transfer;
    @Autowired
    ApplyQualityStatusUseCase quality;
    @Autowired
    ApplyCompanyPrivacyUseCase privacy;
    @Autowired
    ApplyCompanyRegisteredUseCase companyRegistered;

    ListAppender<ILoggingEvent> logs;
    Logger adapterLogger;

    @BeforeEach
    void captureLogs() {
        adapterLogger = (Logger) LoggerFactory.getLogger("com.nodotextil.trazatex.traceability.infrastructure.persistence.Neo4jLineageGraphAdapter");
        logs = new ListAppender<>();
        logs.start();
        adapterLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        adapterLogger.detachAppender(logs);
    }

    // ---- ayudas ----

    static UUID event() {
        return UUID.randomUUID();
    }

    static RegisterBatchCommand registered(UUID batch, String traceabilityId, String qr) {
        return new RegisterBatchCommand(event(), batch, traceabilityId, qr, COMPANY_A, "Algodón", COTTON, new BigDecimal("100"),
                "Piura", "Textiles del Norte", "Fibra limpia", T0);
    }

    static ApplyBatchSplitCommand.Child child(UUID batch, String traceabilityId, String qr, String kg) {
        return new ApplyBatchSplitCommand.Child(batch, traceabilityId, qr, "Algodón", new BigDecimal(kg), COTTON, "Piura", "Textiles del Norte",
                "Fibra limpia", QualityStatus.NOT_REVIEWED, T0.plusSeconds(60));
    }

    static ApplyBatchPhaseChangedCommand phase(UUID batch, UUID company, BatchPhase newPhase, boolean hold, long version) {
        return new ApplyBatchPhaseChangedCommand(event(), batch, company, newPhase, hold, version);
    }

    Map<String, Object> batch(UUID id) {
        return neo4j.query("MATCH (b:Batch {batchId: $id}) RETURN properties(b) AS p").bind(id.toString()).to("id")
                .fetch().one().map(row -> castMap(row.get("p"))).orElseThrow(() -> new AssertionError("no hay lote " + id));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    long count(String cypher) {
        return (Long) neo4j.query(cypher).fetch().one().orElseThrow().get("n");
    }

    List<String> owners(UUID batch) {
        return neo4j.query("MATCH (c:Company)-[:OWNS_BATCH]->(b:Batch {batchId: $id}) RETURN c.companyId AS c ORDER BY c").bind(batch.toString()).to("id")
                .fetchAs(String.class).mappedBy((t, r) -> r.get("c").asString()).all().stream().toList();
    }

    List<String> errors() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ---- BatchRegistered ----

    @Test
    void aRegisteredBatchBecomesAFullNodeOwnedByItsCompany() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        Map<String, Object> node = batch(L1);
        assertThat(node).containsEntry("traceabilityId", "TT-AAAAAAAAAA").containsEntry("qrCode", "QR-1").containsEntry("companyId", COMPANY_A.toString())
                .containsEntry("materialType", "Algodón").containsEntry("quantityKg", "100.000").containsEntry("origin", "Piura")
                .containsEntry("supplierName", "Textiles del Norte").containsEntry("receptionCharacteristics", "Fibra limpia")
                .containsEntry("phase", "AVAILABLE").containsEntry("qualityHold", false).containsEntry("qualityStatus", "NOT_REVIEWED")
                .containsEntry("finalProduct", false).containsEntry("provisional", false).doesNotContainKey("batchVersion");
        assertThat(node.get("composition").toString()).isEqualTo("[{\"name\":\"Algodón\",\"percentage\":100.00}]");
        assertThat(owners(L1)).containsExactly(COMPANY_A.toString());
        assertThat(count("MATCH (c:Company) RETURN count(c) AS n")).isEqualTo(1);
    }

    // ---- BatchSplit ----

    @Test
    void aSplitCreatesTheChildrenWithTheirDataAndLinksThemToTheParentWithoutTouchingTheParentsPhase() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        split.apply(new ApplyBatchSplitCommand(event(), L1, COMPANY_A, List.of(child(H2, "TT-CCCCCCCCCC", "QR-3", "60"), child(H1, "TT-BBBBBBBBBB", "QR-2", "40"))));

        assertThat(batch(L1)).as("el padre cambia de fase solo con su BatchPhaseChanged").containsEntry("phase", "AVAILABLE");
        Map<String, Object> first = batch(H1);
        assertThat(first).containsEntry("traceabilityId", "TT-BBBBBBBBBB").containsEntry("quantityKg", "40.000").containsEntry("phase", "AVAILABLE")
                .containsEntry("qualityHold", false).containsEntry("companyId", COMPANY_A.toString()).containsEntry("provisional", false)
                .containsEntry("finalProduct", false).containsEntry("origin", "Piura");
        assertThat(first.get("createdAt").toString()).startsWith("2026-10-21T10:01");
        assertThat(count("MATCH (:Batch {batchId: '" + L1 + "'})-[r:SPLIT_INTO]->(:Batch) RETURN count(r) AS n")).isEqualTo(2);
        assertThat(owners(H1)).containsExactly(COMPANY_A.toString());
        assertThat(owners(H2)).containsExactly(COMPANY_A.toString());
    }

    @Test
    void aChildInheritsTheQualityStatusOfItsParent() {
        ApplyBatchSplitCommand.Child conforming = new ApplyBatchSplitCommand.Child(H1, "TT-BBBBBBBBBB", "QR-2", "Algodón", new BigDecimal("4"), COTTON,
                null, null, null, QualityStatus.CONFORMING, T0);

        split.apply(new ApplyBatchSplitCommand(event(), L1, COMPANY_A, List.of(conforming)));

        assertThat(batch(H1)).containsEntry("qualityStatus", "CONFORMING");
    }

    @Test
    void theParentOfASplitWhoseRegistrationHasNotArrivedIsAProvisionalNodeWithOnlyItsId() {
        split.apply(new ApplyBatchSplitCommand(event(), L1, COMPANY_A, List.of(child(H1, "TT-BBBBBBBBBB", "QR-2", "40"))));

        assertThat(batch(L1)).containsEntry("provisional", true).containsOnlyKeys("batchId", "provisional");
        assertThat(count("MATCH (:Batch {batchId: '" + L1 + "'})-[r:SPLIT_INTO]->(:Batch {batchId: '" + H1 + "'}) RETURN count(r) AS n")).isEqualTo(1);
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));
        assertThat(batch(L1)).containsEntry("provisional", false).containsEntry("traceabilityId", "TT-AAAAAAAAAA").containsEntry("phase", "AVAILABLE");
    }

    // ---- TransformationCompleted ----

    private ApplyTransformationCompletedCommand completed(boolean finalProduct) {
        return new ApplyTransformationCompletedCommand(event(), T1, TransformationType.DYEING, COMPANY_A, "Cardadora", "Cardado",
                List.of(L2, H1), List.of(new ApplyTransformationCompletedCommand.Output(OUT, "TT-DDDDDDDDDD", "QR-5", "Tela teñida", new BigDecimal("35"),
                COTTON, finalProduct)), new BigDecimal("2"), "Orillo", new BigDecimal("3"), T0, T0.plusSeconds(3600));
    }

    @Test
    void aCompletedTransformationLinksEveryInputToItAndItToEveryOutputAndLeavesTheInputsPhaseAlone() {
        registerBatch.apply(registered(L2, "TT-EEEEEEEEEE", "QR-4"));

        transformation.apply(completed(true));

        Map<String, Object> node = neo4j.query("MATCH (t:Transformation {transformationId: $id}) RETURN properties(t) AS p").bind(T1.toString()).to("id")
                .fetch().one().map(row -> castMap(row.get("p"))).orElseThrow();
        assertThat(node).containsEntry("type", "DYEING").containsEntry("companyId", COMPANY_A.toString()).containsEntry("machineName", "Cardadora")
                .containsEntry("machineType", "Cardado").containsEntry("wasteKg", "2.000").containsEntry("wasteReason", "Orillo")
                .containsEntry("shrinkageKg", "3.000").containsEntry("provisional", false).doesNotContainKey("machineId");
        assertThat(count("MATCH (b:Batch)-[:INPUT_OF]->(:Transformation {transformationId: '" + T1 + "'}) RETURN count(b) AS n")).isEqualTo(2);
        assertThat(count("MATCH (:Transformation {transformationId: '" + T1 + "'})-[:PRODUCED]->(b:Batch {batchId: '" + OUT + "'}) RETURN count(b) AS n")).isEqualTo(1);
        assertThat(batch(L2)).as("la entrada cambia de fase con su BatchPhaseChanged").containsEntry("phase", "AVAILABLE");
        assertThat(batch(H1)).as("una entrada desconocida queda provisional").containsEntry("provisional", true).containsOnlyKeys("batchId", "provisional");
        Map<String, Object> output = batch(OUT);
        assertThat(output).containsEntry("finalProduct", true).containsEntry("phase", "AVAILABLE").containsEntry("qualityStatus", "NOT_REVIEWED")
                .containsEntry("companyId", COMPANY_A.toString()).containsEntry("provisional", false).containsEntry("quantityKg", "35.000")
                .doesNotContainKeys("origin", "supplierName", "receptionCharacteristics");
        assertThat(output.get("createdAt").toString()).as("la fecha de la salida es el fin de la transformación").startsWith("2026-10-21T11:00");
        assertThat(owners(OUT)).containsExactly(COMPANY_A.toString());
    }

    @Test
    void aTransformationWithoutWasteReasonOrMachineKeepsThemAbsent() {
        transformation.apply(new ApplyTransformationCompletedCommand(event(), T1, TransformationType.SPINNING, COMPANY_A, null, null, List.of(L2),
                List.of(), BigDecimal.ZERO, null, BigDecimal.ZERO, T0, T0));

        Map<String, Object> node = neo4j.query("MATCH (t:Transformation) RETURN properties(t) AS p").fetch().one().map(row -> castMap(row.get("p"))).orElseThrow();
        assertThat(node).doesNotContainKeys("wasteReason", "machineName", "machineType").containsEntry("wasteKg", "0.000");
    }

    // ---- BatchPhaseChanged ----

    @Test
    void aPhaseChangeIsAppliedOnlyWhenItsVersionIsGreaterThanTheStoredOne() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        phaseChanged.apply(phase(L1, COMPANY_A, BatchPhase.IN_TRANSFORMATION, false, 3));
        phaseChanged.apply(phase(L1, COMPANY_A, BatchPhase.AVAILABLE, true, 2));
        phaseChanged.apply(phase(L1, COMPANY_A, BatchPhase.AVAILABLE, true, 3));

        assertThat(batch(L1)).as("las versiones 2 y 3 repetida no pisan la 3").containsEntry("phase", "IN_TRANSFORMATION")
                .containsEntry("qualityHold", false).containsEntry("batchVersion", 3L);

        phaseChanged.apply(phase(L1, COMPANY_A, BatchPhase.PROCESSED, false, 7));

        assertThat(batch(L1)).containsEntry("phase", "PROCESSED").containsEntry("batchVersion", 7L);
    }

    @Test
    void aPhaseChangeTellsWhetherItWasAppliedToTheGraphPort() {
        assertThat(phaseChangedPort(phase(L1, COMPANY_A, BatchPhase.AVAILABLE, false, 1))).isTrue();
        assertThat(phaseChangedPort(phase(L1, COMPANY_A, BatchPhase.AVAILABLE, false, 1))).isFalse();
        assertThat(phaseChangedPort(phase(L1, COMPANY_A, BatchPhase.AVAILABLE, false, 0))).isFalse();
    }

    @Autowired
    com.nodotextil.trazatex.traceability.application.port.out.LineageGraphPort graphPort;

    private boolean phaseChangedPort(ApplyBatchPhaseChangedCommand command) {
        return graphPort.applyBatchPhaseChanged(command);
    }

    @Test
    void theHoldAndTheOwnerComeFromThePhaseChangeAndTheOwnershipEdgeFollowsTheOwner() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        phaseChanged.apply(phase(L1, COMPANY_B, BatchPhase.AVAILABLE, true, 5));

        assertThat(batch(L1)).containsEntry("companyId", COMPANY_B.toString()).containsEntry("qualityHold", true);
        assertThat(owners(L1)).as("una sola relación OWNS_BATCH, desde la empresa actual").containsExactly(COMPANY_B.toString());
        assertThat(count("MATCH (:Company)-[r:OWNS_BATCH]->(:Batch) RETURN count(r) AS n")).isEqualTo(1);
    }

    @Test
    void aPhaseChangeForAnUnknownBatchCreatesAProvisionalNodeThatTheRegistrationLaterCompletes() {
        phaseChanged.apply(phase(L1, COMPANY_B, BatchPhase.IN_TRANSFER, false, 4));

        assertThat(batch(L1)).containsEntry("provisional", true).containsEntry("phase", "IN_TRANSFER").containsEntry("batchVersion", 4L)
                .containsEntry("companyId", COMPANY_B.toString()).doesNotContainKeys("traceabilityId", "qualityStatus");
        assertThat(owners(L1)).containsExactly(COMPANY_B.toString());

        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        assertThat(batch(L1)).as("la creación completa los datos pero no pisa el estado de una versión mayor")
                .containsEntry("provisional", false).containsEntry("traceabilityId", "TT-AAAAAAAAAA").containsEntry("phase", "IN_TRANSFER")
                .containsEntry("companyId", COMPANY_B.toString()).containsEntry("qualityHold", false).containsEntry("batchVersion", 4L)
                .containsEntry("qualityStatus", "NOT_REVIEWED");
        assertThat(owners(L1)).containsExactly(COMPANY_B.toString());
    }

    @Test
    void theFirstPhaseChangeOfAChildMayArriveBeforeTheSplitThatCreatesIt() {
        phaseChanged.apply(phase(H1, COMPANY_A, BatchPhase.IN_TRANSFORMATION, false, 1));

        split.apply(new ApplyBatchSplitCommand(event(), L1, COMPANY_A, List.of(child(H1, "TT-BBBBBBBBBB", "QR-2", "40"))));

        assertThat(batch(H1)).containsEntry("phase", "IN_TRANSFORMATION").containsEntry("batchVersion", 1L).containsEntry("provisional", false)
                .containsEntry("traceabilityId", "TT-BBBBBBBBBB").containsEntry("quantityKg", "40.000");
    }

    // ---- QualityStatusChanged ----

    @Test
    void aQualityStatusReplacesTheStoredOneInTheOrderOfItsTopic() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        quality.apply(new ApplyQualityStatusCommand(event(), L1, QualityStatus.FAILED));
        quality.apply(new ApplyQualityStatusCommand(event(), L1, QualityStatus.CONFORMING));

        assertThat(batch(L1)).containsEntry("qualityStatus", "CONFORMING");
    }

    @Test
    void aQualityStatusThatArrivedBeforeTheCreationIsNotOverwrittenByTheInitialOne() {
        quality.apply(new ApplyQualityStatusCommand(event(), L1, QualityStatus.FAILED));
        assertThat(batch(L1)).containsEntry("provisional", true).containsEntry("qualityStatus", "FAILED");

        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        assertThat(batch(L1)).containsEntry("qualityStatus", "FAILED").containsEntry("provisional", false);
    }

    // ---- TransferAccepted ----

    @Test
    void anAcceptedTransferIsHistoryOnlyAndCreatesNoBatchAndNoGenealogyEdge() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));
        UUID transferId = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        ApplyTransferAcceptedCommand command = new ApplyTransferAcceptedCommand(event(), transferId, List.of(L1), COMPANY_B, T0.plusSeconds(10));

        transfer.apply(command);
        transfer.apply(new ApplyTransferAcceptedCommand(event(), transferId, List.of(L1), COMPANY_B, T0.plusSeconds(10)));

        assertThat(count("MATCH (:Batch {batchId: '" + L1 + "'})-[r:TRANSFERRED_VIA {transferId: '" + transferId + "'}]->(:Company {companyId: '"
                + COMPANY_B + "'}) RETURN count(r) AS n")).as("una sola vez aunque llegue otra vez").isEqualTo(1);
        assertThat(count("MATCH ()-[r:TRANSFERRED_VIA]->() WHERE r.at IS NOT NULL RETURN count(r) AS n")).isEqualTo(1);
        assertThat(count("MATCH (b:Batch) RETURN count(b) AS n")).as("no crea lotes").isEqualTo(1);
        assertThat(count("MATCH (:Batch)-[r:SPLIT_INTO|INPUT_OF|PRODUCED]->() RETURN count(r) AS n")).as("ni relaciones genealógicas").isZero();
        assertThat(owners(L1)).as("el dueño cambia con BatchPhaseChanged, no con este evento").containsExactly(COMPANY_A.toString());
    }

    @Test
    void aTransferOfAnUnknownBatchLeavesAProvisionalNode() {
        transfer.apply(new ApplyTransferAcceptedCommand(event(), UUID.randomUUID(), List.of(L1, L2), COMPANY_B, T0));

        assertThat(batch(L1)).containsEntry("provisional", true).containsOnlyKeys("batchId", "provisional");
        assertThat(count("MATCH (b:Batch) WHERE b.provisional RETURN count(b) AS n")).isEqualTo(2);
    }

    // ---- CompanyPrivacyUpdated ----

    @Test
    void thePrivacyIsStoredWithAllElevenFieldsAndTheLatestEventReplacesIt() {
        privacy.apply(new ApplyCompanyPrivacyCommand(event(), COMPANY_A, Map.of("SUPPLIER", "SHARED")));

        assertThat(neo4j.query("MATCH (c:Company {companyId: $id}) RETURN c.privacy AS p").bind(COMPANY_A.toString()).to("id").fetch().one().orElseThrow().get("p").toString())
                .contains("\"SUPPLIER\":\"SHARED\"").contains("\"ORIGIN\":\"PRIVATE\"").contains("\"QUALITY_TEST_VALUES\":\"PRIVATE\"");

        privacy.apply(new ApplyCompanyPrivacyCommand(event(), COMPANY_A, Map.of("ORIGIN", "SHARED")));

        assertThat(neo4j.query("MATCH (c:Company {companyId: $id}) RETURN c.privacy AS p").bind(COMPANY_A.toString()).to("id").fetch().one().orElseThrow().get("p").toString())
                .contains("\"SUPPLIER\":\"PRIVATE\"").contains("\"ORIGIN\":\"SHARED\"");
        assertThat(count("MATCH (c:Company) RETURN count(c) AS n")).isEqualTo(1);
    }

    @Test
    void anUnknownPrivacyFieldIsIgnoredAndAnUnknownValueIsStoredAsPrivate() {
        Map<String, String> raw = new java.util.LinkedHashMap<>();
        raw.put("TRACEABILITY_ID", "SHARED");
        raw.put("SUPPLIER", "SOMETIMES");
        raw.put("QUANTITY", "SHARED");

        privacy.apply(new ApplyCompanyPrivacyCommand(event(), COMPANY_A, raw));

        String stored = neo4j.query("MATCH (c:Company) RETURN c.privacy AS p").fetch().one().orElseThrow().get("p").toString();
        assertThat(stored).doesNotContain("TRACEABILITY_ID").contains("\"SUPPLIER\":\"PRIVATE\"").contains("\"QUANTITY\":\"SHARED\"");
    }

    // ---- CompanyRegistered: el nombre de la empresa ----

    private Map<String, Object> company(UUID id) {
        return neo4j.query("MATCH (c:Company {companyId: $id}) RETURN properties(c) AS p").bind(id.toString()).to("id").fetch().one()
                .map(row -> castMap(row.get("p"))).orElseThrow(() -> new AssertionError("no hay empresa " + id));
    }

    @Test
    void aRegisteredCompanyGetsItsName() {
        companyRegistered.apply(new ApplyCompanyRegisteredCommand(event(), COMPANY_A, "Hilandería Andina SAC"));

        assertThat(company(COMPANY_A)).containsEntry("businessName", "Hilandería Andina SAC").doesNotContainKey("privacy");
    }

    @Test
    void theNameDoesNotTouchThePrivacyThatArrivedBeforeIt() {
        privacy.apply(new ApplyCompanyPrivacyCommand(event(), COMPANY_A, Map.of("SUPPLIER", "SHARED")));

        companyRegistered.apply(new ApplyCompanyRegisteredCommand(event(), COMPANY_A, "Hilandería Andina SAC"));

        assertThat(company(COMPANY_A)).containsEntry("businessName", "Hilandería Andina SAC");
        assertThat(company(COMPANY_A).get("privacy").toString()).contains("\"SUPPLIER\":\"SHARED\"").contains("\"ORIGIN\":\"PRIVATE\"");
        assertThat(count("MATCH (c:Company) RETURN count(c) AS n")).isEqualTo(1);
    }

    @Test
    void thePrivacyThatArrivesAfterTheNameDoesNotTouchTheName() {
        companyRegistered.apply(new ApplyCompanyRegisteredCommand(event(), COMPANY_A, "Hilandería Andina SAC"));

        privacy.apply(new ApplyCompanyPrivacyCommand(event(), COMPANY_A, Map.of("ORIGIN", "SHARED")));
        privacy.apply(new ApplyCompanyPrivacyCommand(event(), COMPANY_A, Map.of("QUANTITY", "SHARED")));

        assertThat(company(COMPANY_A)).containsEntry("businessName", "Hilandería Andina SAC");
        assertThat(company(COMPANY_A).get("privacy").toString()).contains("\"QUANTITY\":\"SHARED\"").contains("\"ORIGIN\":\"PRIVATE\"");
    }

    @Test
    void aCompanyAlreadyKnownByItsBatchesIsCompletedWithoutDuplicatingTheNode() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        companyRegistered.apply(new ApplyCompanyRegisteredCommand(event(), COMPANY_A, "Hilandería Andina SAC"));

        assertThat(count("MATCH (c:Company) RETURN count(c) AS n")).isEqualTo(1);
        assertThat(owners(L1)).containsExactly(COMPANY_A.toString());
        assertThat(company(COMPANY_A)).containsEntry("businessName", "Hilandería Andina SAC");
    }

    @Test
    void aDifferentNameKeepsTheStoredOneAndLogsAnErrorWithTheEventTheCompanyAndTheField() {
        companyRegistered.apply(new ApplyCompanyRegisteredCommand(event(), COMPANY_A, "Hilandería Andina SAC"));
        UUID eventId = event();

        companyRegistered.apply(new ApplyCompanyRegisteredCommand(eventId, COMPANY_A, "Otro nombre SAC"));
        companyRegistered.apply(new ApplyCompanyRegisteredCommand(event(), COMPANY_A, "Hilandería Andina SAC"));

        assertThat(company(COMPANY_A)).containsEntry("businessName", "Hilandería Andina SAC");
        assertThat(errors()).singleElement().satisfies(message -> assertThat(message).contains("eventId=" + eventId).contains("companyId=" + COMPANY_A)
                .contains("campo=businessName"));
    }

    // ---- datos de identidad: se completan, nunca se pisan ----

    @Test
    void aDifferentValueForAnIdentityFieldKeepsTheStoredOneLogsAnErrorAndStillAppliesTheRestOfTheEvent() {
        registerBatch.apply(registered(H1, "TT-BBBBBBBBBB", "QR-2"));
        UUID eventId = event();
        ApplyBatchSplitCommand.Child conflicting = new ApplyBatchSplitCommand.Child(H1, "TT-BBBBBBBBBB", "QR-OTRO", "Lana", new BigDecimal("999"),
                COTTON, "Cusco", "Otro proveedor", "Humedo", QualityStatus.CONFORMING, T0.plusSeconds(500));

        split.apply(new ApplyBatchSplitCommand(eventId, L1, COMPANY_A, List.of(conflicting)));

        assertThat(batch(H1)).as("se conserva lo guardado").containsEntry("qrCode", "QR-2").containsEntry("materialType", "Algodón")
                .containsEntry("quantityKg", "100.000").containsEntry("origin", "Piura").containsEntry("supplierName", "Textiles del Norte")
                .containsEntry("receptionCharacteristics", "Fibra limpia").containsEntry("traceabilityId", "TT-BBBBBBBBBB");
        assertThat(batch(H1).get("createdAt").toString()).startsWith("2026-10-21T10:00");
        assertThat(errors()).as("un ERROR por campo en conflicto, con eventId, batchId y el nombre del campo").hasSize(7)
                .allSatisfy(message -> assertThat(message).contains("eventId=" + eventId).contains("batchId=" + H1));
        assertThat(errors()).anySatisfy(message -> assertThat(message).contains("campo=qrCode"))
                .anySatisfy(message -> assertThat(message).contains("campo=materialType"))
                .anySatisfy(message -> assertThat(message).contains("campo=quantityKg"))
                .anySatisfy(message -> assertThat(message).contains("campo=origin"))
                .anySatisfy(message -> assertThat(message).contains("campo=supplierName"))
                .anySatisfy(message -> assertThat(message).contains("campo=receptionCharacteristics"))
                .anySatisfy(message -> assertThat(message).contains("campo=createdAt"));
        assertThat(count("MATCH (:Batch {batchId: '" + L1 + "'})-[r:SPLIT_INTO]->(:Batch {batchId: '" + H1 + "'}) RETURN count(r) AS n"))
                .as("el resto del evento sí se aplica").isEqualTo(1);
    }

    @Test
    void aDifferentFinalProductFlagOrCreationDateIsAlsoAConflict() {
        registerBatch.apply(registered(OUT, "TT-DDDDDDDDDD", "QR-5"));
        UUID eventId = event();

        transformation.apply(new ApplyTransformationCompletedCommand(eventId, T1, TransformationType.DYEING, COMPANY_A, null, null, List.of(L2),
                List.of(new ApplyTransformationCompletedCommand.Output(OUT, "TT-DDDDDDDDDD", "QR-5", "Algodón", new BigDecimal("100"), COTTON, true)),
                BigDecimal.ZERO, null, BigDecimal.ZERO, T0, T0.plusSeconds(3600)));

        assertThat(batch(OUT)).containsEntry("finalProduct", false);
        assertThat(errors()).anySatisfy(message -> assertThat(message).contains("batchId=" + OUT).contains("campo=finalProduct"))
                .anySatisfy(message -> assertThat(message).contains("campo=createdAt"));
        assertThat(errors()).as("la misma composición con otra escala no es un conflicto").noneSatisfy(message -> assertThat(message).contains("campo=composition"));
    }

    @Test
    void theSameEventDataAgainIsNotAConflict() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        assertThat(errors()).isEmpty();
        assertThat(count("MATCH (b:Batch) RETURN count(b) AS n")).isEqualTo(1);
    }

    @Test
    void aTraceabilityIdAlreadyUsedByAnotherBatchIsRejectedByTheConstraintAndRollsBackTheWholeEvent() {
        registerBatch.apply(registered(L1, "TT-AAAAAAAAAA", "QR-1"));

        assertThatThrownBy(() -> split.apply(new ApplyBatchSplitCommand(event(), L2, COMPANY_A, List.of(
                child(H1, "TT-BBBBBBBBBB", "QR-2", "40"), child(H2, "TT-AAAAAAAAAA", "QR-3", "60")))))
                .hasMessageContaining("already exists");

        assertThat(count("MATCH (b:Batch) RETURN count(b) AS n")).as("ni el padre provisional ni el primer hijo quedaron").isEqualTo(1);
        assertThat(count("MATCH ()-[r:SPLIT_INTO]->() RETURN count(r) AS n")).isZero();
    }
}
