package com.nodotextil.trazatex.traceability.application.port.out;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;

import java.util.UUID;

/**
 * Escritura del grafo de genealogía. Se usa siempre dentro de la transacción del evento (la que abre
 * {@code IdempotentEventHandler}) y cada método es idempotente y no depende del orden de llegada:
 * <ul>
 *   <li><b>Nodos provisionales.</b> Un lote que un evento nombra pero cuyo evento de creación no ha llegado se crea solo con
 *       su {@code batchId} y {@code provisional = true}; su propio evento de creación lo completa.</li>
 *   <li><b>Datos de identidad</b> (códigos, material, composición, cantidad, origen, proveedor, recepción, fecha, producto
 *       final) no cambian nunca después de crear el lote: se completan solo si faltan y <b>nunca se sobrescriben</b>. Si un evento
 *       trae un valor distinto del guardado se conserva el guardado y se registra un ERROR con el {@code eventId}, el id y el
 *       campo; el resto del evento se aplica.</li>
 *   <li><b>Fase, retención y dueño</b> cambian solo con {@code BatchPhaseChanged}, y solo si su {@code batchVersion} es mayor
 *       que la guardada. Los eventos de creación ponen el estado inicial de lo que nace <b>solo si el nodo aún no tiene
 *       {@code batchVersion}</b>; el {@code qualityStatus} inicial solo si aún no hay uno.</li>
 * </ul>
 */
public interface LineageGraphPort {

    void registerBatch(RegisterBatchCommand command);

    void applyBatchSplit(ApplyBatchSplitCommand command);

    void applyTransformationCompleted(ApplyTransformationCompletedCommand command);

    /** @return {@code true} si se aplicó; {@code false} si la versión no era mayor que la guardada */
    boolean applyBatchPhaseChanged(ApplyBatchPhaseChangedCommand command);

    void applyTransferAccepted(ApplyTransferAcceptedCommand command);

    void applyQualityStatus(ApplyQualityStatusCommand command);

    void saveCompanyPrivacy(UUID companyId, CompanyPrivacy privacy);

    /**
     * Completa el nombre de la empresa: {@code coalesce(guardado, nuevo)}, como los datos de identidad, y <b>sin tocar su privacidad</b>
     * (el mismo {@code :Company} puede haberse creado antes por un lote o por {@code CompanyPrivacyUpdated}, en cualquier orden). Un nombre
     * distinto del guardado se conserva y se registra un ERROR; el evento no va a la DLT.
     */
    void registerCompany(ApplyCompanyRegisteredCommand command);
}
