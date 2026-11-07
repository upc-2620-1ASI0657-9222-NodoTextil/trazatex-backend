package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepositoryPort {

    /** Guarda y envía el cambio a la base de inmediato: el índice único parcial de correos pendientes debe ver la baja de la vencida antes del alta de la nueva. */
    void save(Invitation invitation);

    Optional<Invitation> findById(UUID id);

    /** Bloquea la fila: dos aceptaciones simultáneas del mismo enlace se serializan. */
    Optional<Invitation> findByTokenForUpdate(String token);

    Optional<Invitation> findByToken(String token);

    /** La invitación guardada como PENDING para ese correo (puede estar vencida si el job aún no corrió). */
    Optional<Invitation> findStoredPendingByEmail(Email email);

    /** Invitaciones de la empresa guardadas como PENDING con {@code expires_at > now}: las que ocupan cupo. */
    long countOccupyingSlots(UUID companyId, Instant now);

    /** Existe una invitación LICENSE_OWNER PENDING con {@code expires_at > now}. */
    boolean existsLiveOwnerInvitation(Instant now);

    /** Guardadas como PENDING con {@code expires_at <= now}, para el job de expiración. */
    List<Invitation> findDue(Instant now, int limit);

    List<Invitation> findByCompanyId(UUID companyId);

    /** Las que emitió ese usuario, de la más reciente a la más antigua (a igual fecha, por id). */
    com.nodotextil.trazatex.sharedkernel.paging.PageResult<Invitation> findPageByIssuer(UUID issuedBy, com.nodotextil.trazatex.sharedkernel.paging.PageRequest request);
}
