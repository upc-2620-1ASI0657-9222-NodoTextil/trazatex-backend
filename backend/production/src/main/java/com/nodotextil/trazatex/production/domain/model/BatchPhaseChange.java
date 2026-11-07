package com.nodotextil.trazatex.production.domain.model;

import java.util.UUID;

/**
 * Un cambio de fase o de retención por calidad de un lote ya existente. {@link Batch} lo anota en el único punto donde esos
 * dos valores cambian, así que toda operación —presente o futura— lo deja registrado y la capa de aplicación lo publica como
 * {@code BatchPhaseChanged} (docs/architecture/communication.md). {@code companyId} y {@code qualityHold} son los valores
 * posteriores al cambio; si solo cambió la retención, {@code previousPhase} y {@code newPhase} son iguales.
 */
public record BatchPhaseChange(UUID batchId, UUID companyId, BatchPhase previousPhase, BatchPhase newPhase, boolean qualityHold) {
}
