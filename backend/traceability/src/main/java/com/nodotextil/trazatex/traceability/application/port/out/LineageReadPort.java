package com.nodotextil.trazatex.traceability.application.port.out;

import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransferRecord;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransformationData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura del grafo para las consultas. Devuelve datos <b>sin filtrar</b> (pueden ser de cualquier empresa e incompletos): solo
 * {@code LineageAuthorizationPolicy} decide qué se puede devolver. El recorrido es nivel por nivel: cada llamada de
 * {@link #parentsOf} o {@link #childrenOf} resuelve un nivel entero para toda la frontera a la vez, con un número fijo de consultas a la
 * base (no un patrón de longitud variable que enumere caminos).
 */
public interface LineageReadPort {

    /**
     * Un salto de lote a lote desde un lote de la frontera: el vecino, y la transformación por la que se llega ({@code via}) o {@code null}
     * si es una división ({@code SPLIT_INTO}). El vecino y la transformación pueden estar incompletos.
     */
    record Link(UUID frontierBatchId, BatchData neighbor, TransformationData via) {
    }

    /** Una página de lotes y el total que cumple el criterio (con {@code limit = 0} solo interesa el total). */
    record BatchPage(List<BatchData> items, long total) {
    }

    /** El lote aunque esté incompleto; vacío si no existe. */
    Optional<BatchData> findBatch(UUID batchId);

    /** Un nivel hacia atrás: por cada lote de la frontera, los lotes que lo dividieron o que entraron a la transformación que lo produjo. */
    List<Link> parentsOf(Collection<UUID> batchIds);

    /** Un nivel hacia adelante: por cada lote de la frontera, sus hijos y las salidas de las transformaciones en las que entró. */
    List<Link> childrenOf(Collection<UUID> batchIds);

    /** El historial de transferencias de cada lote (puede faltar la clave si no tiene). */
    Map<UUID, List<TransferRecord>> transfersOf(Collection<UUID> batchIds);

    /** El nombre de cada empresa que se conoce (las que aún no llegaron de {@code CompanyRegistered} faltan). */
    Map<UUID, String> businessNames(Collection<UUID> companyIds);

    /** La privacidad B2B vigente de cada empresa que la configuró (las que faltan son todo {@code PRIVATE}). Una sola consulta por petición. */
    Map<UUID, CompanyPrivacy> privacyOf(Collection<UUID> companyIds);

    /** Coincidencia exacta con el QR, de cualquier empresa. */
    Optional<BatchData> findByQrCode(String qrCode);

    /** Coincidencia exacta con el Traceability ID, de cualquier empresa. */
    Optional<BatchData> findByTraceabilityId(String traceabilityId);

    /**
     * Los lotes <b>de esa empresa</b> cuyo Traceability ID empieza por {@code prefix}, completos, ordenados por fecha de creación
     * descendente y, a igual fecha, por id. Nunca de otra empresa.
     */
    BatchPage searchByTraceabilityPrefix(UUID companyId, String prefix, int offset, int limit);
}
