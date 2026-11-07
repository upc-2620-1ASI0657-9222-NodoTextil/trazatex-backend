package com.nodotextil.trazatex.production.application.dto;

import java.util.List;

public record CompleteTransformationResult(TransformationResult transformation, List<BatchResult> outputs) {
}
