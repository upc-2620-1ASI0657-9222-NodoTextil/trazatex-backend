package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void normalizesCaseAndSpaces() {
        assertThat(new Email("  Ana.Perez@Textil.PE ").value()).isEqualTo("ana.perez@textil.pe");
        assertThat(new Email("A@b.pe")).isEqualTo(new Email("a@B.PE"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "sin-arroba", "a@b", "a b@c.pe", "@c.pe", "a@@c.pe"})
    void rejectsInvalidEmails(String value) {
        assertThatThrownBy(() -> new Email(value)).isInstanceOf(ValidationException.class);
    }
}
