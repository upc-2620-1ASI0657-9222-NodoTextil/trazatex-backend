package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.port.PrivacySettingRepository;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PrivacySettingPersistenceAdapter implements PrivacySettingRepository {

	private final SpringDataPrivacySettingRepository repository;

	PrivacySettingPersistenceAdapter(SpringDataPrivacySettingRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<CompanyPrivacySetting> findByCompanyId(UUID companyId) {
		return repository.findByCompanyId(companyId).stream()
				.map(PrivacySettingJpaEntity::toDomain).toList();
	}

	@Override
	public void saveAll(Collection<CompanyPrivacySetting> settings) {
		Map<UUID, Map<PrivacyField, PrivacySettingJpaEntity>> existing = new HashMap<>();
		List<PrivacySettingJpaEntity> toSave = new ArrayList<>();
		for (CompanyPrivacySetting setting : settings) {
			PrivacySettingJpaEntity entity = existing
					.computeIfAbsent(setting.companyId(), this::loadByField)
					.get(setting.field());
			if (entity == null) {
				toSave.add(PrivacySettingJpaEntity.from(setting));
			}
			else {
				entity.visibility = setting.visibility();
				toSave.add(entity);
			}
		}
		repository.saveAll(toSave);
	}

	private Map<PrivacyField, PrivacySettingJpaEntity> loadByField(UUID companyId) {
		Map<PrivacyField, PrivacySettingJpaEntity> byField = new HashMap<>();
		repository.findByCompanyId(companyId).forEach(entity -> byField.put(entity.field,
				entity));
		return byField;
	}
}
