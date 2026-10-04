package com.nodotextil.trazatex.traceability.infrastructure.web;

import com.nodotextil.trazatex.traceability.application.GetSharedLineageUseCase;
import com.nodotextil.trazatex.traceability.application.LineageDirection;
import com.nodotextil.trazatex.traceability.application.SearchLineageUseCase;
import com.nodotextil.trazatex.traceability.application.view.LineageView;
import com.nodotextil.trazatex.traceability.application.view.LotView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traceability")
public class TraceabilityController {

    private final GetSharedLineageUseCase getSharedLineageUseCase;
    private final SearchLineageUseCase searchLineageUseCase;

    public TraceabilityController(
            GetSharedLineageUseCase getSharedLineageUseCase,
            SearchLineageUseCase searchLineageUseCase) {
        this.getSharedLineageUseCase = getSharedLineageUseCase;
        this.searchLineageUseCase = searchLineageUseCase;
    }

    @GetMapping("/lineages")
    public List<LotView> search(@RequestParam String qrCode, @AuthenticationPrincipal Jwt jwt) {
        return searchLineageUseCase.byQrCode(qrCode, companyOf(jwt));
    }

    @GetMapping("/batches/{batchId}/ancestors")
    public LineageView ancestors(@PathVariable UUID batchId, @AuthenticationPrincipal Jwt jwt) {
        return getSharedLineageUseCase.execute(batchId, companyOf(jwt), LineageDirection.BACKWARD);
    }

    @GetMapping("/batches/{batchId}/descendants")
    public LineageView descendants(@PathVariable UUID batchId, @AuthenticationPrincipal Jwt jwt) {
        return getSharedLineageUseCase.execute(batchId, companyOf(jwt), LineageDirection.FORWARD);
    }

    @GetMapping("/batches/{batchId}/lineage")
    public LineageView lineage(@PathVariable UUID batchId, @AuthenticationPrincipal Jwt jwt) {
        return getSharedLineageUseCase.execute(batchId, companyOf(jwt), LineageDirection.FULL);
    }

    private static UUID companyOf(Jwt jwt) {
        String companyId = jwt.getClaimAsString("companyId");
        return companyId == null ? null : UUID.fromString(companyId);
    }
}