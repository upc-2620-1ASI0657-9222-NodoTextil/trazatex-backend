package com.nodotextil.trazatex.traceability.domain.model;

/** Estado de calidad del lote (glosario). Lo gobierna Quality; aquí solo se refleja. */
public enum QualityStatus {
    NOT_REVIEWED,
    CONFORMING,
    FAILED,
    PENDING_REEVALUATION,
    POTENTIAL_DERIVED_FAILURE
}
