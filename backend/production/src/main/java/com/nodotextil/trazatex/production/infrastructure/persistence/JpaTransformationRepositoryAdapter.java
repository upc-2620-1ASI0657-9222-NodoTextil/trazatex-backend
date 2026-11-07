package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.production.application.port.out.TransformationRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.TransformationSearchCriteria;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.Transformation;
import com.nodotextil.trazatex.production.domain.model.TransformationInput;
import com.nodotextil.trazatex.production.domain.model.TransformationSnapshot;
import com.nodotextil.trazatex.production.domain.service.TransformationFactory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaTransformationRepositoryAdapter implements TransformationRepositoryPort {

    private final TransformationJpaRepository repository;
    private final TransformationFactory factory;

    JpaTransformationRepositoryAdapter(TransformationJpaRepository repository, TransformationFactory factory) {
        this.repository = repository;
        this.factory = factory;
    }

    @Override
    public void save(Transformation transformation) {
        TransformationEntity entity = repository.findById(transformation.id()).orElseGet(() -> create(transformation));
        entity.status = transformation.status();
        entity.totalOutputKg = kg(transformation.totalOutput());
        entity.wasteKg = kg(transformation.waste());
        entity.wasteReason = transformation.wasteReason();
        entity.shrinkageKg = kg(transformation.shrinkage());
        entity.endTime = transformation.endTime();
        if (!entity.outputBatchIds.equals(transformation.outputBatchIds())) {
            entity.outputBatchIds = new ArrayList<>(transformation.outputBatchIds());
        }
        repository.saveAndFlush(entity);
    }

    private static TransformationEntity create(Transformation transformation) {
        TransformationEntity entity = new TransformationEntity();
        entity.id = transformation.id();
        entity.companyId = transformation.companyId();
        entity.type = transformation.type();
        entity.machineId = transformation.machineId();
        entity.operatorId = transformation.operatorId();
        entity.totalInputKg = transformation.totalInput().kg();
        entity.startTime = transformation.startTime();
        entity.inputs = new ArrayList<>();
        for (TransformationInput input : transformation.inputs()) {
            entity.inputs.add(new InputRow(input.batchId(), input.quantity().kg()));
        }
        return entity;
    }

    @Override
    public Optional<Transformation> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Transformation> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public Page<Transformation> search(TransformationSearchCriteria criteria) {
        Specification<TransformationEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("companyId"), criteria.companyId()));
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.type() != null) {
                predicates.add(builder.equal(root.get("type"), criteria.type()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
                Sort.by(Sort.Direction.DESC, "startTime").and(Sort.by("id")));
        org.springframework.data.domain.Page<TransformationEntity> result = repository.findAll(specification, pageRequest);
        return new Page<>(result.getContent().stream().map(this::toDomain).toList(), criteria.page(), criteria.size(),
                result.getTotalElements());
    }

    private Transformation toDomain(TransformationEntity entity) {
        List<TransformationInput> inputs = entity.inputs.stream()
                .map(row -> new TransformationInput(row.batchId, Quantity.positive(row.quantityKg)))
                .toList();
        return factory.restore(new TransformationSnapshot(entity.id, entity.companyId, entity.type, entity.machineId,
                entity.operatorId, entity.status, inputs, List.copyOf(entity.outputBatchIds),
                Quantity.positive(entity.totalInputKg), quantity(entity.totalOutputKg), quantity(entity.wasteKg),
                entity.wasteReason, quantity(entity.shrinkageKg), entity.startTime, entity.endTime));
    }

    private static java.math.BigDecimal kg(Quantity quantity) {
        return quantity == null ? null : quantity.kg();
    }

    private static Quantity quantity(java.math.BigDecimal kg) {
        return kg == null ? null : Quantity.nonNegative(kg);
    }
}
