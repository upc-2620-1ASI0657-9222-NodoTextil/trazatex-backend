package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario de TrazaTex. Solo se crea al aceptar una invitación ({@link Invitation#accept}). El Dueño de Licencia no tiene
 * empresa; los demás roles siempre la tienen. Inactivar conserva el historial (US09). Los intentos fallidos de login y el
 * bloqueo no son del usuario sino del correo ({@link LoginAttempts}), para que existan también con correos sin cuenta.
 */
public final class User {

    private final UUID id;
    private final UUID companyId;
    private final Email email;
    private final String firstName;
    private final String lastName;
    private final String position;
    private final String passwordHash;
    private final Role role;
    private UserStatus status;
    private final Instant createdAt;

    private User(UUID id, UUID companyId, Email email, String firstName, String lastName, String position,
                 String passwordHash, Role role, UserStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.companyId = companyId;
        this.email = email;
        this.firstName = firstName == null ? null : firstName.trim();
        this.lastName = lastName == null ? null : lastName.trim();
        this.position = position == null || position.isBlank() ? null : position.trim();
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Cuenta nueva y activa. Solo la usa {@link Invitation#accept}. */
    static User createActive(UUID id, UUID companyId, Email email, String firstName, String lastName, String position,
                             String passwordHash, Role role, Instant now) {
        new RequiredFields()
                .value("email", email)
                .text("firstName", firstName)
                .text("lastName", lastName)
                .text("passwordHash", passwordHash)
                .value("role", role)
                .verify();
        if (role.belongsToCompany() != (companyId != null)) {
            throw new IllegalArgumentException("Solo el LICENSE_OWNER carece de empresa; los demás roles la requieren.");
        }
        return new User(id, companyId, email, firstName, lastName, position, passwordHash, role,
                UserStatus.ACTIVE, now);
    }

    public static User restore(UUID id, UUID companyId, Email email, String firstName, String lastName, String position,
                               String passwordHash, Role role, UserStatus status, Instant createdAt) {
        return new User(id, companyId, email, firstName, lastName, position, passwordHash, role, status, createdAt);
    }

    // --- Estado (US09) ---

    /**
     * @param activeAdminsInCompany administradores ACTIVE de la empresa, incluido este usuario si lo es. Una empresa debe
     *                              conservar al menos un administrador activo.
     */
    public void deactivate(long activeAdminsInCompany) {
        if (status == UserStatus.INACTIVE) {
            throw new ConflictException(ErrorCodes.INVALID_STATUS_TRANSITION, "El usuario ya está inactivo.");
        }
        if (role == Role.COMPANY_ADMIN && activeAdminsInCompany <= 1) {
            throw new ConflictException(ErrorCodes.LAST_ADMIN_REQUIRED,
                    "La empresa debe conservar al menos un administrador activo.");
        }
        status = UserStatus.INACTIVE;
    }

    /** La capacidad de la licencia (RF-013) se valida antes, con {@link License#ensureUserCapacity}. */
    public void activate() {
        if (status == UserStatus.ACTIVE) {
            throw new ConflictException(ErrorCodes.INVALID_STATUS_TRANSITION, "El usuario ya está activo.");
        }
        status = UserStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
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

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
