package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.BatchFactory;

import java.util.List;

/** El padre ya {@code SPLIT} y sus hijos nuevos, en el orden pedido. */
public record SplitResult(BatchResult parent, List<BatchResult> children) {

    public static SplitResult from(BatchFactory.SplitResult result) {
        return new SplitResult(BatchResult.from(result.parent()), result.children().stream().map(BatchResult::from).toList());
    }
}
