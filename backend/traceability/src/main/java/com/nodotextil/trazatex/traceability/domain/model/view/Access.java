package com.nodotextil.trazatex.traceability.domain.model.view;

/** Si el nodo es de la empresa que consulta ({@link #OWN}: lo ve completo) o de otra ({@link #SHARED}: solo lo que comparte). */
public enum Access {
    OWN,
    SHARED
}
