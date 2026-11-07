package com.nodotextil.trazatex.traceability.domain.model.view;

import java.util.List;

/** Una página de resultados de búsqueda. */
public record VisibleBatchPage(List<VisibleBatchSummary> items, int page, int size, long totalElements, int totalPages) implements VisibleView {
}
