package com.nodotextil.trazatex.organizationaccess.application.port.in;

/** ADR-013 y su ampliación (recuperación del Dueño). Idempotente: se puede ejecutar en cada arranque. */
public interface BootstrapOwnerUseCase {

    enum Outcome {
        /** No había licencia: se creó con los límites del entorno y se invitó al Dueño. */
        LICENSE_CREATED,
        /** Había licencia pero ningún Dueño activo ni invitación de Dueño vigente: se emitió una nueva. */
        OWNER_INVITATION_REISSUED,
        /** Ya hay un Dueño activo o una invitación de Dueño vigente. */
        NOTHING_TO_DO,
        /** Había que reinvitar al Dueño pero el correo ya tiene una cuenta (u otra invitación): no se pudo y se avisa. */
        OWNER_INVITATION_BLOCKED
    }

    Outcome run();
}
