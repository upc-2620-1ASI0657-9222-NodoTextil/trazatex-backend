package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.QualityEvidenceRepository;
import com.nodotextil.trazatex.quality.domain.QualityEvidence;
import org.springframework.stereotype.Repository;

@Repository
public class QualityEvidencePersistenceAdapter implements QualityEvidenceRepository {

    private final SpringDataQualityEvidenceRepository repository;

    public QualityEvidencePersistenceAdapter(SpringDataQualityEvidenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public QualityEvidence save(QualityEvidence evidence) {
        return repository.save(QualityEvidenceJpaEntity.fromDomain(evidence)).toDomain();
    }
}
