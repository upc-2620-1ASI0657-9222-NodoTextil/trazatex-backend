package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepositoryPort {

    void save(Invitation invitation);

    Optional<Invitation> findById(UUID id);

    Optional<Invitation> findByTokenForUpdate(String token);

    Optional<Invitation> findByToken(String token);

    Optional<Invitation> findStoredPendingByEmail(Email email);

    long countOccupyingSlots(UUID companyId, Instant now);

    boolean existsLiveOwnerInvitation(Instant now);

    List<Invitation> findDue(Instant now, int limit);

    List<Invitation> findByCompanyId(UUID companyId);

    com.nodotextil.trazatex.sharedkernel.paging.PageResult<Invitation> findPageByIssuer(UUID issuedBy, com.nodotextil.trazatex.sharedkernel.paging.PageRequest request);
}
