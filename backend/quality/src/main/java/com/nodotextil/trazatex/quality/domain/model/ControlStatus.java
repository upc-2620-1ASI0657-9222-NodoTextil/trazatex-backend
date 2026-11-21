package com.nodotextil.trazatex.quality.domain.model;

public enum ControlStatus {
    STARTING,
    IN_PROGRESS,
    FINISHED,
    CANCELLED;

    public boolean active() {
        return this == STARTING || this == IN_PROGRESS;
    }
}
