package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyPrivacyCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class GraphBuilder {

    static final Instant T0 = Instant.parse("2026-10-21T10:00:00Z");
    static final List<CompositionComponent> COTTON = List.of(new CompositionComponent("Algodón", new BigDecimal("100")));

    private final RegisterBatchUseCase register;
    private final ApplyBatchSplitUseCase split;
    private final ApplyTransformationCompletedUseCase transformation;
    private final ApplyBatchPhaseChangedUseCase phase;
    private final ApplyTransferAcceptedUseCase transfer;
    private final ApplyCompanyRegisteredUseCase company;
    private final ApplyCompanyPrivacyUseCase privacy;
    private final AtomicInteger clock = new AtomicInteger();
    private final Map<String, String> names = new java.util.HashMap<>();

    GraphBuilder(RegisterBatchUseCase register, ApplyBatchSplitUseCase split, ApplyTransformationCompletedUseCase transformation,
                 ApplyBatchPhaseChangedUseCase phase, ApplyTransferAcceptedUseCase transfer, ApplyCompanyRegisteredUseCase company,
                 ApplyCompanyPrivacyUseCase privacy) {
        this.register = register;
        this.split = split;
        this.transformation = transformation;
        this.phase = phase;
        this.transfer = transfer;
        this.company = company;
        this.privacy = privacy;
    }

    static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("gb-" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID event() {
        return UUID.randomUUID();
    }

    private Instant next() {
        return T0.plusSeconds(clock.incrementAndGet());
    }

    GraphBuilder company(UUID companyId, String businessName) {
        company.apply(new ApplyCompanyRegisteredCommand(event(), companyId, businessName));
        return this;
    }

    GraphBuilder privacy(UUID companyId, Map<String, String> fields) {
        privacy.apply(new ApplyCompanyPrivacyCommand(event(), companyId, fields));
        return this;
    }

    GraphBuilder named(String key, String batchName) {
        names.put(key, batchName);
        return this;
    }

    GraphBuilder batch(String name, UUID companyId) {
        register.apply(new RegisterBatchCommand(event(), id(name), "TT-" + name, "QR-" + name, companyId, "Algodón", COTTON, new BigDecimal("100"), "Piura",
                "Textiles del Norte", "Fibra limpia", next(), names.get(name)));
        return this;
    }

    GraphBuilder batches(UUID companyId, String... names) {
        for (String name : names) {
            batch(name, companyId);
        }
        return this;
    }

    GraphBuilder split(String parent, UUID companyId, String... children) {
        List<ApplyBatchSplitCommand.Child> list = new ArrayList<>();
        for (String child : children) {
            list.add(new ApplyBatchSplitCommand.Child(id(child), "TT-" + child, "QR-" + child, "Algodón", new BigDecimal("10"), COTTON, "Piura", "Textiles del Norte",
                    "Fibra limpia", QualityStatus.NOT_REVIEWED, next(), names.get(child)));
        }
        split.apply(new ApplyBatchSplitCommand(event(), id(parent), companyId, list));
        return this;
    }

    GraphBuilder transform(String name, UUID companyId, List<String> inputs, String... outputs) {
        List<ApplyTransformationCompletedCommand.Output> list = new ArrayList<>();
        for (String output : outputs) {
            list.add(new ApplyTransformationCompletedCommand.Output(id(output), "TT-" + output, "QR-" + output, "Tela teñida", new BigDecimal("8"), COTTON, false, names.get(output)));
        }
        transformation.apply(new ApplyTransformationCompletedCommand(event(), id(name), TransformationType.DYEING, companyId, "Cardadora", "Cardado",
                inputs.stream().map(GraphBuilder::id).toList(), list, new BigDecimal("1"), "Orillo", new BigDecimal("1"), T0, next()));
        return this;
    }

    GraphBuilder phase(String batch, UUID companyId, BatchPhase newPhase, boolean hold, long version) {
        phase.apply(new ApplyBatchPhaseChangedCommand(event(), id(batch), companyId, newPhase, hold, version));
        return this;
    }

    GraphBuilder transferred(String transfer, String batch, UUID toCompany) {
        this.transfer.apply(new ApplyTransferAcceptedCommand(event(), id(transfer), List.of(id(batch)), toCompany, next()));
        return this;
    }

    GraphBuilder chain(String prefix, UUID companyId, int length) {
        batch(prefix + 0, companyId);
        for (int i = 1; i < length; i++) {
            split(prefix + (i - 1), companyId, prefix + i);
        }
        return this;
    }
}
