package com.nodotextil.trazatex.quality.application.usecase;

interface PendingCommandSource {

    enum Kind {
        START,
        FINISH,
        DISCARD,
        HOLD
    }

    Kind kind();

    int recover();
}
