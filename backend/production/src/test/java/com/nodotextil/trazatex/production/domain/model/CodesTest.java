package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Traceability ID y QR (decisión 3 de la fase 04): Crockford Base32, sin O, I, L ni U. */
class CodesTest {

    private static final long SEED = 20261020L;
    private static final Pattern TRACEABILITY_ID = Pattern.compile("^TT-[0-9A-HJKMNP-TV-Z]{10}$");
    private static final Pattern QR = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    @Test
    void alphabetHasExactly32SymbolsAndNoConfusingLetters() {
        assertThat(Crockford32.ALPHABET).hasSize(32).doesNotContain("O", "I", "L", "U");
        assertThat(Crockford32.ALPHABET.chars().distinct().count()).isEqualTo(32);
    }

    @Test
    void generatedTraceabilityIdsHaveTheDocumentedFormat() {
        Random random = new Random(SEED);
        for (int i = 0; i < 5000; i++) {
            assertThat(TraceabilityId.generate(random).value()).matches(TRACEABILITY_ID);
        }
    }

    @Test
    void generatedQrCodesHaveTheDocumentedFormatAndDifferFromTheTraceabilityId() {
        Random random = new Random(SEED);
        for (int i = 0; i < 5000; i++) {
            TraceabilityId id = TraceabilityId.generate(random);
            QrCode qr = QrCode.generate(random);
            assertThat(qr.value()).matches(QR);
            assertThat(qr.value()).isNotEqualTo(id.value()).doesNotContain(id.value().substring(3));
        }
    }

    @Test
    void tenThousandDrawsDoNotCollide() {
        Random random = new Random(SEED);
        Set<String> ids = new HashSet<>();
        Set<String> qrs = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(ids.add(TraceabilityId.generate(random).value())).isTrue();
            assertThat(qrs.add(QrCode.generate(random).value())).isTrue();
        }
    }

    @Test
    void secureRandomIsAcceptedAsTheSource() {
        assertThat(TraceabilityId.generate(new SecureRandom()).value()).matches(TRACEABILITY_ID);
        assertThat(QrCode.generate(new SecureRandom()).value()).matches(QR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TT-ABCDEFGHJK", "TT-0123456789", "TT-ZZZZZZZZZZ"})
    void acceptsWellFormedTraceabilityIds(String value) {
        assertThat(new TraceabilityId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "TT-", "TT-ABC", "TT-ABCDEFGHJKM", "XX-ABCDEFGHJK", "tt-abcdefghjk", "TT-ABCDEFGHJO",
            "TT-ABCDEFGHJI", "TT-ABCDEFGHJL", "TT-ABCDEFGHJU", "TT-ABCDEFGH K", "ABCDEFGHJK"})
    void rejectsMalformedTraceabilityIds(String value) {
        assertThatThrownBy(() -> new TraceabilityId(value))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_CODE));
    }

    @Test
    void parseReadsWhatAPersonTypesByHand() {
        // minúsculas, espacios alrededor y O/I/L escritas por 0/1 (Crockford): el código emitido nunca las contiene
        assertThat(TraceabilityId.parse("  tt-abcdefghjk ").value()).isEqualTo("TT-ABCDEFGHJK");
        assertThat(TraceabilityId.parse("TT-O1IL0O1ILA").value()).isEqualTo("TT-011100111A");
        assertThat(TraceabilityId.parse("TT-OOOOOOOOOO").value()).isEqualTo("TT-0000000000");
        assertThat(TraceabilityId.parse("TT-IIIIILLLLL").value()).isEqualTo("TT-1111111111");
    }

    @Test
    void parseDoesNotCorrectTheLetterU() {
        assertThatThrownBy(() -> TraceabilityId.parse("TT-UUUUUUUUUU")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> TraceabilityId.parse(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void qrParseNormalizesAndValidates() {
        String qr = QrCode.generate(new Random(SEED)).value();
        assertThat(QrCode.parse(" " + qr.toLowerCase() + " ").value()).isEqualTo(qr);
        assertThatThrownBy(() -> QrCode.parse(qr.substring(1))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> QrCode.parse(null)).isInstanceOf(ValidationException.class);
        assertThat(QrCode.isValid(qr)).isTrue();
        assertThat(QrCode.isValid("nope")).isFalse();
    }
}
