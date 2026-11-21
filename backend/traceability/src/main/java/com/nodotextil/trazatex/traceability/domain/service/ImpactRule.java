package com.nodotextil.trazatex.traceability.domain.service;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;

import java.util.UUID;

public final class ImpactRule {

    private ImpactRule() {
    }

    public static boolean isAffected(BatchData batch, UUID sourceBatchId) {
        return batch.isComplete()
                && !batch.batchId().equals(sourceBatchId)
                && batch.phase() == BatchPhase.AVAILABLE
                && batch.qualityStatus() != QualityStatus.FAILED;
    }

    public static boolean isCloserToTheFailedBatch(int levelOfA, UUID a, int levelOfB, UUID b) {
        if (levelOfA != levelOfB) {
            return levelOfA < levelOfB;
        }
        return a.toString().compareTo(b.toString()) < 0;
    }
}
