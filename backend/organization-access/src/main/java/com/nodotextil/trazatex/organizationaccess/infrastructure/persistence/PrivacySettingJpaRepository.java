package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface PrivacySettingJpaRepository extends JpaRepository<PrivacySettingEntity, PrivacySettingEntity.Key> {

    List<PrivacySettingEntity> findByIdCompanyId(UUID companyId);
}
