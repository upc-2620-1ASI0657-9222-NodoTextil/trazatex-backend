package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.PrivacyAuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;


@Repository
class PrivacyAuditPersistenceAdapter implements PrivacyAuditPort {

	private static final Logger log = LoggerFactory.getLogger(PrivacyAuditPersistenceAdapter.class);

	private final SpringDataPrivacyAuditRepository repository;

	PrivacyAuditPersistenceAdapter(SpringDataPrivacyAuditRepository repository) {
		this.repository = repository;
	}

	@Override
	public void record(PrivacyChange change) {
		repository.save(PrivacyAuditJpaEntity.from(change));
		log.info("Privacy change: company={} user={} field={} {} -> {}", change.companyId(),
				change.changedByUserId(), change.field(), change.previous(), change.current());
	}
}
