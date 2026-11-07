package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityFailure;

/** Cómo terminó un intento de resolver un comando de un control: lo que Production respondió y en qué quedó el control (y su falla, al finalizar). */
record ControlResolution(Kind kind, QualityControl control, QualityFailure failure, int httpStatus, String code, String message) {

    enum Kind {
        /** 200: el inicio pasó a {@code IN_PROGRESS}, o la finalización a {@code FINISHED}. */
        CONFIRMED,
        /** Sin respuesta: el control sigue como estaba y se reintentará. */
        PENDING,
        /** 409 o 404: Production no lo aplicó. */
        REJECTED,
        /** 400 (u otro 4xx): defecto de Quality; Production no lo aplicó y se registró un ERROR. */
        DEFECT,
        /** Otro (el job, o una repetición) ya lo había resuelto: se informa tal como quedó. */
        ALREADY_RESOLVED
    }

    static ControlResolution confirmed(QualityControl control) {
        return new ControlResolution(Kind.CONFIRMED, control, null, 200, null, null);
    }

    static ControlResolution confirmed(QualityControl control, QualityFailure failure) {
        return new ControlResolution(Kind.CONFIRMED, control, failure, 200, null, null);
    }

    static ControlResolution pending(QualityControl control) {
        return new ControlResolution(Kind.PENDING, control, null, 0, null, null);
    }

    static ControlResolution rejected(QualityControl control, int status, String code, String message) {
        return new ControlResolution(Kind.REJECTED, control, null, status, code, message);
    }

    static ControlResolution defect(QualityControl control, int status, String code, String message) {
        return new ControlResolution(Kind.DEFECT, control, null, status, code, message);
    }

    static ControlResolution alreadyResolved(QualityControl control) {
        return new ControlResolution(Kind.ALREADY_RESOLVED, control, null, 0, null, null);
    }
}
