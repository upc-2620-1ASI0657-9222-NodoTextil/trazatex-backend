package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityFailure;

record ControlResolution(Kind kind, QualityControl control, QualityFailure failure, int httpStatus, String code, String message) {

    enum Kind {
        CONFIRMED,
        PENDING,
        REJECTED,
        DEFECT,
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
