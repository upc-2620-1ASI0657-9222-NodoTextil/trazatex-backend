package com.nodotextil.trazatex.quality.application.port;

import com.nodotextil.trazatex.quality.domain.QualityEvidence;

public interface QualityEvidenceRepository {

    QualityEvidence save(QualityEvidence evidence);
}
