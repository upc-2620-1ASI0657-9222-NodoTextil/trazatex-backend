package com.nodotextil.trazatex.production.application.dto;

/** Lo que devuelve la consulta por QR: el lote completo si es de la propia empresa, la vista compartida si es ajeno. */
public sealed interface QrView permits BatchResult, SharedBatchResult {
}
