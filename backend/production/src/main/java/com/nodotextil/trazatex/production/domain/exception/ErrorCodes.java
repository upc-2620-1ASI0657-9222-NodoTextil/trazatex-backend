package com.nodotextil.trazatex.production.domain.exception;

/**
 * Códigos estables de error del dominio. Las reglas lanzan las excepciones base del shared-kernel
 * ({@code ValidationException}, {@code ConflictException}, {@code ForbiddenException}) con uno de estos códigos;
 * el {@code @ControllerAdvice} los lleva a {@code ApiError.code}.
 */
public final class ErrorCodes {

    // Validación (400)
    public static final String MISSING_FIELDS = "MISSING_FIELDS";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    /** Balance de masa roto: la salida más el desperdicio supera la entrada (US18 esc. 2). */
    public static final String INVALID_QUANTITIES = "INVALID_QUANTITIES";
    public static final String INVALID_COMPOSITION = "INVALID_COMPOSITION";
    public static final String SPLIT_REQUIRES_TWO_CHILDREN = "SPLIT_REQUIRES_TWO_CHILDREN";
    public static final String SPLIT_QUANTITIES_MISMATCH = "SPLIT_QUANTITIES_MISMATCH";
    public static final String WASTE_REASON_REQUIRED = "WASTE_REASON_REQUIRED";
    public static final String YIELD_BELOW_MINIMUM = "YIELD_BELOW_MINIMUM";
    public static final String PRICE_REQUIRES_CURRENCY = "PRICE_REQUIRES_CURRENCY";
    public static final String INVALID_COMMERCIAL_DATA = "INVALID_COMMERCIAL_DATA";
    public static final String NO_BATCHES_SELECTED = "NO_BATCHES_SELECTED";
    public static final String DUPLICATE_BATCH = "DUPLICATE_BATCH";
    public static final String REJECTION_REASON_REQUIRED = "REJECTION_REASON_REQUIRED";
    public static final String SAME_COMPANY_TRANSFER = "SAME_COMPANY_TRANSFER";
    /** US21 esc. 2: se pidió transferir solo una parte de un lote; hay que dividirlo antes. */
    public static final String PARTIAL_TRANSFER_NOT_ALLOWED = "PARTIAL_TRANSFER_NOT_ALLOWED";
    public static final String INVALID_CODE = "INVALID_CODE";
    /** El {@code requestId} del comando interno de Quality es más largo de lo que admite {@code phase_command_log} (64). */
    public static final String INVALID_REQUEST_ID = "INVALID_REQUEST_ID";
    /** Evidencias (US42): no llegó ningún archivo. */
    public static final String NO_FILES = "NO_FILES";
    public static final String TOO_MANY_FILES = "TOO_MANY_FILES";
    /** El archivo no es JPEG, PNG ni WebP (por sus primeros bytes), o Cloudinary lo rechazó como imagen inválida. */
    public static final String UNSUPPORTED_FILE_FORMAT = "UNSUPPORTED_FILE_FORMAT";
    /** 413: un archivo supera el máximo (10 MB). */
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

    // Estado (409)
    public static final String INVALID_PHASE_TRANSITION = "INVALID_PHASE_TRANSITION";
    public static final String BATCH_ON_QUALITY_HOLD = "BATCH_ON_QUALITY_HOLD";
    public static final String BATCH_IS_FINAL_PRODUCT = "BATCH_IS_FINAL_PRODUCT";
    public static final String NOT_A_FINAL_PRODUCT = "NOT_A_FINAL_PRODUCT";
    public static final String BATCH_COMPANY_MISMATCH = "BATCH_COMPANY_MISMATCH";
    public static final String BATCHES_DO_NOT_MATCH = "BATCHES_DO_NOT_MATCH";
    public static final String MACHINE_NOT_ACTIVE = "MACHINE_NOT_ACTIVE";
    public static final String MACHINE_COMPANY_MISMATCH = "MACHINE_COMPANY_MISMATCH";
    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
    public static final String TRANSFORMATION_NOT_IN_PROGRESS = "TRANSFORMATION_NOT_IN_PROGRESS";
    public static final String TRANSFER_NOT_PENDING = "TRANSFER_NOT_PENDING";
    public static final String COMPANY_INACTIVE = "COMPANY_INACTIVE";
    public static final String TARGET_COMPANY_INACTIVE = "TARGET_COMPANY_INACTIVE";

    // Aplicación: no encontrado, paginación y concurrencia
    public static final String BATCH_NOT_FOUND = "BATCH_NOT_FOUND";
    public static final String QR_NOT_FOUND = "QR_NOT_FOUND";
    public static final String MACHINE_NOT_FOUND = "MACHINE_NOT_FOUND";
    public static final String TRANSFORMATION_NOT_FOUND = "TRANSFORMATION_NOT_FOUND";
    public static final String TRANSFER_NOT_FOUND = "TRANSFER_NOT_FOUND";
    public static final String TARGET_COMPANY_NOT_FOUND = "TARGET_COMPANY_NOT_FOUND";
    public static final String INVALID_PAGINATION = "INVALID_PAGINATION";
    public static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    /** La empresa del usuario aún no llegó a la proyección local (evento no consumido): no se opera sobre un estado sin verificar. */
    public static final String COMPANY_UNKNOWN = "COMPANY_UNKNOWN";
    public static final String MACHINE_CODE_ALREADY_EXISTS = "MACHINE_CODE_ALREADY_EXISTS";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    /** 503 del comando interno: la carrera no se resolvió tras los reintentos; Quality repite el comando con el mismo requestId. */
    public static final String TEMPORARILY_UNAVAILABLE = "TEMPORARILY_UNAVAILABLE";

    // Acceso
    public static final String NOT_TRANSFER_RECIPIENT = "NOT_TRANSFER_RECIPIENT";

    private ErrorCodes() {
    }
}
