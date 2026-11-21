package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.ImpactedBatch;

public record ClaimedHold(ImpactedBatch impacted, int attempts) {
}
