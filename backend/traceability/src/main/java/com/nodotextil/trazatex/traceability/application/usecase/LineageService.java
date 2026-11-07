package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyPrivacyCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.application.port.out.LineageGraphPort;
import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Construye la genealogía a partir de los eventos (US29). Traceability no crea lotes, IDs ni QR ni cambia fases: refleja lo que
 * publican los demás servicios. Todas las reglas de orden y de datos inmutables viven en el contrato de {@link LineageGraphPort}
 * (se aplican de forma atómica en la base). {@code @Transactional} se une a la transacción idempotente del consumidor; llamado
 * solo, abre la suya, para que un evento nunca quede a medias.
 */
@Service
@Transactional
class LineageService implements RegisterBatchUseCase, ApplyBatchSplitUseCase, ApplyTransformationCompletedUseCase,
        ApplyBatchPhaseChangedUseCase, ApplyTransferAcceptedUseCase, ApplyQualityStatusUseCase, ApplyCompanyPrivacyUseCase, ApplyCompanyRegisteredUseCase {

    private static final Logger log = LoggerFactory.getLogger(LineageService.class);

    private final LineageGraphPort graph;

    LineageService(LineageGraphPort graph) {
        this.graph = graph;
    }

    @Override
    public void apply(RegisterBatchCommand command) {
        graph.registerBatch(command);
    }

    @Override
    public void apply(ApplyBatchSplitCommand command) {
        graph.applyBatchSplit(command);
    }

    @Override
    public void apply(ApplyTransformationCompletedCommand command) {
        graph.applyTransformationCompleted(command);
    }

    @Override
    public void apply(ApplyBatchPhaseChangedCommand command) {
        if (!graph.applyBatchPhaseChanged(command)) {
            log.debug("BatchPhaseChanged {} del lote {} con batchVersion {} no es más nuevo que el guardado: se ignora",
                    command.eventId(), command.batchId(), command.batchVersion());
        }
    }

    @Override
    public void apply(ApplyTransferAcceptedCommand command) {
        graph.applyTransferAccepted(command);
    }

    @Override
    public void apply(ApplyQualityStatusCommand command) {
        graph.applyQualityStatus(command);
    }

    @Override
    public void apply(ApplyCompanyRegisteredCommand command) {
        graph.registerCompany(command);
    }

    @Override
    public void apply(ApplyCompanyPrivacyCommand command) {
        CompanyPrivacy.Parsed parsed = CompanyPrivacy.fromRaw(command.fields());
        parsed.warnings().forEach(warning -> log.warn("CompanyPrivacyUpdated {} de la empresa {}: {}", command.eventId(), command.companyId(), warning));
        graph.saveCompanyPrivacy(command.companyId(), parsed.privacy());
    }
}
