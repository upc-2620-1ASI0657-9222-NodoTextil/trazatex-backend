package com.nodotextil.trazatex.quality.domain.exception;

public final class ErrorCodes {

    public static final String MISSING_FIELDS = "MISSING_FIELDS";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    public static final String INVALID_IMPACT = "INVALID_IMPACT";

    public static final String CONTROL_ALREADY_ACTIVE = "CONTROL_ALREADY_ACTIVE";
    public static final String BATCH_NOT_AVAILABLE_FOR_CONTROL = "BATCH_NOT_AVAILABLE_FOR_CONTROL";
    public static final String BATCH_ALREADY_CONFORMING = "BATCH_ALREADY_CONFORMING";
    public static final String BATCH_FAILED_REQUIRES_REEVALUATION = "BATCH_FAILED_REQUIRES_REEVALUATION";
    public static final String BATCH_QUALITY_UNKNOWN = "BATCH_QUALITY_UNKNOWN";

    public static final String NO_FILES = "NO_FILES";
    public static final String TOO_MANY_FILES = "TOO_MANY_FILES";
    public static final String EVIDENCE_OWNER_REQUIRED = "EVIDENCE_OWNER_REQUIRED";
    public static final String UNSUPPORTED_FILE_FORMAT = "UNSUPPORTED_FILE_FORMAT";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

    public static final String INVALID_CONTROL_TRANSITION = "INVALID_CONTROL_TRANSITION";
    public static final String CONTROL_NOT_IN_PROGRESS = "CONTROL_NOT_IN_PROGRESS";
    public static final String CONTROL_HAS_NO_TESTS = "CONTROL_HAS_NO_TESTS";
    public static final String CONTROL_NOT_FINISHED = "CONTROL_NOT_FINISHED";

    public static final String INVALID_FAILURE_TRANSITION = "INVALID_FAILURE_TRANSITION";
    public static final String CONTROL_FINISH_PENDING = "CONTROL_FINISH_PENDING";
    public static final String FAILURE_DISCARD_PENDING = "FAILURE_DISCARD_PENDING";
    public static final String FAILURE_INFO_INCOMPLETE = "FAILURE_INFO_INCOMPLETE";
    public static final String INVALID_QUALITY_TRANSITION = "INVALID_QUALITY_TRANSITION";
    public static final String CONTROL_BATCH_MISMATCH = "CONTROL_BATCH_MISMATCH";
    public static final String INVALID_LOCK_TRANSITION = "INVALID_LOCK_TRANSITION";

    public static final String BATCH_NOT_FOUND = "BATCH_NOT_FOUND";
    public static final String CONTROL_NOT_FOUND = "CONTROL_NOT_FOUND";
    public static final String FAILURE_NOT_FOUND = "FAILURE_NOT_FOUND";

    private ErrorCodes() {
    }
}
