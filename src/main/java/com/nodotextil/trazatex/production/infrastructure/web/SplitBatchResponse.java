package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.SplitBatchUseCase;
import java.util.List;

public record SplitBatchResponse(BatchResponse parent, List<BatchResponse> children) {

    static SplitBatchResponse from(SplitBatchUseCase.Result result) {
        return new SplitBatchResponse(
                BatchResponse.from(result.parent()),
                result.children().stream().map(BatchResponse::from).toList());
    }
}
