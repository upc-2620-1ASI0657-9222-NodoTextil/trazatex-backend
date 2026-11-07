package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyPrivacyTest {

    private final UUID companyId = UUID.randomUUID();

    @Test
    void everyConfigurableFieldStartsPrivate() {
        CompanyPrivacy privacy = CompanyPrivacy.defaults(companyId);

        assertThat(privacy.asMap()).hasSize(ConfigurableField.values().length)
                .containsOnlyKeys(ConfigurableField.values())
                .containsValue(Visibility.PRIVATE)
                .doesNotContainValue(Visibility.SHARED);
        assertThat(privacy.settings()).hasSize(11);
    }

    @Test
    void sharingAndMakingPrivateAgain() {
        CompanyPrivacy privacy = CompanyPrivacy.defaults(companyId);

        privacy.update(Map.of("SUPPLIER", Visibility.SHARED, "ORIGIN", Visibility.SHARED));
        assertThat(privacy.visibilityOf(ConfigurableField.SUPPLIER)).isEqualTo(Visibility.SHARED);
        assertThat(privacy.visibilityOf(ConfigurableField.QUANTITY)).isEqualTo(Visibility.PRIVATE);

        privacy.update(Map.of("SUPPLIER", Visibility.PRIVATE));
        assertThat(privacy.visibilityOf(ConfigurableField.SUPPLIER)).isEqualTo(Visibility.PRIVATE);
        assertThat(privacy.visibilityOf(ConfigurableField.ORIGIN)).isEqualTo(Visibility.SHARED);
    }

    @Test
    void alwaysSharedAlwaysPrivateOrUnknownFieldsAreRejectedAndNothingIsApplied() {
        CompanyPrivacy privacy = CompanyPrivacy.defaults(companyId);

        assertThatThrownBy(() -> privacy.update(Map.of("SUPPLIER", Visibility.SHARED, "TRACEABILITY_ID", Visibility.PRIVATE)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo("FIELD_NOT_CONFIGURABLE");
                    assertThat(e.details()).containsExactly("TRACEABILITY_ID");
                });

        assertThat(privacy.visibilityOf(ConfigurableField.SUPPLIER)).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void emptyChangeIsRejected() {
        CompanyPrivacy privacy = CompanyPrivacy.defaults(companyId);
        assertThatThrownBy(() -> privacy.update(Map.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> privacy.update(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void restoreFillsMissingFieldsAsPrivate() {
        CompanyPrivacy privacy = CompanyPrivacy.restore(companyId,
                java.util.List.of(new PrivacySetting(companyId, ConfigurableField.WASTE, Visibility.SHARED)));

        assertThat(privacy.visibilityOf(ConfigurableField.WASTE)).isEqualTo(Visibility.SHARED);
        assertThat(privacy.visibilityOf(ConfigurableField.MACHINERY)).isEqualTo(Visibility.PRIVATE);
        assertThat(privacy.asMap()).hasSize(11);
    }
}
