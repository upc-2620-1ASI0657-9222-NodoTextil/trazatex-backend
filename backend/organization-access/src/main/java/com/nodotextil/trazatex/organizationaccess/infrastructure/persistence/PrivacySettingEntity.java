package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.model.ConfigurableField;
import com.nodotextil.trazatex.organizationaccess.domain.model.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "privacy_settings")
class PrivacySettingEntity {

    @EmbeddedId
    Key id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Visibility visibility;

    protected PrivacySettingEntity() {
    }

    @Embeddable
    record Key(
            @Column(name = "company_id") UUID companyId,
            @Enumerated(EnumType.STRING) @Column(name = "field") ConfigurableField field) implements Serializable {
    }
}
