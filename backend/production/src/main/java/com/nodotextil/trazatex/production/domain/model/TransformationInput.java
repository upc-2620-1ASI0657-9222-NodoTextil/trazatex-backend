package com.nodotextil.trazatex.production.domain.model;

import java.util.UUID;

public record TransformationInput(UUID batchId, Quantity quantity) {
}
