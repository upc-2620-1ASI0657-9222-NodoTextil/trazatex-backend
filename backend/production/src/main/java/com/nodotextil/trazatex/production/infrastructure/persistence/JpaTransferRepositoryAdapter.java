package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.production.application.port.out.TransferRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.TransferSearchCriteria;
import com.nodotextil.trazatex.production.domain.model.Transfer;
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
class JpaTransferRepositoryAdapter implements TransferRepositoryPort {

    private final TransferJpaRepository repository;

    JpaTransferRepositoryAdapter(TransferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Transfer transfer) {
        TransferEntity entity = repository.findById(transfer.id()).orElseGet(() -> create(transfer));
        entity.status = transfer.status();
        entity.resolvedAt = transfer.resolvedAt();
        entity.rejectionReason = transfer.rejectionReason();
        entity.resolvedBy = transfer.resolvedBy();
        repository.saveAndFlush(entity);
    }

    private static TransferEntity create(Transfer transfer) {
        TransferEntity entity = new TransferEntity();
        entity.id = transfer.id();
        entity.originCompanyId = transfer.originCompanyId();
        entity.targetCompanyId = transfer.targetCompanyId();
        entity.departureTime = transfer.departureTime();
        entity.initiatedBy = transfer.initiatedBy();
        entity.batchIds = new ArrayList<>(transfer.batchIds());
        return entity;
    }

    @Override
    public Optional<Transfer> findById(UUID id) {
        return repository.findById(id).map(JpaTransferRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Transfer> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(JpaTransferRepositoryAdapter::toDomain);
    }

    @Override
    public Page<Transfer> search(TransferSearchCriteria criteria) {
        Specification<TransferEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Predicate outgoing = builder.equal(root.get("originCompanyId"), criteria.companyId());
            Predicate incoming = builder.equal(root.get("targetCompanyId"), criteria.companyId());
            predicates.add(switch (criteria.direction()) {
                case OUTGOING -> outgoing;
                case INCOMING -> incoming;
                case ALL -> builder.or(outgoing, incoming);
            });
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
                Sort.by(Sort.Direction.DESC, "departureTime").and(Sort.by("id")));
        org.springframework.data.domain.Page<TransferEntity> result = repository.findAll(specification, pageRequest);
        return new Page<>(result.getContent().stream().map(JpaTransferRepositoryAdapter::toDomain).toList(),
                criteria.page(), criteria.size(), result.getTotalElements());
    }

    private static Transfer toDomain(TransferEntity entity) {
        return Transfer.restore(entity.id, entity.originCompanyId, entity.targetCompanyId, entity.status,
                List.copyOf(entity.batchIds), entity.departureTime, entity.resolvedAt, entity.rejectionReason,
                entity.initiatedBy, entity.resolvedBy);
    }
}
