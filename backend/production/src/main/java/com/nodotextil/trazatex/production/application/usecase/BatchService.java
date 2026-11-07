package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.BatchResult;
import com.nodotextil.trazatex.production.application.dto.BatchSummaryResult;
import com.nodotextil.trazatex.production.application.dto.CommercialDataInput;
import com.nodotextil.trazatex.production.application.dto.CompositionComponentInput;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.QrLookupResult;
import com.nodotextil.trazatex.production.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.production.application.dto.SearchBatchesQuery;
import com.nodotextil.trazatex.production.application.dto.SharedBatchResult;
import com.nodotextil.trazatex.production.application.dto.SplitBatchCommand;
import com.nodotextil.trazatex.production.application.dto.SplitResult;
import com.nodotextil.trazatex.production.application.port.in.ManageBatchesUseCase;
import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.BatchSearchCriteria;
import com.nodotextil.trazatex.production.application.port.out.ProductionEventPublisherPort;
import com.nodotextil.trazatex.production.domain.event.BatchRegistered;
import com.nodotextil.trazatex.production.domain.event.BatchSplit;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchFactory;
import com.nodotextil.trazatex.production.domain.model.CommercialData;
import com.nodotextil.trazatex.production.domain.model.Composition;
import com.nodotextil.trazatex.production.domain.model.CompositionComponent;
import com.nodotextil.trazatex.production.domain.model.QrCode;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.TraceabilityId;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
class BatchService implements ManageBatchesUseCase {

    /** Las fechas de los filtros son días calendario en Lima: un lote registrado a las 22:00 del día 3 no cae en el 4. */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final BatchRepositoryPort batches;
    private final BatchChanges batchChanges;
    private final ProductionEventPublisherPort events;
    private final BatchFactory factory;
    private final CompanyGuard companyGuard;
    private final AuditLogPort audit;
    private final Clock clock;

