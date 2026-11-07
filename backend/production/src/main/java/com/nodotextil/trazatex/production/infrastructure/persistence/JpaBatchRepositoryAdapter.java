package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.BatchSearchCriteria;
import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchSnapshot;
import com.nodotextil.trazatex.production.domain.model.CommercialData;
import com.nodotextil.trazatex.production.domain.model.Composition;
import com.nodotextil.trazatex.production.domain.model.CompositionComponent;
import com.nodotextil.trazatex.production.domain.model.QrCode;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.TraceabilityId;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaBatchRepositoryAdapter implements BatchRepositoryPort {

    private final BatchJpaRepository repository;

    JpaBatchRepositoryAdapter(BatchJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public long save(Batch batch) {
        BatchEntity entity = repository.findById(batch.id()).orElseGet(() -> create(batch));
        entity.companyId = batch.companyId();
        entity.phase = batch.phase();
        entity.qualityStatus = batch.qualityStatus();
        entity.qualityHold = batch.qualityHold();
        entity.finalProduct = batch.isFinalProduct();
        CommercialData commercial = batch.commercialData();
        entity.buyerOrDistributor = commercial.buyerOrDistributor();
        entity.salePrice = commercial.salePrice();
        entity.currency = commercial.currency();
        entity.commercialExitDate = commercial.commercialExitDate();
        entity.commercialReference = commercial.commercialReference();
        return repository.saveAndFlush(entity).version;
    }

    /** Lo que no cambia nunca después de crear el lote (códigos, cantidad, composición, origen...). */
    private static BatchEntity create(Batch batch) {
        BatchEntity entity = new BatchEntity();
        entity.id = batch.id();
        entity.traceabilityId = batch.traceabilityId().value();
        entity.qrCode = batch.qrCode().value();
        entity.name = batch.name();
        entity.materialType = batch.materialType();
        entity.quantityKg = batch.quantity().kg();
        entity.originType = batch.originType();
        entity.origin = batch.origin();
        entity.supplierName = batch.supplierName();
        entity.receptionCharacteristics = batch.receptionCharacteristics();
        entity.createdBy = batch.createdBy();
        entity.createdAt = batch.createdAt();
        entity.composition = new ArrayList<>();
        for (CompositionComponent component : batch.composition().components()) {
            entity.composition.add(new CompositionRow(component.name(), component.percentage()));
        }
        return entity;
    }

    @Override
    public Optional<Batch> findById(UUID id) {
        return repository.findById(id).map(JpaBatchRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Batch> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(JpaBatchRepositoryAdapter::toDomain);
    }

    @Override
    public List<Batch> findAllForUpdate(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIdForUpdate(ids).stream().map(JpaBatchRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<Batch> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids).stream().map(JpaBatchRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<Batch> findByQrCode(QrCode qrCode) {
        return repository.findByQrCode(qrCode.value()).map(JpaBatchRepositoryAdapter::toDomain);
    }

    @Override
    public Page<Batch> search(BatchSearchCriteria criteria) {
        Specification<BatchEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("companyId"), criteria.companyId()));
            if (criteria.traceabilityIdPrefix() != null && !criteria.traceabilityIdPrefix().isEmpty()) {
                predicates.add(builder.like(root.get("traceabilityId"), escapeLike(criteria.traceabilityIdPrefix()) + "%", '\\'));
            }
            if (criteria.materialType() != null && !criteria.materialType().isBlank()) {
                predicates.add(builder.equal(builder.lower(root.get("materialType")),
                        criteria.materialType().trim().toLowerCase(Locale.ROOT)));
            }
            if (criteria.phase() != null) {
                predicates.add(builder.equal(root.get("phase"), criteria.phase()));
            }
            if (criteria.qualityStatus() != null) {
                predicates.add(builder.equal(root.get("qualityStatus"), criteria.qualityStatus()));
            }
            if (criteria.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), criteria.createdFrom()));
            }
            if (criteria.createdTo() != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), criteria.createdTo()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        org.springframework.data.domain.Page<BatchEntity> result = repository.findAll(specification, pageRequest);
        return new Page<>(result.getContent().stream().map(JpaBatchRepositoryAdapter::toDomain).toList(),
                criteria.page(), criteria.size(), result.getTotalElements());
    }

    @Override
    public boolean isCodeTaken(String code) {
        return repository.existsByTraceabilityId(code) || repository.existsByQrCode(code);
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static Batch toDomain(BatchEntity entity) {
        List<CompositionComponent> components = entity.composition.stream()
                .map(row -> new CompositionComponent(row.componentName, row.percentage))
                .toList();
        CommercialData commercial = new CommercialData(entity.buyerOrDistributor, entity.salePrice, entity.currency,
                entity.commercialExitDate, entity.commercialReference);
        return Batch.restore(new BatchSnapshot(entity.id, new TraceabilityId(entity.traceabilityId), new QrCode(entity.qrCode),
                entity.companyId, entity.name, entity.materialType, Quantity.positive(entity.quantityKg),
                Composition.of(components), entity.phase, entity.qualityStatus, entity.qualityHold, entity.finalProduct,
                entity.originType, entity.origin, entity.supplierName, entity.receptionCharacteristics, entity.createdBy,
                entity.createdAt, commercial, entity.version == null ? 0 : entity.version));
    }
}
