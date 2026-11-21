package com.nodotextil.trazatex.quality.infrastructure.web;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.DiscardFailureResult;
import com.nodotextil.trazatex.quality.application.dto.FailureInfoCommand;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;
import com.nodotextil.trazatex.quality.application.dto.Role;
import com.nodotextil.trazatex.quality.application.port.in.ManageFailuresUseCase;
import com.nodotextil.trazatex.quality.infrastructure.web.security.RequiresRole;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/quality/failures")
class QualityFailureController {

    private final ManageFailuresUseCase failures;

    QualityFailureController(ManageFailuresUseCase failures) {
        this.failures = failures;
    }

    @GetMapping("/{failureId}")
    @RequiresRole(Role.COMPANY_ADMIN)
    FailureResult get(@PathVariable UUID failureId, Actor actor) {
        return failures.get(failureId, actor);
    }

    @PutMapping("/{failureId}")
    @RequiresRole(Role.COMPANY_ADMIN)
    FailureResult complete(@PathVariable UUID failureId, @RequestBody FailureInfoCommand request, Actor actor) {
        return failures.complete(failureId, request, actor);
    }

    @PostMapping("/{failureId}/reevaluation")
    @RequiresRole(Role.COMPANY_ADMIN)
    FailureResult reevaluate(@PathVariable UUID failureId, Actor actor) {
        return failures.reevaluate(failureId, actor);
    }

    @PostMapping("/{failureId}/discard")
    @RequiresRole(Role.COMPANY_ADMIN)
    ResponseEntity<DiscardFailureResult> discard(@PathVariable UUID failureId, Actor actor) {
        DiscardFailureResult result = failures.discard(failureId, actor);
        if (result.completed()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).header(HttpHeaders.LOCATION, "/api/quality/failures/" + failureId)
                .header(HttpHeaders.RETRY_AFTER, QualityControlController.POLL_AFTER_SECONDS).body(result);
    }
}
