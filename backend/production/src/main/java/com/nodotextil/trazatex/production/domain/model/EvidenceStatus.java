package com.nodotextil.trazatex.production.domain.model;

/** {@code PENDING}: aún no está en Cloudinary (sus bytes esperan en la cola). {@code FAILED}: no se pudo subir; los bytes se conservan. */
public enum EvidenceStatus {
    PENDING,
    UPLOADED,
    FAILED
}
