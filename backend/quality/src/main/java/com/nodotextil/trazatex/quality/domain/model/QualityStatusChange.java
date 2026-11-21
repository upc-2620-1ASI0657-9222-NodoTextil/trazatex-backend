package com.nodotextil.trazatex.quality.domain.model;

public record QualityStatusChange(QualityStatus previous, QualityStatus current) {

    public boolean changed() {
        return previous != current;
    }
}
