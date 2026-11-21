package com.nodotextil.trazatex.quality.domain.model;

public enum DerivedFailureDecision {
    HOLD_NOW(LockStatus.PENDING, true, false),
    WAIT_FOR_AVAILABLE(LockStatus.PENDING, false, true),
    NO_ACTION_HISTORIC(LockStatus.NO_ACTION, false, true),
    ALREADY_RETAINED(LockStatus.NO_ACTION, false, false);

    private final LockStatus initialLockStatus;
    private final boolean sendsHold;
    private final boolean warns;

    DerivedFailureDecision(LockStatus initialLockStatus, boolean sendsHold, boolean warns) {
        this.initialLockStatus = initialLockStatus;
        this.sendsHold = sendsHold;
        this.warns = warns;
    }

    public LockStatus initialLockStatus() {
        return initialLockStatus;
    }

    public boolean sendsHold() {
        return sendsHold;
    }

    public boolean warns() {
        return warns;
    }
}
