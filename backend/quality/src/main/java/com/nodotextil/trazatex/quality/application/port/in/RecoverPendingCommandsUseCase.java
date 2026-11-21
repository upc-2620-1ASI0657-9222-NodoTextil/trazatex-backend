package com.nodotextil.trazatex.quality.application.port.in;

public interface RecoverPendingCommandsUseCase {

    int recoverPending();

    int recoverPendingHolds();

    int recoverAll();
}
