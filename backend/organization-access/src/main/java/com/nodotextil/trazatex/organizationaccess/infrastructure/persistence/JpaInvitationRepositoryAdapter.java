package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.InvitationToken;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaInvitationRepositoryAdapter implements InvitationRepositoryPort {

    private final InvitationJpaRepository repository;

    JpaInvitationRepositoryAdapter(InvitationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Invitation invitation) {
        InvitationEntity entity = new InvitationEntity();
        entity.id = invitation.id();
        entity.token = invitation.token().value();
        entity.email = invitation.email().value();
        entity.displayName = invitation.displayName();
        entity.firstName = invitation.firstName();
        entity.lastName = invitation.lastName();
        entity.position = invitation.position();
        entity.role = invitation.role();
        entity.companyId = invitation.companyId();
        entity.issuedBy = invitation.issuedBy();
        entity.status = invitation.status();
        entity.createdAt = invitation.createdAt();
        entity.expiresAt = invitation.expiresAt();
        repository.saveAndFlush(entity);
    }

    @Override
    public Optional<Invitation> findById(UUID id) {
        return repository.findById(id).map(JpaInvitationRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Invitation> findByTokenForUpdate(String token) {
        return repository.findByTokenForUpdate(token).map(JpaInvitationRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Invitation> findByToken(String token) {
        return repository.findByToken(token).map(JpaInvitationRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Invitation> findStoredPendingByEmail(Email email) {
        return repository.findByEmailAndStatus(email.value(), InvitationStatus.PENDING).map(JpaInvitationRepositoryAdapter::toDomain);
    }

    @Override
    public long countOccupyingSlots(UUID companyId, Instant now) {
        return repository.countByCompanyIdAndStatusAndExpiresAtAfter(companyId, InvitationStatus.PENDING, now);
    }

    @Override
    public boolean existsLiveOwnerInvitation(Instant now) {
        return repository.existsByRoleAndStatusAndExpiresAtAfter(Role.LICENSE_OWNER, InvitationStatus.PENDING, now);
    }

    @Override
    public List<Invitation> findDue(Instant now, int limit) {
        return repository.findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(InvitationStatus.PENDING, now, PageRequest.of(0, limit))
                .stream().map(JpaInvitationRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<Invitation> findByCompanyId(UUID companyId) {
        return repository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(JpaInvitationRepositoryAdapter::toDomain).toList();
    }

    @Override
    public com.nodotextil.trazatex.sharedkernel.paging.PageResult<Invitation> findPageByIssuer(UUID issuedBy, com.nodotextil.trazatex.sharedkernel.paging.PageRequest request) {
        org.springframework.data.domain.Page<InvitationEntity> found = repository.findByIssuedByOrderByCreatedAtDescIdAsc(issuedBy, PageRequest.of(request.page(), request.size()));
        return com.nodotextil.trazatex.sharedkernel.paging.PageResult.of(found.getContent().stream().map(JpaInvitationRepositoryAdapter::toDomain).toList(), request, found.getTotalElements());
    }

    private static Invitation toDomain(InvitationEntity entity) {
        return Invitation.restore(entity.id, new InvitationToken(entity.token), new Email(entity.email), entity.displayName, entity.firstName,
                entity.lastName, entity.position, entity.role, entity.companyId, entity.issuedBy, entity.status,
                entity.createdAt, entity.expiresAt);
    }
}
