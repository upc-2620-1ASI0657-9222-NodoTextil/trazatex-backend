package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.CompleteTransformationUseCase;
import java.util.List;

public record CompleteTransformationResponse(
        TransformationResponse transformation,
        List<BatchResponse> outputs) {

    static CompleteTransformationResponse from(CompleteTransformationUseCase.Result result) {
        return new CompleteTransformationResponse(
                TransformationResponse.from(result.transformation()),
                result.outputs().stream().map(BatchResponse::from).toList());
    }
}
