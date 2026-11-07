package com.nodotextil.trazatex.quality.domain.model;

/** Estado del control (docs/domain/glossary.md): {@code STARTING} es INICIANDO y {@code IN_PROGRESS} es EN_CURSO. */
public enum ControlStatus {
    STARTING,
    IN_PROGRESS,
    FINISHED,
    CANCELLED;

    /** Un control activo bloquea iniciar otro sobre el mismo lote. */
    public boolean active() {
        return this == STARTING || this == IN_PROGRESS;
    }
}
