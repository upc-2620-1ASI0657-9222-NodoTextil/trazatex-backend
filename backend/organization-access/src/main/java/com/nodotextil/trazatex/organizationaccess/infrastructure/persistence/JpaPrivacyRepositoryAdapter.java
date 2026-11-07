package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.out.PrivacyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.organizationaccess.domain.model.PrivacySetting;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class JpaPrivacyRepositoryAdapter implements PrivacyRepositoryPort {

    private final PrivacySettingJpaRepository repository;

    JpaPrivacyRepositoryAdapter(PrivacySettingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompanyPrivacy find(UUID companyId) {
        return CompanyPrivacy.restore(companyId, repository.findByIdCompanyId(companyId).stream()
                .map(entity -> new PrivacySetting(entity.id.companyId(), entity.id.field(), entity.visibility))
                .toList());
    }

    @Override
    public void save(CompanyPrivacy privacy) {
        privacy.settings().forEach(setting -> {
            PrivacySettingEntity entity = new PrivacySettingEntity();
            entity.id = new PrivacySettingEntity.Key(setting.companyId(), setting.field());
            entity.visibility = setting.visibility();
            repository.save(entity);
        });
    }
}
