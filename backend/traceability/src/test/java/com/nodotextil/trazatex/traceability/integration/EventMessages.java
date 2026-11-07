package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los mensajes tal como los publican los demás servicios: el sobre de {@code communication.md} con el payload del catálogo,
 * escrito a mano (con los campos que traceability no usa, como {@code previousPhase}, {@code reason} o {@code machineId}, para
 * comprobar que se ignoran). No usa ninguna clase de Production: es el contrato, no su implementación.
 */
public final class EventMessages {

    public static final ObjectMapper MAPPER = JsonMapper.builder().build();
    public static final Instant ENVELOPE_TIME = Instant.parse("2026-10-21T09:00:00Z");

    /** Un mensaje listo para publicar en {@code topic} con {@code key} = aggregateId. */
    public record Message(String topic, String key, UUID eventId, String type, String json) {
    }

    private EventMessages() {
    }

    public static Message of(String topic, String key, UUID eventId, String type, Map<String, Object> payload) {
        return new Message(topic, key, eventId, type, MAPPER.writeValueAsString(new BaseDomainEvent(eventId, type, key, 1, ENVELOPE_TIME, payload)));
    }

    public static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    public static List<Map<String, Object>> cotton() {
        return List.of(map("name", "Algodón", "percentage", 100.00));
    }

    public static Message batchRegistered(UUID eventId, UUID batchId, String traceabilityId, String qrCode, UUID companyId, String material, double kg,
                                   String origin, String supplier, String reception, Instant createdAt) {
        return of("production.batch", batchId.toString(), eventId, "BatchRegistered", map("batchId", batchId, "traceabilityId", traceabilityId,
                "qrCode", qrCode, "companyId", companyId, "materialType", material, "composition", cotton(), "quantityKg", kg, "origin", origin,
                "supplierName", supplier, "receptionCharacteristics", reception, "createdAt", createdAt));
    }

    public static Map<String, Object> splitChild(UUID batchId, String traceabilityId, String qrCode, String material, double kg, String origin,
                                          String supplier, String reception, String qualityStatus, Instant createdAt) {
        return map("batchId", batchId, "traceabilityId", traceabilityId, "qrCode", qrCode, "materialType", material, "quantityKg", kg,
                "composition", cotton(), "origin", origin, "supplierName", supplier, "receptionCharacteristics", reception,
                "qualityStatus", qualityStatus, "createdAt", createdAt);
    }

    public static Message batchSplit(UUID eventId, UUID parent, UUID companyId, List<Map<String, Object>> children) {
        return of("production.batch", parent.toString(), eventId, "BatchSplit", map("parentBatchId", parent, "companyId", companyId, "children", children));
    }

    public static Message phaseChanged(UUID eventId, UUID batchId, UUID companyId, String previousPhase, String newPhase, boolean hold, long version) {
        return of("production.batch", batchId.toString(), eventId, "BatchPhaseChanged", map("batchId", batchId, "companyId", companyId,
                "previousPhase", previousPhase, "newPhase", newPhase, "qualityHold", hold, "reason", "PRUEBA", "requestId", "req-" + version,
                "batchVersion", version));
    }

    public static Map<String, Object> transformationOutput(UUID batchId, String traceabilityId, String qrCode, String material, double kg, boolean finalProduct) {
        return map("batchId", batchId, "traceabilityId", traceabilityId, "qrCode", qrCode, "materialType", material, "quantityKg", kg,
                "composition", cotton(), "finalProduct", finalProduct);
    }

    public static Message transformationCompleted(UUID eventId, UUID transformation, String type, UUID companyId, String machineName, String machineType,
                                                  List<UUID> inputs, UUID output, String outputTrace, String outputQr, String material, double outputKg,
                                                  boolean finalProduct, double wasteKg, String wasteReason, double shrinkageKg, Instant started, Instant completed) {
        return transformationCompleted(eventId, transformation, type, companyId, machineName, machineType, inputs,
                List.of(transformationOutput(output, outputTrace, outputQr, material, outputKg, finalProduct)), wasteKg, wasteReason, shrinkageKg, started, completed);
    }

    public static Message transformationCompleted(UUID eventId, UUID transformation, String type, UUID companyId, String machineName, String machineType,
                                                  List<UUID> inputs, List<Map<String, Object>> outputs, double wasteKg, String wasteReason, double shrinkageKg,
                                                  Instant started, Instant completed) {
        List<Map<String, Object>> inputList = new ArrayList<>();
        inputs.forEach(input -> inputList.add(map("batchId", input, "quantityKg", 1.0)));
        return of("production.transformation", transformation.toString(), eventId, "TransformationCompleted", map("transformationId", transformation,
                "type", type, "companyId", companyId, "machineId", UUID.randomUUID(), "machineName", machineName, "machineType", machineType,
                "inputs", inputList, "outputs", outputs, "wasteKg", wasteKg, "wasteReason", wasteReason, "shrinkageKg", shrinkageKg,
                "startedAt", started, "completedAt", completed));
    }

    public static Message transferAccepted(UUID eventId, UUID transfer, List<UUID> batches, UUID from, UUID to, Instant acceptedAt) {
        return of("production.transfer", transfer.toString(), eventId, "TransferAccepted", map("transferId", transfer, "batchIds", batches,
                "fromCompanyId", from, "toCompanyId", to, "acceptedAt", acceptedAt));
    }

    public static Message qualityStatusChanged(UUID eventId, UUID batchId, UUID companyId, String previous, String next) {
        return of("quality.control", batchId.toString(), eventId, "QualityStatusChanged", map("batchId", batchId, "companyId", companyId,
                "previousStatus", previous, "newStatus", next, "controlId", UUID.randomUUID()));
    }

    public static Message companyRegistered(UUID eventId, UUID companyId, String businessName) {
        return of("orgaccess.company", companyId.toString(), eventId, "CompanyRegistered", map("companyId", companyId, "ruc", "20131312955", "businessName", businessName,
                "activities", List.of("SPINNING"), "status", "ACTIVE"));
    }

    public static Message companyPrivacyUpdated(UUID eventId, UUID companyId, Map<String, String> fields) {
        return of("orgaccess.company", companyId.toString(), eventId, "CompanyPrivacyUpdated", map("companyId", companyId, "fields", fields));
    }
}
