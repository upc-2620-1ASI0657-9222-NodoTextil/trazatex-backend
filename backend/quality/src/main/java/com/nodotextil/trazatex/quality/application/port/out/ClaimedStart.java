package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.QualityControl;

public record ClaimedStart(QualityControl control, int attempts) {
}
