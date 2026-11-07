package com.nodotextil.trazatex.quality.infrastructure.web;

/** Cuerpo opcional de {@code POST /api/quality/batches/{batchId}/controls}: observaciones internas (siempre privadas). */
record StartControlRequest(String internalObservations) {
}
