package com.nodotextil.trazatex.traceability.domain.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo que no dice {@code SHARED} nunca se comparte (docs/domain/organization-access.md: los configurables son PRIVATE por defecto). */
class CompanyPrivacyTest {

    @Test
    void aCompanyWithoutConfigurationSharesNothing() {
        for (ConfigurableField field : ConfigurableField.values()) {
            assertThat(CompanyPrivacy.ALL_PRIVATE.visibilityOf(field)).isEqualTo(Visibility.PRIVATE);
            assertThat(CompanyPrivacy.ALL_PRIVATE.isShared(field)).isFalse();
        }
    }

    @Test
    void onlyTheFieldsMarkedSharedAreSharedAndTheMissingOnesArePrivate() {
        CompanyPrivacy.Parsed parsed = CompanyPrivacy.fromRaw(Map.of("SUPPLIER", "SHARED", "ORIGIN", "PRIVATE"));

        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.privacy().isShared(ConfigurableField.SUPPLIER)).isTrue();
        assertThat(parsed.privacy().isShared(ConfigurableField.ORIGIN)).isFalse();
        assertThat(parsed.privacy().isShared(ConfigurableField.QUANTITY)).as("ausente del mapa").isFalse();
        assertThat(parsed.privacy().asMap()).hasSize(11).containsEntry(ConfigurableField.SUPPLIER, Visibility.SHARED)
                .containsEntry(ConfigurableField.WASTE, Visibility.PRIVATE);
    }

    @Test
    void anUnknownFieldIsIgnoredWithAWarningAndAnUnknownValueFailsTowardsPrivate() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("TRACEABILITY_ID", "SHARED");
        raw.put("SUPPLIER", "MAYBE");
        raw.put("ORIGIN", null);
        raw.put("QUANTITY", "SHARED");

        CompanyPrivacy.Parsed parsed = CompanyPrivacy.fromRaw(raw);

        assertThat(parsed.warnings()).hasSize(3);
        assertThat(parsed.warnings().get(0)).contains("TRACEABILITY_ID").contains("se ignora");
        assertThat(parsed.privacy().isShared(ConfigurableField.SUPPLIER)).isFalse();
        assertThat(parsed.privacy().isShared(ConfigurableField.ORIGIN)).isFalse();
        assertThat(parsed.privacy().isShared(ConfigurableField.QUANTITY)).isTrue();
    }

    @Test
    void caseMattersSoALowercaseSharedIsNotShared() {
        CompanyPrivacy.Parsed parsed = CompanyPrivacy.fromRaw(Map.of("SUPPLIER", "shared"));

        assertThat(parsed.privacy().isShared(ConfigurableField.SUPPLIER)).isFalse();
        assertThat(parsed.warnings()).hasSize(1);
    }
}
