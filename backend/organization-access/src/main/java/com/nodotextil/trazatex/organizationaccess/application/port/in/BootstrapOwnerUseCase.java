package com.nodotextil.trazatex.organizationaccess.application.port.in;

public interface BootstrapOwnerUseCase {

    enum Outcome {
        LICENSE_CREATED,
        OWNER_INVITATION_REISSUED,
        NOTHING_TO_DO,
        OWNER_INVITATION_BLOCKED
    }

    Outcome run();
}
