package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.QualityFailure;

public record ClaimedDiscard(QualityFailure failure, int attempts) {
}
