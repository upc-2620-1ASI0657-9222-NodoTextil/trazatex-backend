package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.BatchResult;
import com.nodotextil.trazatex.production.application.dto.CommercialDataInput;
import com.nodotextil.trazatex.production.application.dto.CompleteTransformationCommand;
import com.nodotextil.trazatex.production.application.dto.CompleteTransformationResult;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.StartTransformationCommand;
import com.nodotextil.trazatex.production.application.dto.TransformationResult;
import com.nodotextil.trazatex.production.application.port.in.ManageTransformationsUseCase;
import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.production.application.port.out.MachineRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.TransformationRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.TransformationSearchCriteria;
import com.nodotextil.trazatex.production.domain.event.TransformationCompleted;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchFactory;
import com.nodotextil.trazatex.production.domain.model.CommercialData;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.Transformation;
import com.nodotextil.trazatex.production.domain.model.TransformationInput;
import com.nodotextil.trazatex.production.domain.model.TransformationStatus;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import com.nodotextil.trazatex.production.domain.service.TransformationFactory;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
class TransformationService implements ManageTransformationsUseCase {

    private final TransformationRepositoryPort transformations;
    private final BatchChanges batchChanges;
    private final MachineRepositoryPort machines;
    private final TransformationFactory transformationFactory;
    private final BatchFactory batchFactory;
    private final BatchLocks locks;
    private final CompanyGuard companyGuard;
    private final AuditLogPort audit;
    private final Clock clock;

    TransformationService(TransformationRepositoryPort transformations, BatchChanges batchChanges, MachineRepositoryPort machines,
                          TransformationFactory transformationFactory, BatchFactory batchFactory, BatchLocks locks,
                          CompanyGuard companyGuard, AuditLogPort audit, Clock clock) {
        this.transformations = transformations;
        this.batchChanges = batchChanges;
        this.machines = machines;
        this.transformationFactory = transformationFactory;
        this.batchFactory = batchFactory;
        this.locks = locks;
        this.companyGuard = companyGuard;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public TransformationResult start(StartTransformationCommand command, Actor actor) {
        companyGuard.requireActive(actor.companyId());
        if (command.type() == null || command.machineId() == null) {
            List<String> missing = new ArrayList<>();
            if (command.type() == null) {
                missing.add("type");
            }
            if (command.machineId() == null) {
                missing.add("machineId");
            }
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos obligatorios: " + String.join(", ", missing) + ".", missing);
        }
        List<UUID> ids = BatchLocks.requireDistinct(command.batchIds());
        Machine machine = machines.findById(command.machineId()).filter(m -> m.companyId().equals(actor.companyId()))
                .orElseThrow(() -> new NotFoundException(ErrorCodes.MACHINE_NOT_FOUND, "La maquinaria no existe."));
        List<Batch> inputs = locks.lockOwned(ids, actor.companyId());
        Transformation transformation = transformationFactory.start(UUID.randomUUID(), command.type(), actor.companyId(), machine,
                actor.userId(), inputs, clock.instant());
        batchChanges.persistAndPublish(inputs, PhaseChangeCause.of(PhaseChangeCause.TRANSFORMATION_STARTED, transformation.id()), null);
        transformations.save(transformation);
        audit.record(actor.userId(), actor.companyId(), "TRANSFORMATION_STARTED", "Transformation", transformation.id().toString(),
                BatchService.changes("type", transformation.type().name(), "machineId", machine.id().toString(),
                        "inputs", inputs.stream().map(b -> b.traceabilityId().value()).toList(),
                        "totalInputKg", transformation.totalInput().kg().toPlainString()));
        return TransformationResult.from(transformation);
    }

    /** Finalizar cierra trabajo ya iniciado: se permite aunque la empresa esté inactiva (RF-003, sin lotes atascados). */
    @Override
    public CompleteTransformationResult complete(UUID transformationId, CompleteTransformationCommand command, Actor actor) {
        Transformation transformation = transformations.findByIdForUpdate(transformationId)
                .filter(t -> t.companyId().equals(actor.companyId())).orElseThrow(TransformationService::notFound);
        List<Batch> inputs = locks.lockOwned(transformation.inputs().stream().map(TransformationInput::batchId).toList(), actor.companyId());
        List<BatchFactory.OutputSpec> outputs = new ArrayList<>();
        if (command.outputs() != null) {
            for (CompleteTransformationCommand.Output output : command.outputs()) {
                outputs.add(toSpec(output));
            }
        }
        Quantity waste = command.wasteKg() == null ? null : Quantity.nonNegative(command.wasteKg());
        List<Batch> produced = transformation.finalize(inputs, outputs, waste, command.wasteReason(), batchFactory,
                actor.userId(), clock.instant());
        Machine machine = machines.findById(transformation.machineId())
                .orElseThrow(() -> new IllegalStateException("La maquinaria " + transformation.machineId() + " de la transformación no existe"));
        batchChanges.persistAndPublish(inputs, PhaseChangeCause.of(PhaseChangeCause.TRANSFORMATION_COMPLETED, transformation.id()),
                TransformationCompleted.of(transformation, produced, machine));
        batchChanges.persist(produced);
        transformations.save(transformation);
        audit.record(actor.userId(), actor.companyId(), "TRANSFORMATION_COMPLETED", "Transformation", transformation.id().toString(),
                BatchService.changes("totalInputKg", transformation.totalInput().kg().toPlainString(),
                        "totalOutputKg", transformation.totalOutput().kg().toPlainString(),
                        "wasteKg", transformation.waste().kg().toPlainString(),
                        "shrinkageKg", transformation.shrinkage().kg().toPlainString(),
                        "outputs", produced.stream().map(b -> b.traceabilityId().value()).toList()));
        return new CompleteTransformationResult(TransformationResult.from(transformation), produced.stream().map(BatchResult::from).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TransformationResult get(UUID transformationId, Actor actor) {
        return TransformationResult.from(transformations.findById(transformationId)
                .filter(t -> t.companyId().equals(actor.companyId())).orElseThrow(TransformationService::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TransformationResult> search(TransformationStatus status, TransformationType type, Integer page, Integer size,
                                                   Actor actor) {
        return PageResult.of(transformations.search(new TransformationSearchCriteria(actor.companyId(), status, type,
                Paging.page(page), Paging.size(size))).map(TransformationResult::from));
    }

    private static BatchFactory.OutputSpec toSpec(CompleteTransformationCommand.Output output) {
        if (output == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos obligatorios: outputs.", List.of("outputs"));
        }
        CommercialDataInput data = output.commercialData();
        CommercialData commercial = data == null ? null : new CommercialData(data.buyerOrDistributor(), data.salePrice(),
                data.currency(), data.commercialExitDate(), data.commercialReference());
        return new BatchFactory.OutputSpec(output.name(), output.materialType(), Quantity.positive(output.quantityKg()),
                Boolean.TRUE.equals(output.finalProduct()), commercial);
    }

    private static NotFoundException notFound() {
        return new NotFoundException(ErrorCodes.TRANSFORMATION_NOT_FOUND, "La transformación no existe.");
    }
}
