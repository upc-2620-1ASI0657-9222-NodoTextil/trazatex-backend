package com.nodotextil.trazatex.quality.infrastructure.web;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.AddTestCommand;
import com.nodotextil.trazatex.quality.application.dto.ControlReportDocument;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;
import com.nodotextil.trazatex.quality.application.dto.FinishControlResult;
import com.nodotextil.trazatex.quality.application.dto.Role;
import com.nodotextil.trazatex.quality.application.dto.StartControlResult;
import com.nodotextil.trazatex.quality.application.port.in.AddQualityTestUseCase;
import com.nodotextil.trazatex.quality.application.port.in.FinishQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.port.in.GenerateControlReportUseCase;
import com.nodotextil.trazatex.quality.application.port.in.GetQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.port.in.StartQualityControlUseCase;
import com.nodotextil.trazatex.quality.infrastructure.web.security.RequiresRole;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/quality")
class QualityControlController {

    static final String POLL_AFTER_SECONDS = "5";

    private final StartQualityControlUseCase start;
    private final GetQualityControlUseCase get;
    private final AddQualityTestUseCase tests;
    private final FinishQualityControlUseCase finish;
    private final GenerateControlReportUseCase reports;

    QualityControlController(StartQualityControlUseCase start, GetQualityControlUseCase get, AddQualityTestUseCase tests, FinishQualityControlUseCase finish,
                             GenerateControlReportUseCase reports) {
        this.start = start;
        this.get = get;
        this.tests = tests;
        this.finish = finish;
        this.reports = reports;
    }

    @PostMapping("/batches/{batchId}/controls")
    @RequiresRole(Role.COMPANY_ADMIN)
    ResponseEntity<ControlResult> start(@PathVariable UUID batchId, @RequestBody(required = false) StartControlRequest request, Actor actor) {
        StartControlResult result = start.start(batchId, actor, request == null ? null : request.internalObservations());
        String location = "/api/quality/controls/" + result.control().id();
        if (result.confirmed()) {
            return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.LOCATION, location).body(result.control());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.RETRY_AFTER, POLL_AFTER_SECONDS).body(result.control());
    }

    @GetMapping("/controls/{controlId}")
    @RequiresRole(Role.COMPANY_ADMIN)
    ControlResult get(@PathVariable UUID controlId, Actor actor) {
        return get.get(controlId, actor);
    }

    @PostMapping("/controls/{controlId}/tests")
    @RequiresRole(Role.COMPANY_ADMIN)
    ResponseEntity<ControlResult.TestView> addTest(@PathVariable UUID controlId, @RequestBody AddTestCommand request, Actor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tests.addTest(controlId, request, actor));
    }

    @PostMapping("/controls/{controlId}/finish")
    @RequiresRole(Role.COMPANY_ADMIN)
    ResponseEntity<FinishControlResult> finish(@PathVariable UUID controlId, Actor actor) {
        FinishControlResult result = finish.finish(controlId, actor);
        if (result.completed()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).header(HttpHeaders.LOCATION, "/api/quality/controls/" + controlId)
                .header(HttpHeaders.RETRY_AFTER, POLL_AFTER_SECONDS).body(result);
    }

    @GetMapping("/controls/{controlId}/report")
    @RequiresRole(Role.COMPANY_ADMIN)
    ResponseEntity<byte[]> report(@PathVariable UUID controlId, Actor actor) {
        ControlReportDocument document = reports.generate(controlId, actor);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(document.filename()).build().toString())
                .contentLength(document.pdf().length).body(document.pdf());
    }
}
