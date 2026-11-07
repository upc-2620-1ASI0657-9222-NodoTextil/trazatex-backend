package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Invitación para crear una cuenta (US04, US06, US41). {@code PENDING} pasa a {@code ACCEPTED}, {@code EXPIRED} o
 * {@code CANCELLED} y ninguna vuelve a {@code PENDING}.
 *
 * <p><b>Vigencia.</b> Vence a los {@value #VALIDITY_DAYS} días. El estado guardado puede ir por detrás (el job de expiración
 * corre cada cierto tiempo), así que toda decisión usa {@link #effectiveStatus(Instant)}: una invitación vencida nunca se
 * acepta, no se cancela y no ocupa cupo aunque siga guardada como {@code PENDING}.
 *
 * <p>Los datos personales son opcionales salvo para el operario (US06 pide nombres, apellidos, correo y cargo); en las
 * invitaciones de administrador y de Dueño quien acepta completa nombres y apellidos. {@code displayName} es el nombre
 * para mostrar (el del correo de invitación): el que trae quien invita ({@code fullName} de la solicitud de administrador,
 * {@code TRAZATEX_OWNER_NAME}) tal cual, sin dividirlo, o "nombres apellidos" si la invitación los trae. {@code issuedBy} es nulo solo en la
 * invitación del Dueño que crea el bootstrap, que nadie puede cancelar.
 */
public final class Invitation {

    public static final int VALIDITY_DAYS = 7;
    public static final Duration VALIDITY = Duration.ofDays(VALIDITY_DAYS);

    private final UUID id;
    private final InvitationToken token;
    private final Email email;
    private final String displayName;
    private final String firstName;
    private final String lastName;
    private final String position;
    private final Role role;
    private final UUID companyId;
    private final UUID issuedBy;
    private InvitationStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;

    private Invitation(UUID id, InvitationToken token, Email email, String displayName, String firstName, String lastName,
                       String position, Role role, UUID companyId, UUID issuedBy, InvitationStatus status,
                       Instant createdAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.token = token;
        this.email = email;
        this.firstName = blankToNull(firstName);
        this.lastName = blankToNull(lastName);
        String given = blankToNull(displayName);
        this.displayName = given != null ? given
                : this.firstName != null && this.lastName != null ? this.firstName + " " + this.lastName : null;
        this.position = blankToNull(position);
        this.role = role;
        this.companyId = companyId;
        this.issuedBy = issuedBy;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static Invitation issue(UUID id, InvitationToken token, Email email, String firstName, String lastName,
                                   String position, Role role, UUID companyId, UUID issuedBy, Instant now) {
        return issue(id, token, email, null, firstName, lastName, position, role, companyId, issuedBy, now);
    }

    /** @param displayName nombre para mostrar tal como lo dio quien invita; si es nulo se arma con nombres y apellidos, si los hay */
    public static Invitation issue(UUID id, InvitationToken token, Email email, String displayName, String firstName,
                                   String lastName, String position, Role role, UUID companyId, UUID issuedBy, Instant now) {
        RequiredFields required = new RequiredFields().value("token", token).value("email", email).value("role", role);
        if (role == Role.OPERATOR) {
            required.text("firstName", firstName).text("lastName", lastName).text("position", position);
        }
        required.verify();
        if (role.belongsToCompany() != (companyId != null)) {
            throw new IllegalArgumentException("Solo la invitación del LICENSE_OWNER carece de empresa.");
        }
        return new Invitation(id, token, email, displayName, firstName, lastName, position, role, companyId, issuedBy,
                InvitationStatus.PENDING, now, now.plus(VALIDITY));
    }

    public static Invitation restore(UUID id, InvitationToken token, Email email, String displayName, String firstName,
                                     String lastName, String position, Role role, UUID companyId, UUID issuedBy,
                                     InvitationStatus status, Instant createdAt, Instant expiresAt) {
        return new Invitation(id, token, email, displayName, firstName, lastName, position, role, companyId, issuedBy,
                status, createdAt, expiresAt);
    }

    /** Estado real en {@code now}: una {@code PENDING} vencida es {@code EXPIRED}. */
    public InvitationStatus effectiveStatus(Instant now) {
        if (status == InvitationStatus.PENDING && !now.isBefore(expiresAt)) {
            return InvitationStatus.EXPIRED;
        }
        return status;
    }

    /** Ocupa un cupo de usuario de su empresa (RF-013): está realmente pendiente y es de una empresa. */
    public boolean occupiesSlot(Instant now) {
        return companyId != null && effectiveStatus(now) == InvitationStatus.PENDING;
    }

    /**
     * Crea la cuenta del invitado con la empresa y el rol de la invitación y pasa la invitación a {@code ACCEPTED}.
     * La contraseña ya viene validada y con hash. Si la invitación no está vigente no cambia nada y lanza el error del estado.
     */
    public User accept(UUID userId, String firstName, String lastName, String passwordHash, Instant now) {
        ensureUsable(now);
        User user = User.createActive(userId, companyId, email, firstName, lastName, position, passwordHash, role, now);
        status = InvitationStatus.ACCEPTED;
        return user;
    }

    /** Solo quien la emitió, y solo si sigue pendiente (US41). */
    public void cancel(UUID requestedBy, Instant now) {
        if (issuedBy == null || !issuedBy.equals(requestedBy)) {
            throw new ForbiddenException(ErrorCodes.INVITATION_NOT_ISSUER, "Solo quien emitió la invitación puede cancelarla.");
        }
        if (effectiveStatus(now) != InvitationStatus.PENDING) {
            throw new ConflictException(ErrorCodes.INVITATION_NOT_PENDING, "Solo se puede cancelar una invitación pendiente.");
        }
        status = InvitationStatus.CANCELLED;
    }

    /** Uso del job de expiración: marca {@code EXPIRED} si venció. Devuelve si cambió. */
    public boolean expire(Instant now) {
        if (status == InvitationStatus.PENDING && !now.isBefore(expiresAt)) {
            status = InvitationStatus.EXPIRED;
            return true;
        }
        return false;
    }

    /** Lanza el error del estado si la invitación no se puede usar para crear una cuenta (vencida, cancelada o ya aceptada). */
    public void ensureUsable(Instant now) {
        switch (effectiveStatus(now)) {
            case PENDING -> { }
            case EXPIRED -> throw new ConflictException(ErrorCodes.INVITATION_EXPIRED, "La invitación expiró.");
            case CANCELLED -> throw new ConflictException(ErrorCodes.INVITATION_CANCELLED, "La invitación fue cancelada.");
            case ACCEPTED -> throw new ConflictException(ErrorCodes.INVITATION_ALREADY_ACCEPTED, "La invitación ya fue utilizada.");
        }
    }

    /** Nombre para mostrar (va en {@code InvitationCreated.fullName}); nulo si quien invita no dio ninguno. */
    public String fullName() {
        return displayName;
    }

    public String displayName() {
        return displayName;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() {
        return id;
    }

    public InvitationToken token() {
        return token;
    }

    public Email email() {
        return email;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String position() {
        return position;
    }

    public Role role() {
        return role;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID issuedBy() {
        return issuedBy;
    }

    /** Estado guardado; para decidir usa {@link #effectiveStatus(Instant)}. */
    public InvitationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
