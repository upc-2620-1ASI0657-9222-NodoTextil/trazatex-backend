package com.nodotextil.trazatex.production.domain.exception;

public final class ErrorCodes {

    public static final String MISSING_FIELDS = "MISSING_FIELDS";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
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
    public static final String PARTIAL_TRANSFER_NOT_ALLOWED = "PARTIAL_TRANSFER_NOT_ALLOWED";
    public static final String INVALID_CODE = "INVALID_CODE";
    public static final String INVALID_REQUEST_ID = "INVALID_REQUEST_ID";
    public static final String NO_FILES = "NO_FILES";
    public static final String TOO_MANY_FILES = "TOO_MANY_FILES";
    public static final String UNSUPPORTED_FILE_FORMAT = "UNSUPPORTED_FILE_FORMAT";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

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

    public static final String BATCH_NOT_FOUND = "BATCH_NOT_FOUND";
    public static final String QR_NOT_FOUND = "QR_NOT_FOUND";
    public static final String MACHINE_NOT_FOUND = "MACHINE_NOT_FOUND";
    public static final String TRANSFORMATION_NOT_FOUND = "TRANSFORMATION_NOT_FOUND";
    public static final String TRANSFER_NOT_FOUND = "TRANSFER_NOT_FOUND";
    public static final String TARGET_COMPANY_NOT_FOUND = "TARGET_COMPANY_NOT_FOUND";
    public static final String INVALID_PAGINATION = "INVALID_PAGINATION";
    public static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    public static final String COMPANY_UNKNOWN = "COMPANY_UNKNOWN";
    public static final String MACHINE_CODE_ALREADY_EXISTS = "MACHINE_CODE_ALREADY_EXISTS";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    public static final String TEMPORARILY_UNAVAILABLE = "TEMPORARILY_UNAVAILABLE";

    public static final String NOT_TRANSFER_RECIPIENT = "NOT_TRANSFER_RECIPIENT";

    private ErrorCodes() {
    }
}
