package com.nodotextil.trazatex.quality.domain.exception;

/**
 * Códigos estables de error del dominio. Las reglas lanzan las excepciones base del shared-kernel
 * ({@code ValidationException}, {@code ConflictException}) con uno de estos códigos; el {@code @ControllerAdvice} los lleva a
 * {@code ApiError.code}.
 */
public final class ErrorCodes {

    // Validación (400)
    public static final String MISSING_FIELDS = "MISSING_FIELDS";
    /** La cantidad afectada de una falla debe ser mayor que cero. */
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    /** El aviso de Traceability no trae el antecesor común: sin él no se puede deducir el tipo de impacto. */
    public static final String INVALID_IMPACT = "INVALID_IMPACT";

    // Iniciar un control (409), US23 / US34
    /** US23 esc. 3: el lote ya posee un control activo (su fase es {@code IN_QUALITY_CONTROL}). */
    public static final String CONTROL_ALREADY_ACTIVE = "CONTROL_ALREADY_ACTIVE";
    /** Un control solo se inicia sobre un lote {@code AVAILABLE}. */
    public static final String BATCH_NOT_AVAILABLE_FOR_CONTROL = "BATCH_NOT_AVAILABLE_FOR_CONTROL";
    /** Un lote {@code CONFORMING} no recibe otro control (decisión del usuario, 2026-10-23). */
    public static final String BATCH_ALREADY_CONFORMING = "BATCH_ALREADY_CONFORMING";
    /** Un lote {@code FAILED} no se controla: primero se envía a reevaluación (US26, decisión del usuario, 2026-10-23). */
    public static final String BATCH_FAILED_REQUIRES_REEVALUATION = "BATCH_FAILED_REQUIRES_REEVALUATION";
    /** La proyección aún no conoce el estado de calidad del lote (solo llegó su {@code BatchPhaseChanged}): no se opera a ciegas. */
    public static final String BATCH_QUALITY_UNKNOWN = "BATCH_QUALITY_UNKNOWN";

    // Evidencias (US43)
    public static final String NO_FILES = "NO_FILES";
    public static final String TOO_MANY_FILES = "TOO_MANY_FILES";
    /** La petición no dice a qué se adjunta, o dice a dos cosas a la vez: exactamente uno de {@code controlId} / {@code failureId}. */
    public static final String EVIDENCE_OWNER_REQUIRED = "EVIDENCE_OWNER_REQUIRED";
    public static final String UNSUPPORTED_FILE_FORMAT = "UNSUPPORTED_FILE_FORMAT";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

    // Estado del control (409)
    public static final String INVALID_CONTROL_TRANSITION = "INVALID_CONTROL_TRANSITION";
    /** Agregar pruebas y finalizar exigen un control {@code IN_PROGRESS}. */
    public static final String CONTROL_NOT_IN_PROGRESS = "CONTROL_NOT_IN_PROGRESS";
    /** Finalizar un control sin ninguna prueba se rechaza (decisión del usuario, 2026-10-23). */
    public static final String CONTROL_HAS_NO_TESTS = "CONTROL_HAS_NO_TESTS";
    /** El reporte PDF solo se genera para un control {@code FINISHED}. */
    public static final String CONTROL_NOT_FINISHED = "CONTROL_NOT_FINISHED";

    // Estado de la falla y del lote (409)
    public static final String INVALID_FAILURE_TRANSITION = "INVALID_FAILURE_TRANSITION";
    /** Finalizar está pidiendo RELEASE a Production y no hay respuesta todavía: las pruebas están congeladas. */
    public static final String CONTROL_FINISH_PENDING = "CONTROL_FINISH_PENDING";
    /** Descartar está pidiendo DISCARD a Production y no hay respuesta todavía: la falla no admite otro cambio. */
    public static final String FAILURE_DISCARD_PENDING = "FAILURE_DISCARD_PENDING";
    /** Confirmar (descartar) una falla exige causa no vacía y cantidad afectada mayor que cero (decisión del usuario, 2026-10-23). */
    public static final String FAILURE_INFO_INCOMPLETE = "FAILURE_INFO_INCOMPLETE";
    public static final String INVALID_QUALITY_TRANSITION = "INVALID_QUALITY_TRANSITION";
    public static final String CONTROL_BATCH_MISMATCH = "CONTROL_BATCH_MISMATCH";
    public static final String INVALID_LOCK_TRANSITION = "INVALID_LOCK_TRANSITION";

    // No encontrado (404)
    /** El lote no existe, o es de otra empresa (no se revela que existe), o Production respondió 404. */
    public static final String BATCH_NOT_FOUND = "BATCH_NOT_FOUND";
    public static final String CONTROL_NOT_FOUND = "CONTROL_NOT_FOUND";
    public static final String FAILURE_NOT_FOUND = "FAILURE_NOT_FOUND";

    private ErrorCodes() {
    }
}
