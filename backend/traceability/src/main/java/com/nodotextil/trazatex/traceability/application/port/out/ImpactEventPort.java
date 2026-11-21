package com.nodotextil.trazatex.traceability.application.port.out;

import com.nodotextil.trazatex.traceability.domain.model.impact.ImpactResult;

import java.util.UUID;

public interface ImpactEventPort {

    boolean alreadyRecorded(UUID failureId);

    void record(UUID failureId, ImpactResult result);
}
