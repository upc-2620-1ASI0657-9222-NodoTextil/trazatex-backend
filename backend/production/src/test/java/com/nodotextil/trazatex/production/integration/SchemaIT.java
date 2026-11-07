package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Migraciones Flyway y restricciones de la base: lo que la aplicación no puede olvidar porque la base lo impide. */
class SchemaIT extends IntegrationTestBase {

    private static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID COMPANY_B = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    // ---- ayudas: inserciones directas con valores por defecto válidos y sobrescrituras ----

    private void insert(String table, Map<String, Object> values) {
        String columns = String.join(", ", values.keySet());
        String placeholders = String.join(", ", values.keySet().stream().map(c -> ":" + c).toList());
        var statement = jdbc.sql("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")");
        for (Map.Entry<String, Object> value : values.entrySet()) {
            statement = statement.param(value.getKey(), value.getValue());
        }
        statement.update();
    }

    private static Map<String, Object> with(Map<String, Object> defaults, Object... overrides) {
        Map<String, Object> values = new LinkedHashMap<>(defaults);
        for (int i = 0; i < overrides.length; i += 2) {
            values.put((String) overrides[i], overrides[i + 1]);
        }
        return values;
    }

    private static final java.time.OffsetDateTime NOW = java.time.OffsetDateTime.parse("2026-10-20T12:00:00Z");

