package com.nodotextil.trazatex.organizationaccess.domain.service;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsEightCharactersWithoutCompositionRules() {
        assertThatCode(() -> PasswordPolicy.validate("aaaaaaaa", "aaaaaaaa")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("contraseña larga con espacios", "contraseña larga con espacios"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShorterThanEightCharacters() {
        assertThatThrownBy(() -> PasswordPolicy.validate("1234567", "1234567"))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("PASSWORD_TOO_SHORT");
        assertThatThrownBy(() -> PasswordPolicy.validate(null, null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void countsCharactersNotBytes() {
        assertThatCode(() -> PasswordPolicy.validate("ñññññññ1", "ñññññññ1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMoreThanSeventyTwoBytesWhichBcryptWouldTruncate() {
        String password = "a".repeat(73);
        assertThatThrownBy(() -> PasswordPolicy.validate(password, password))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("PASSWORD_TOO_LONG");
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(72), "a".repeat(72))).doesNotThrowAnyException();
    }

    @Test
    void confirmationMustMatch() {
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678", "12345679"))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("PASSWORD_MISMATCH");
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678", null)).isInstanceOf(ValidationException.class);
    }
}