    BatchService(BatchRepositoryPort batches, BatchChanges batchChanges, ProductionEventPublisherPort events, BatchFactory factory,
                 CompanyGuard companyGuard, AuditLogPort audit, Clock clock) {
        this.batches = batches;
        this.batchChanges = batchChanges;
        this.events = events;
        this.factory = factory;
        this.companyGuard = companyGuard;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public BatchResult register(RegisterBatchCommand command, Actor actor) {
        companyGuard.requireActive(actor.companyId());
        Composition composition = command.composition() == null ? null : toComposition(command.composition());
        Quantity quantity = command.quantityKg() == null ? null : Quantity.positive(command.quantityKg());
        Batch batch = factory.register(new BatchFactory.Registration(command.name(), command.materialType(), composition, quantity,
                command.origin(), command.supplierName(), command.receptionCharacteristics()),
                actor.companyId(), actor.userId(), clock.instant());
        batchChanges.persist(batch);
        events.publish(BatchRegistered.of(batch));
        audit.record(actor.userId(), actor.companyId(), "BATCH_REGISTERED", "Batch", batch.id().toString(),
                changes("traceabilityId", batch.traceabilityId().value(), "materialType", batch.materialType(),
                        "quantityKg", batch.quantity().kg().toPlainString()));
        return BatchResult.from(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BatchSummaryResult> search(SearchBatchesQuery query, Actor actor) {
        int page = Paging.page(query.page());
        int size = Paging.size(query.size());
        if (query.createdFrom() != null && query.createdTo() != null && query.createdFrom().isAfter(query.createdTo())) {
            throw new ValidationException(ErrorCodes.INVALID_DATE_RANGE,
                    "La fecha inicial no puede ser posterior a la final.", List.of("createdFrom", "createdTo"));
        }
        Instant from = query.createdFrom() == null ? null : startOfDay(query.createdFrom());
        Instant to = query.createdTo() == null ? null : startOfDay(query.createdTo().plusDays(1));
        String prefix = query.traceabilityId() == null || query.traceabilityId().isBlank()
                ? null : TraceabilityId.normalizePrefix(query.traceabilityId());
        return PageResult.of(batches.search(new BatchSearchCriteria(actor.companyId(), prefix, query.materialType(), query.phase(),
                query.qualityStatus(), from, to, page, size)).map(BatchSummaryResult::from));
    }

    @Override
    @Transactional(readOnly = true)
    public BatchResult get(UUID batchId, Actor actor) {
        return BatchResult.from(batches.findById(batchId).filter(b -> b.companyId().equals(actor.companyId()))
                .orElseThrow(BatchService::batchNotFound));
    }

    @Override
    @Transactional(readOnly = true)
    public QrLookupResult findByQr(String qrCode, Actor actor) {
        Batch batch = parseQr(qrCode).flatMap(batches::findByQrCode).orElseThrow(
                () -> new NotFoundException(ErrorCodes.QR_NOT_FOUND, "Código no encontrado."));
        return batch.companyId().equals(actor.companyId())
                ? new QrLookupResult(QrLookupResult.OWN, BatchResult.from(batch))
                : new QrLookupResult(QrLookupResult.SHARED, SharedBatchResult.from(batch));
    }

    @Override
    public SplitResult split(UUID batchId, SplitBatchCommand command, Actor actor) {
        companyGuard.requireActive(actor.companyId());
        Batch parent = lockOwned(batchId, actor);
        List<BatchFactory.ChildSpec> specs = new ArrayList<>();
        if (command.children() != null) {
            for (SplitBatchCommand.Child child : command.children()) {
                specs.add(new BatchFactory.ChildSpec(child == null ? null : child.name(),
                        child == null || child.quantityKg() == null ? null : Quantity.positive(child.quantityKg())));
            }
        }
        BatchFactory.SplitResult result = factory.split(parent, specs, actor.userId(), clock.instant());
        batchChanges.persist(result.children());
        batchChanges.persistAndPublish(List.of(parent), PhaseChangeCause.of(PhaseChangeCause.SPLIT, parent.id()),
                BatchSplit.of(parent, result.children()));
        List<String> children = result.children().stream().map(c -> c.traceabilityId().value() + "=" + c.quantity().kg().toPlainString()).toList();
        audit.record(actor.userId(), actor.companyId(), "BATCH_SPLIT", "Batch", parent.id().toString(),
                changes("parentTraceabilityId", parent.traceabilityId().value(), "children", children));
        return SplitResult.from(result);
    }

    @Override
    public BatchResult updateCommercialData(UUID batchId, CommercialDataInput data, Actor actor) {
        Batch batch = lockOwned(batchId, actor);
        CommercialData commercial = data == null ? CommercialData.EMPTY
                : new CommercialData(data.buyerOrDistributor(), data.salePrice(), data.currency(), data.commercialExitDate(),
                data.commercialReference());
        batch.updateCommercialData(commercial);
        batchChanges.persist(batch);
        audit.record(actor.userId(), actor.companyId(), "BATCH_COMMERCIAL_DATA_UPDATED", "Batch", batch.id().toString(),
                changes("traceabilityId", batch.traceabilityId().value(), "buyerOrDistributor", String.valueOf(commercial.buyerOrDistributor()),
                        "salePrice", String.valueOf(commercial.salePrice()), "currency", String.valueOf(commercial.currency())));
        return BatchResult.from(batch);
    }

    // ---- ayudas ----

    /** El lote con su fila bloqueada; uno de otra empresa es "no existe" (no se revela que existe). */
    private Batch lockOwned(UUID batchId, Actor actor) {
        return batches.findByIdForUpdate(batchId).filter(b -> b.companyId().equals(actor.companyId()))
                .orElseThrow(BatchService::batchNotFound);
    }

    private static NotFoundException batchNotFound() {
        return new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "El lote no existe.");
    }

    private static java.util.Optional<QrCode> parseQr(String text) {
        try {
            return java.util.Optional.of(QrCode.parse(text));
        } catch (TrazatexException e) {
            return java.util.Optional.empty();    // un código mal formado es "no encontrado", igual que uno desconocido
        }
    }

    private static Composition toComposition(List<CompositionComponentInput> inputs) {
        List<CompositionComponent> components = new ArrayList<>();
        for (CompositionComponentInput input : inputs) {
            if (input == null) {
                throw new ValidationException(ErrorCodes.INVALID_COMPOSITION, "La composición no es válida: hay un componente vacío.", null);
            }
            components.add(new CompositionComponent(input.name(), input.percentage()));
        }
        return Composition.of(components);
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    static Map<String, Object> changes(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
