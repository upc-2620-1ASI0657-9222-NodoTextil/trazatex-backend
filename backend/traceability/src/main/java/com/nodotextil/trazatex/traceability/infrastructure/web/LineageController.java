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

/**
 * US30–US32 y US36. Solo lectura. Cada respuesta ya pasó por la política de privacidad (lo exige ArchUnit: un handler devuelve solo tipos de
 * {@code domain.model.view}). OPERATOR y COMPANY_ADMIN; cualquier usuario autenticado de una empresa puede abrir cualquier genealogía y ve de las
 * otras solo lo siempre compartido.
 */
@RestController
@RequestMapping("/api/traceability")
class LineageController {

    private final QueryLineageUseCase lineages;

    LineageController(QueryLineageUseCase lineages) {
        this.lineages = lineages;
    }

    /**
     * US30. {@code qrCode} exacto (de cualquier empresa); {@code traceabilityId} por prefijo solo entre los lotes de la propia empresa y, en los de
     * otra, solo exacto; con ambos deben cumplirse los dos; al menos uno. {@code size} máximo 100 (si piden más se usa 100); orden:
     * {@code createdAt} descendente, luego id.
     */
    @GetMapping("/lineages")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    VisibleBatchPage search(@RequestParam(required = false) String qrCode, @RequestParam(required = false) String traceabilityId,
                            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return lineages.search(actor.companyId(), qrCode, traceabilityId, page, size);
    }

    /** US31: los lotes y relaciones que dieron origen al lote, hasta 10 niveles. */
    @GetMapping("/batches/{batchId}/ancestors")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    VisibleLineage ancestors(@PathVariable UUID batchId, Actor actor) {
        return lineages.ancestors(batchId, actor.companyId());
    }

    /** US32: los lotes derivados y sus relaciones, hasta 10 niveles. */
    @GetMapping("/batches/{batchId}/descendants")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    VisibleLineage descendants(@PathVariable UUID batchId, Actor actor) {
        return lineages.descendants(batchId, actor.companyId());
    }

    /** US36: ambas direcciones, con el historial de transferencias de cada lote. */
    @GetMapping("/batches/{batchId}/lineage")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    VisibleLineage lineage(@PathVariable UUID batchId, Actor actor) {
        return lineages.lineage(batchId, actor.companyId());
    }
}
