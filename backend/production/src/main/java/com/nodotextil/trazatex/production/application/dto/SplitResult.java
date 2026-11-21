package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.BatchFactory;

import java.util.List;

public record SplitResult(BatchResult parent, List<BatchResult> children) {

    public static SplitResult from(BatchFactory.SplitResult result) {
        return new SplitResult(BatchResult.from(result.parent()), result.children().stream().map(BatchResult::from).toList());
    }
}
