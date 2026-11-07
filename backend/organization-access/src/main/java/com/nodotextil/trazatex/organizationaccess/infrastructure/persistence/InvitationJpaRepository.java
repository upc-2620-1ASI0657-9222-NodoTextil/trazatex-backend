package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.model.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InvitationJpaRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvitationEntity i where i.token = :token")
    Optional<InvitationEntity> findByTokenForUpdate(@Param("token") String token);

    Optional<InvitationEntity> findByEmailAndStatus(String email, InvitationStatus status);

    long countByCompanyIdAndStatusAndExpiresAtAfter(UUID companyId, InvitationStatus status, Instant now);

    boolean existsByRoleAndStatusAndExpiresAtAfter(Role role, InvitationStatus status, Instant now);

    List<InvitationEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(InvitationStatus status, Instant now, Pageable page);

    List<InvitationEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    org.springframework.data.domain.Page<InvitationEntity> findByIssuedByOrderByCreatedAtDescIdAsc(UUID issuedBy, org.springframework.data.domain.Pageable pageable);
}
