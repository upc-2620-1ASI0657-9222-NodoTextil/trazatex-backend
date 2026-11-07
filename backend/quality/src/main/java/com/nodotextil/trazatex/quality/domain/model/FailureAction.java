package com.nodotextil.trazatex.quality.domain.model;

/** Qué pasa con la falla cuando termina un control. */
public enum FailureAction {
    /** El control salió con falla: se genera una falla propia (US25 esc. 2, US34 esc. 4). */
    GENERATE,
    /** Una reevaluación vuelve a fallar: se reutiliza la falla existente (US28 esc. 2). */
    REOPEN,
    /** Una reevaluación sale conforme: la falla existente pasa a {@code RESOLVED} (US28 esc. 1). */
    RESOLVE,
    /** Nada con ninguna falla (control conforme sin falla que resolver). */
    NONE
}
