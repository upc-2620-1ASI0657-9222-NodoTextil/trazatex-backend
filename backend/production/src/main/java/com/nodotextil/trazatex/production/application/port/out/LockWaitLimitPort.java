package com.nodotextil.trazatex.production.application.port.out;

import java.time.Duration;

public interface LockWaitLimitPort {

    void limit(Duration wait);
}