    private Map<String, Object> batchRow(String traceabilityId, String qr) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("traceability_id", traceabilityId);
        row.put("qr_code", qr);
        row.put("company_id", COMPANY_A);
        row.put("name", "Lote");
        row.put("material_type", "Fibra");
        row.put("quantity_kg", new java.math.BigDecimal("10.000"));
        row.put("current_stage", "AVAILABLE");
        row.put("quality_status", "NOT_REVIEWED");
        row.put("quality_hold", false);
        row.put("final_product", false);
        row.put("origin_type", "REGISTERED");
        row.put("origin", "Piura");
        row.put("reception_characteristics", "ok");
        row.put("created_by", USER);
        row.put("created_at", NOW);
        return row;
    }

    private UUID insertBatch(Object... overrides) {
        String serial = String.format("%010d", counter++);
        Map<String, Object> row = with(batchRow("TT-" + serial, "Q".repeat(26 - serial.length()) + serial), overrides);
        insert("batches", row);
        return (UUID) row.get("id");
    }

    private int counter = 1;

    private UUID insertMachine(UUID company, String code) {
        UUID id = UUID.randomUUID();
        insert("machines", Map.of("id", id, "company_id", company, "internal_code", code, "name", "Cardadora", "type",
                "Cardado", "status", "ACTIVE", "created_at", NOW));
        return id;
    }

    private Map<String, Object> transformationRow(UUID machine) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("company_id", COMPANY_A);
        row.put("transformation_type", "SPINNING");
        row.put("machinery_id", machine);
        row.put("operator_id", USER);
        row.put("status", "IN_PROGRESS");
        row.put("total_input_kg", new java.math.BigDecimal("100.000"));
        row.put("start_time", NOW);
        return row;
    }

    private Map<String, Object> transferRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("origin_company_id", COMPANY_A);
        row.put("target_company_id", COMPANY_B);
        row.put("status", "PENDING");
        row.put("departure_time", NOW);
        row.put("initiated_by", USER);
        return row;
    }

    // ---- migraciones ----

    @Test
    void flywayCreatedEveryTable() {
        List<String> tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
                .query(String.class).list();
        assertThat(tables).contains("batches", "batch_compositions", "transformations", "transformation_inputs",
                "transformation_outputs", "transfers", "transfer_batches", "machines", "machine_status_history",
                "company_projection", "outbox", "processed_events", "idempotency_keys", "audit_log", "phase_command_log",
                "flyway_schema_history");
    }

    // ---- batches ----

    @Test
    void aValidBatchRowIsAccepted() {
        assertThatCode(() -> insertBatch()).doesNotThrowAnyException();
    }

    @Test
    void traceabilityIdAndQrAreUnique() {
        insert("batches", batchRow("TT-ABCDEFGHJK", "A".repeat(26)));

        assertThatThrownBy(() -> insert("batches", batchRow("TT-ABCDEFGHJK", "B".repeat(26))))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ux_batches_traceability_id");
        assertThatThrownBy(() -> insert("batches", batchRow("TT-ZZZZZZZZZZ", "A".repeat(26))))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ux_batches_qr_code");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tt-abcdefghjk", "TT-ABCDEFGHJ", "TT-ABCDEFGHJKM", "XX-ABCDEFGHJK", "TT-ABCDEFGHJO", "TT-ABCDEFGHJU", ""})
    void malformedTraceabilityIdsAreRejected(String traceabilityId) {
        assertThatThrownBy(() -> insert("batches", batchRow(traceabilityId, "C".repeat(26))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    static java.util.stream.Stream<String> malformedQrs() {
        return java.util.stream.Stream.of("short", "O".repeat(26), "I".repeat(26), "a".repeat(26), "A".repeat(27));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("malformedQrs")
    void malformedQrCodesAreRejected(String qr) {
        assertThatThrownBy(() -> insert("batches", batchRow("TT-ABCDEFGHJK", qr)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.0004"})
    void aBatchQuantityMustBePositive(String kg) {
        assertThatThrownBy(() -> insertBatch("quantity_kg", new java.math.BigDecimal(kg)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void phaseAndQualityStatusMustBeKnownValues() {
        assertThatThrownBy(() -> insertBatch("current_stage", "EN_EVALUACION")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch("quality_status", "APPROVED")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch("origin_type", "IMPORTED")).isInstanceOf(DataIntegrityViolationException.class);
        for (String phase : List.of("AVAILABLE", "IN_TRANSFORMATION", "IN_TRANSFER", "IN_QUALITY_CONTROL", "PROCESSED", "SPLIT", "DISCARDED")) {
            assertThatCode(() -> insertBatch("current_stage", phase)).doesNotThrowAnyException();
        }
    }

    @Test
    void aRegisteredBatchNeedsOriginAndReceptionCharacteristics() {
        assertThatThrownBy(() -> insertBatch("origin", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch("reception_characteristics", null)).isInstanceOf(DataIntegrityViolationException.class);
        // las salidas de una transformación nacen sin ellos
        assertThatCode(() -> insertBatch("origin_type", "TRANSFORMATION", "origin", null, "reception_characteristics", null))
                .doesNotThrowAnyException();
    }

    @Test
    void commercialDataBelongsToFinalProductsOnlyAndAPriceNeedsACurrency() {
        assertThatThrownBy(() -> insertBatch("buyer_or_distributor", "Tiendas SAC")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch("final_product", true, "sale_price", new java.math.BigDecimal("10.00")))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_batches_price_needs_currency");
        assertThatThrownBy(() -> insertBatch("final_product", true, "sale_price", new java.math.BigDecimal("10.00"), "currency", "pen"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertBatch("final_product", true, "sale_price", new java.math.BigDecimal("10.00"), "currency", "PEN",
                "buyer_or_distributor", "Tiendas SAC")).doesNotThrowAnyException();
    }

    @Test
    void theVersionStartsAtZero() {
        UUID id = insertBatch();
        assertThat(jdbc.sql("SELECT version FROM batches WHERE id = :id").param("id", id).query(Long.class).single()).isZero();
    }

    // ---- batch_compositions ----

    @Test
    void compositionRowsNeedTheirBatchAndHaveAValidPercentage() {
        UUID batch = insertBatch();
        insert("batch_compositions", Map.of("batch_id", batch, "position", 0, "component_name", "Algodón", "percentage", new java.math.BigDecimal("70.00")));

        assertThatThrownBy(() -> insert("batch_compositions", Map.of("batch_id", batch, "position", 0, "component_name", "Lana",
                "percentage", new java.math.BigDecimal("30.00")))).isInstanceOf(DataIntegrityViolationException.class);
        for (String percentage : List.of("0", "-5", "100.01")) {
            assertThatThrownBy(() -> insert("batch_compositions", Map.of("batch_id", batch, "position", 1, "component_name", "Lana",
                    "percentage", new java.math.BigDecimal(percentage)))).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insert("batch_compositions", Map.of("batch_id", UUID.randomUUID(), "position", 0, "component_name", "Lana",
                "percentage", new java.math.BigDecimal("10")))).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- machines ----

    @Test
    void aMachineCodeIsUniquePerCompanyIgnoringCase() {
        insertMachine(COMPANY_A, "MQ-01");

        assertThatThrownBy(() -> insertMachine(COMPANY_A, "MQ-01")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMachine(COMPANY_A, "mq-01")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertMachine(COMPANY_B, "MQ-01")).as("otra empresa puede repetir el código").doesNotThrowAnyException();
        assertThatCode(() -> insertMachine(COMPANY_A, "MQ-02")).doesNotThrowAnyException();
    }

    @Test
    void aMachineStatusMustBeKnownAndItsHistoryNeedsTheMachine() {
        UUID machine = insertMachine(COMPANY_A, "MQ-01");
        assertThatThrownBy(() -> jdbc.sql("UPDATE machines SET status = 'BROKEN' WHERE id = :id").param("id", machine).update())
                .isInstanceOf(DataIntegrityViolationException.class);

        insert("machine_status_history", Map.of("machine_id", machine, "to_status", "ACTIVE", "changed_by", USER, "changed_at", NOW));
        assertThatThrownBy(() -> insert("machine_status_history", Map.of("machine_id", UUID.randomUUID(), "to_status", "ACTIVE",
                "changed_by", USER, "changed_at", NOW))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("machine_status_history", Map.of("machine_id", machine, "from_status", "ACTIVE", "to_status", "BROKEN",
                "changed_by", USER, "changed_at", NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- transformations ----

    @Test
    void aTransformationNeedsAnExistingMachine() {
        assertThatThrownBy(() -> insert("transformations", transformationRow(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insert("transformations", transformationRow(insertMachine(COMPANY_A, "MQ-01")))).doesNotThrowAnyException();
    }

    @Test
    void aCompletedTransformationMustBalanceInTheDatabaseToo() {
        UUID machine = insertMachine(COMPANY_A, "MQ-01");
        Map<String, Object> completed = with(transformationRow(machine), "status", "COMPLETED",
                "total_output_kg", new java.math.BigDecimal("80.000"), "waste_kg", new java.math.BigDecimal("5.000"),
                "waste_reason", "Recorte", "shrinkage_kg", new java.math.BigDecimal("15.000"), "end_time", NOW);
        assertThatCode(() -> insert("transformations", completed)).doesNotThrowAnyException();

        // entrada 100 ≠ salida 80 + desperdicio 5 + merma 10
        assertThatThrownBy(() -> insert("transformations", with(completed, "id", UUID.randomUUID(),
                "shrinkage_kg", new java.math.BigDecimal("10.000")))).isInstanceOf(DataIntegrityViolationException.class);
        // merma negativa
        assertThatThrownBy(() -> insert("transformations", with(completed, "id", UUID.randomUUID(),
                "total_output_kg", new java.math.BigDecimal("100.000"), "shrinkage_kg", new java.math.BigDecimal("-5.000"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anInProgressTransformationHasNoResultsAndACompletedOneHasAll() {
        UUID machine = insertMachine(COMPANY_A, "MQ-01");
        assertThatThrownBy(() -> insert("transformations", with(transformationRow(machine), "total_output_kg", new java.math.BigDecimal("80.000"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("transformations", with(transformationRow(machine), "status", "COMPLETED")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void wasteNeedsAReason() {
        UUID machine = insertMachine(COMPANY_A, "MQ-01");
        Map<String, Object> noReason = with(transformationRow(machine), "status", "COMPLETED",
                "total_output_kg", new java.math.BigDecimal("90.000"), "waste_kg", new java.math.BigDecimal("5.000"),
                "shrinkage_kg", new java.math.BigDecimal("5.000"), "end_time", NOW);
        assertThatThrownBy(() -> insert("transformations", noReason)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("transformations", with(noReason, "waste_reason", "   "))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insert("transformations", with(noReason, "waste_reason", "Recorte"))).doesNotThrowAnyException();
        // sin desperdicio no hace falta motivo
        assertThatCode(() -> insert("transformations", with(noReason, "id", UUID.randomUUID(), "waste_kg", java.math.BigDecimal.ZERO,
                "shrinkage_kg", new java.math.BigDecimal("10.000")))).doesNotThrowAnyException();
    }

    @Test
    void aBatchIsConsumedByOneTransformationAndBornFromOne() {
        UUID machine = insertMachine(COMPANY_A, "MQ-01");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        insert("transformations", with(transformationRow(machine), "id", first));
        insert("transformations", with(transformationRow(machine), "id", second));
        UUID batch = insertBatch();
        insert("transformation_inputs", Map.of("transformation_id", first, "position", 0, "batch_id", batch, "quantity_kg", new java.math.BigDecimal("10")));

        assertThatThrownBy(() -> insert("transformation_inputs", Map.of("transformation_id", second, "position", 0, "batch_id", batch,
                "quantity_kg", new java.math.BigDecimal("10")))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_transformation_inputs_batch");

        UUID output = insertBatch();
        insert("transformation_outputs", Map.of("transformation_id", first, "position", 0, "batch_id", output));
        assertThatThrownBy(() -> insert("transformation_outputs", Map.of("transformation_id", second, "position", 0, "batch_id", output)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ux_transformation_outputs_batch");
        assertThatThrownBy(() -> insert("transformation_inputs", Map.of("transformation_id", second, "position", 0, "batch_id", UUID.randomUUID(),
                "quantity_kg", new java.math.BigDecimal("10")))).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- transfers ----

    @Test
    void aTransferNeedsTwoDifferentCompanies() {
        assertThatCode(() -> insert("transfers", transferRow())).doesNotThrowAnyException();
        assertThatThrownBy(() -> insert("transfers", with(transferRow(), "target_company_id", COMPANY_A)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTransferResolutionIsConsistentWithItsStatus() {
        // PENDING no tiene resolución
        assertThatThrownBy(() -> insert("transfers", with(transferRow(), "resolved_at", NOW, "resolved_by", USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // ACCEPTED exige quién y cuándo, y no lleva motivo
        assertThatThrownBy(() -> insert("transfers", with(transferRow(), "status", "ACCEPTED"))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("transfers", with(transferRow(), "status", "ACCEPTED", "resolved_at", NOW, "resolved_by", USER,
                "rejection_reason", "no"))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insert("transfers", with(transferRow(), "status", "ACCEPTED", "resolved_at", NOW, "resolved_by", USER)))
                .doesNotThrowAnyException();
        // REJECTED exige motivo no vacío
        assertThatThrownBy(() -> insert("transfers", with(transferRow(), "status", "REJECTED", "resolved_at", NOW, "resolved_by", USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("transfers", with(transferRow(), "status", "REJECTED", "resolved_at", NOW, "resolved_by", USER,
                "rejection_reason", "  "))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insert("transfers", with(transferRow(), "status", "REJECTED", "resolved_at", NOW, "resolved_by", USER,
                "rejection_reason", "Calidad insuficiente"))).doesNotThrowAnyException();
    }

    @Test
    void transferBatchesAreUniquePerTransferAndNeedTheirBatch() {
        Map<String, Object> transfer = transferRow();
        insert("transfers", transfer);
        UUID batch = insertBatch();
        insert("transfer_batches", Map.of("transfer_id", transfer.get("id"), "position", 0, "batch_id", batch));

        assertThatThrownBy(() -> insert("transfer_batches", Map.of("transfer_id", transfer.get("id"), "position", 1, "batch_id", batch)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("transfer_batches", Map.of("transfer_id", transfer.get("id"), "position", 1, "batch_id", UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- phase_command_log, company_projection ----

    @Test
    void aPhaseCommandIsRegisteredOncePerBatchRequestAndOperation() {
        UUID batch = insertBatch();
        Map<String, Object> command = Map.of("batch_id", batch, "request_id", "control-1", "operation", "START_EVALUATION",
                "result_phase", "IN_QUALITY_CONTROL", "result_quality_hold", false, "created_at", NOW);
        insert("phase_command_log", command);

        assertThatThrownBy(() -> insert("phase_command_log", command)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insert("phase_command_log", with(command, "operation", "DISCARD"))).as("otra operación").doesNotThrowAnyException();
        assertThatCode(() -> insert("phase_command_log", with(command, "request_id", "control-2"))).as("otro requestId").doesNotThrowAnyException();
        assertThatThrownBy(() -> insert("phase_command_log", with(command, "request_id", "x", "operation", "TELEPORT")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("phase_command_log", with(command, "batch_id", UUID.randomUUID(), "request_id", "y")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void companyProjectionStatusMustBeKnown() {
        insert("company_projection", Map.of("company_id", COMPANY_A, "business_name", "Hilos SAC", "status", "ACTIVE", "updated_at", NOW));
        assertThatThrownBy(() -> insert("company_projection", Map.of("company_id", COMPANY_B, "business_name", "X", "status", "SUSPENDED", "updated_at", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outboxStatusIsPendingOrPublished() {
        String insert = """
                INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, topic, payload, status, created_at)
                VALUES (:id, 'Batch', 'x', 'BatchRegistered', 'production.batch', '{}'::jsonb, :status, :createdAt)
                """;
        assertThatCode(() -> jdbc.sql(insert).param("id", UUID.randomUUID()).param("status", "PENDING").param("createdAt", NOW).update())
                .doesNotThrowAnyException();
        assertThatCode(() -> jdbc.sql(insert).param("id", UUID.randomUUID()).param("status", "PUBLISHED").param("createdAt", NOW).update())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.sql(insert).param("id", UUID.randomUUID()).param("status", "FAILED").param("createdAt", NOW).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
