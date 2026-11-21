package com.nodotextil.trazatex.notification.application.port.out;

public class EmailDeliveryException extends RuntimeException {
    public enum Kind {
        TRANSIENT,
        PERMANENT,
        UNAVAILABLE
    }

    private final Kind kind;
    private final String reason;

    public EmailDeliveryException(Kind kind, String reason, Throwable cause) {
        super(reason, cause);
        this.kind = kind;
        this.reason = reason;
    }
    public Kind kind() { return kind; }
    public String reason() { return reason; }
}
