package com.nodotextil.trazatex.quality.application.port;

import com.nodotextil.trazatex.quality.domain.Failure;

public interface FailureRepository {

    Failure save(Failure failure);
}
