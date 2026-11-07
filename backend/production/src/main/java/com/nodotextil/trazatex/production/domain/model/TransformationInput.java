package com.nodotextil.trazatex.production.domain.model;

import java.util.UUID;

/** Un lote que entra a la transformación; se consume completo (para usar una parte, primero se divide). */
public record TransformationInput(UUID batchId, Quantity quantity) {
}
