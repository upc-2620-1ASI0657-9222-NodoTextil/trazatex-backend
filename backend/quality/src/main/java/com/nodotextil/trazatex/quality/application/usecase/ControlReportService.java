package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.ControlReport;
import com.nodotextil.trazatex.quality.application.dto.ControlReportDocument;
import com.nodotextil.trazatex.quality.application.port.in.GenerateControlReportUseCase;
import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.PdfGeneratorPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.ControlStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * US44. Solo un control {@code FINISHED} de la propia empresa (otra empresa es «no existe»). El PDF se genera en memoria <b>fuera de toda transacción</b>
 * (puede tardar) y no se guarda en ningún lado; lo único que queda es una fila de auditoría ({@code REPORT_GENERATED}: quién y de qué control, no el
 * contenido), que solo se escribe si la generación salió bien.
 */
@Service
class ControlReportService implements GenerateControlReportUseCase {

    static final String REPORT_GENERATED = "REPORT_GENERATED";

    private final QualityControlRepositoryPort controls;
    private final BatchProjectionRepositoryPort projections;
    private final PdfGeneratorPort pdf;
    private final AuditLogPort audit;
    private final Clock clock;
    private final TransactionTemplate transaction;

    ControlReportService(QualityControlRepositoryPort controls, BatchProjectionRepositoryPort projections, PdfGeneratorPort pdf, AuditLogPort audit, Clock clock,
                         PlatformTransactionManager transactionManager) {
        this.controls = controls;
        this.projections = projections;
        this.pdf = pdf;
        this.audit = audit;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public ControlReportDocument generate(UUID controlId, Actor actor) {
        QualityControl control = controls.findById(controlId).filter(found -> found.companyId().equals(actor.companyId()))
                .orElseThrow(() -> new NotFoundException(ErrorCodes.CONTROL_NOT_FOUND, "El control no existe."));
        if (control.status() != ControlStatus.FINISHED) {
            throw new ConflictException(ErrorCodes.CONTROL_NOT_FINISHED,
                    "El reporte solo se genera para un control finalizado; este está " + control.status() + ".");
        }
        String reference = projections.findById(control.batchId()).map(BatchProjection::traceabilityId).orElse(control.batchId().toString());
        reference = reference == null ? control.batchId().toString() : reference;
        ControlReport report = new ControlReport(control.id(), reference, control.controlType(), control.startDate(), control.endDate(), control.overallResult(),
                control.tests().stream().map(t -> new ControlReport.Test(t.criterionName(), t.expectedValue(), t.obtainedValue(), t.unit(), t.testResult(), t.remarks()))
                        .toList(), clock.instant());

        byte[] document = pdf.render(report);

        transaction.executeWithoutResult(tx -> audit.record(actor.userId(), control.companyId(), REPORT_GENERATED, "QualityControl", control.id().toString(),
                Map.of("batchId", control.batchId().toString())));
        return new ControlReportDocument(document, "reporte-control-" + report.batchReference() + "-" + control.id().toString().substring(0, 8) + ".pdf");
    }
}
