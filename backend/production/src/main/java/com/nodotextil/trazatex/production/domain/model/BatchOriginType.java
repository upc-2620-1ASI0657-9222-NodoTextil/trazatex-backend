package com.nodotextil.trazatex.production.domain.model;

/** Cómo nació el lote (docs/domain/production.md): registrado, hijo de una división o salida de una transformación. */
public enum BatchOriginType {
    REGISTERED,
    SPLIT,
    TRANSFORMATION
}
