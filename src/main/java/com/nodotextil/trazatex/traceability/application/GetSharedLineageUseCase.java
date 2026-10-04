package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.application.port.LotInfo;
import com.nodotextil.trazatex.traceability.application.port.PrivacySettingsPort;
import com.nodotextil.trazatex.traceability.application.view.LineageView;
import com.nodotextil.trazatex.traceability.application.view.LotView;
import com.nodotextil.trazatex.traceability.domain.LineageGraph;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

public class GetSharedLineageUseCase {

    private final GetLineageUseCase getLineageUseCase;
    private final BatchInfoPort batchInfo;
    private final PrivacySettingsPort privacySettings;
    private final OrganizationAccess organizationAccess;

    public GetSharedLineageUseCase(
            GetLineageUseCase getLineageUseCase,
            BatchInfoPort batchInfo,
            PrivacySettingsPort privacySettings,
            OrganizationAccess organizationAccess) {
        this.getLineageUseCase = getLineageUseCase;
        this.batchInfo = batchInfo;
        this.privacySettings = privacySettings;
        this.organizationAccess = organizationAccess;
    }

    public LineageView execute(UUID batchId, UUID requesterCompanyId, LineageDirection direction) {
        requireSameChain(batchId, requesterCompanyId);
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

    private void requireSameChain(UUID batchId, UUID requesterCompanyId) {
        LotInfo lot = batchInfo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        if (requesterCompanyId == null
                || !organizationAccess.companiesShareLicense(requesterCompanyId, lot.responsibleCompanyId())) {
            throw new AccessDeniedException("The batch does not belong to your traceability chain");
        }
    }
}
