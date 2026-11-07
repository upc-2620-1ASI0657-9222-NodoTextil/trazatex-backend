package com.nodotextil.trazatex.quality.domain.model;

/** Lo que cambió en el estado de calidad de un lote: con él se decide si hay que publicar {@code QualityStatusChanged}. */
public record QualityStatusChange(QualityStatus previous, QualityStatus current) {

    public boolean changed() {
        return previous != current;
    }
}
