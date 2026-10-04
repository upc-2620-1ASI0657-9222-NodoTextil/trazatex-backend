package com.nodotextil.trazatex.shared.audit;

import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final SpringDataAuditRecordRepository repository;
    private final Clock clock = Clock.systemUTC();

    public AuditService(SpringDataAuditRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditRecord record(
            AuthenticatedUser user,
            String action,
            String entityType,
            Object entityId,
            String details) {
        AuditRecord record = new AuditRecord(
                UUID.randomUUID(),
                user.userId(),
                user.companyId(),
                action,
                entityType,
                String.valueOf(entityId),
                details,
                LocalDateTime.now(clock));
        return repository.save(new AuditRecordJpaEntity(record)).toRecord();
    }

    @Transactional(readOnly = true)
    public List<AuditRecord> list(UUID companyId) {
        List<AuditRecordJpaEntity> records = companyId == null
                ? repository.findAllByOrderByOccurredAtDesc()
                : repository.findByCompanyIdOrderByOccurredAtDesc(companyId);
        return records.stream().map(AuditRecordJpaEntity::toRecord).toList();
    }
}
