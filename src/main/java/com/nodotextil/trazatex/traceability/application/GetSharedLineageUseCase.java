package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.application.port.LotInfo;
import com.nodotextil.trazatex.traceability.application.port.PrivacySettingsPort;
import com.nodotextil.trazatex.traceability.application.view.LineageView;
import com.nodotextil.trazatex.traceability.application.view.LotView;
import com.nodotextil.trazatex.traceability.domain.LineageGraph;

import java.util.*;

public class GetSharedLineageUseCase {

    private final GetLineageUseCase getLineageUseCase;
    private final BatchInfoPort batchInfo;
    private final PrivacySettingsPort privacySettings;

    public GetSharedLineageUseCase(
            GetLineageUseCase getLineageUseCase,
            BatchInfoPort batchInfo,
            PrivacySettingsPort privacySettings) {
        this.getLineageUseCase = getLineageUseCase;
        this.batchInfo = batchInfo;
        this.privacySettings = privacySettings;
    }

    public LineageView execute(UUID batchId, UUID requesterCompanyId, LineageDirection direction) {
        LineageGraph graph = getLineageUseCase.execute(batchId, direction);
        Map<UUID, LotInfo> lots = batchInfo.findAllByIds(graph.batchIds());

        Map<UUID, Set<String>> sharedFieldsByCompany = new HashMap<>();
        List<LotView> views = lots.values().stream()
                .map(lot -> LotViewAssembler.assemble(
                        lot,
                        requesterCompanyId,
                        sharedFieldsByCompany.computeIfAbsent(
                                lot.responsibleCompanyId(),
                                privacySettings::sharedConfigurableFields)))
                .toList();

        return new LineageView(batchId, views, List.copyOf(graph.edges()));
    }
}
