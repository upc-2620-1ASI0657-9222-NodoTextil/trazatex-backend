package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.BatchPhase;

import java.util.UUID;

public sealed interface PhaseCommandOutcome {

    record Confirmed(UUID batchId, BatchPhase phase, boolean qualityHold) implements PhaseCommandOutcome {
    }

    record Rejected(int status, String code, String message) implements PhaseCommandOutcome {
    }

    record Defective(int status, String code, String message) implements PhaseCommandOutcome {
    }

    record Unavailable(String cause, boolean circuitOpen) implements PhaseCommandOutcome {
    }
}
