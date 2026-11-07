package com.nodotextil.trazatex.quality.infrastructure.web;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.BatchQualityResult;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;
import com.nodotextil.trazatex.quality.application.dto.Role;
import com.nodotextil.trazatex.quality.application.port.in.ListQualityUseCase;
import com.nodotextil.trazatex.quality.infrastructure.web.security.RequiresRole;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Los listados de la empresa, paginados. Solo llama al caso de uso (regla 5 de CLAUDE.md). */
@RestController
@RequestMapping("/api/quality")
class QualityQueryController {
    private final ListQualityUseCase listing;

    QualityQueryController(ListQualityUseCase listing) {
        this.listing = listing;
    }

    @GetMapping("/controls")
    @RequiresRole(Role.COMPANY_ADMIN)
    PageResult<ControlResult> controls(Actor actor, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return listing.controls(page, size, actor);
    }

    @GetMapping("/batches")
    @RequiresRole(Role.COMPANY_ADMIN)
    PageResult<BatchQualityResult> batches(Actor actor, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return listing.batches(page, size, actor);
    }

    @GetMapping("/failures")
    @RequiresRole(Role.COMPANY_ADMIN)
    PageResult<FailureResult> failures(Actor actor, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return listing.failures(page, size, actor);
    }
}
