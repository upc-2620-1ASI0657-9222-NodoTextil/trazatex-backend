package com.nodotextil.trazatex.organizationaccess.domain.exception;

/**
 * Códigos estables de error del dominio. Las reglas lanzan las excepciones base del shared-kernel
 * ({@code ValidationException}, {@code ConflictException}, {@code ForbiddenException}) con uno de estos códigos;
 * el {@code @ControllerAdvice} los lleva a {@code ApiError.code}.
 */
public final class ErrorCodes {

    // Validación
    public static final String MISSING_FIELDS = "MISSING_FIELDS";
    public static final String EMAIL_INVALID = "EMAIL_INVALID";
    public static final String RUC_INVALID = "RUC_INVALID";
    public static final String FIELD_NOT_CONFIGURABLE = "FIELD_NOT_CONFIGURABLE";
    public static final String PASSWORD_TOO_SHORT = "PASSWORD_TOO_SHORT";
    public static final String PASSWORD_TOO_LONG = "PASSWORD_TOO_LONG";
    public static final String PASSWORD_MISMATCH = "PASSWORD_MISMATCH";

    public static final String PASSWORD_COMPROMISED = "PASSWORD_COMPROMISED";

    // Empresas
    public static final String RUC_ALREADY_REGISTERED = "RUC_ALREADY_REGISTERED";
    public static final String RUC_VALIDATION_UNAVAILABLE = "RUC_VALIDATION_UNAVAILABLE";
    public static final String COMPANY_INACTIVE = "COMPANY_INACTIVE";

    // Capacidad de la licencia
    public static final String COMPANY_LIMIT_REACHED = "COMPANY_LIMIT_REACHED";
    public static final String USER_LIMIT_REACHED = "USER_LIMIT_REACHED";

    // Estados
    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
    public static final String LAST_ADMIN_REQUIRED = "LAST_ADMIN_REQUIRED";

    // Invitaciones
    public static final String INVITATION_EXPIRED = "INVITATION_EXPIRED";
    public static final String INVITATION_CANCELLED = "INVITATION_CANCELLED";
    public static final String INVITATION_ALREADY_ACCEPTED = "INVITATION_ALREADY_ACCEPTED";
    public static final String INVITATION_NOT_PENDING = "INVITATION_NOT_PENDING";
    public static final String INVITATION_NOT_ISSUER = "INVITATION_NOT_ISSUER";
    public static final String INVITATION_ALREADY_PENDING = "INVITATION_ALREADY_PENDING";
    public static final String EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED";

    // Solicitudes de administrador
    public static final String ADMIN_REQUEST_NOT_PENDING = "ADMIN_REQUEST_NOT_PENDING";

    private ErrorCodes() {
    }
}
