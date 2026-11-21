package com.nodotextil.trazatex.traceability.infrastructure.web;

import com.nodotextil.trazatex.traceability.application.port.in.QueryLineageUseCase;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchPage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;
import com.nodotextil.trazatex.traceability.infrastructure.web.security.Actor;
import com.nodotextil.trazatex.traceability.infrastructure.web.security.RequiresRole;
import com.nodotextil.trazatex.traceability.infrastructure.web.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/traceability")
class LineageController {

    private final QueryLineageUseCase lineages;

    LineageController(QueryLineageUseCase lineages) {
        this.lineages = lineages;
    }

    @GetMapping("/lineages")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    VisibleBatchPage search(@RequestParam(required = false) String qrCode, @RequestParam(required = false) String traceabilityId,
                            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return lineages.search(actor.companyId(), qrCode, traceabilityId, page, size);
    }

    @GetMapping("/batches/{batchId}/ancestors")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    VisibleLineage ancestors(@PathVariable UUID batchId, Actor actor) {
        return lineages.ancestors(batchId, actor.companyId());
    }

    @GetMapping("/batches/{batchId}/descendants")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    VisibleLineage descendants(@PathVariable UUID batchId, Actor actor) {
        return lineages.descendants(batchId, actor.companyId());
    }

    @GetMapping("/batches/{batchId}/lineage")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    VisibleLineage lineage(@PathVariable UUID batchId, Actor actor) {
        return lineages.lineage(batchId, actor.companyId());
    }
}
