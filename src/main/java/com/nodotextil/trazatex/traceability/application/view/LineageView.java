package com.nodotextil.trazatex.traceability.application.view;

import com.nodotextil.trazatex.traceability.domain.LineageEdge;

import java.util.List;
import java.util.UUID;

public record LineageView(UUID rootBatchId, List<LotView> lots, List<LineageEdge> edges) {
}
