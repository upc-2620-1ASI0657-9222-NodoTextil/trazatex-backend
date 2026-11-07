package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RucTest {

    /** Semilla fija: los casos son aleatorios pero idénticos en cada corrida, así que cualquier fallo se reproduce. */
    private static final long SEED = 20261019L;
    private static final int CASES = 1000;

    @ParameterizedTest
    @ValueSource(strings = {"20100070970", "20131312955", "20100047218"})
    void acceptsRealRucsWithValidCheckDigit(String ruc) {
        assertThat(Ruc.isValid(ruc)).isTrue();
        assertThat(new Ruc(ruc).value()).isEqualTo(ruc);
    }

    @ParameterizedTest(name = "prefijo {0}")
    @ValueSource(strings = {"10", "15", "16", "17", "20"})
    void acceptsTheFiveValidPrefixes(String prefix) {
        String body = prefix + "00700709";
        assertThat(Ruc.isValid(body + Ruc.checkDigitFor(body))).isTrue();
    }

    @ParameterizedTest(name = "prefijo {0}")
    @ValueSource(strings = {"00", "11", "12", "14", "18", "19", "21", "25", "30", "40", "99"})
    void rejectsAnyOtherPrefixEvenWithAValidCheckDigit(String prefix) {
        String body = prefix + "00700709";
        String ruc = body + Ruc.checkDigitFor(body);

        assertThat(Ruc.isValid(ruc)).isFalse();
        assertThatThrownBy(() -> new Ruc(ruc)).isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20100070971", "20131312950", "2010007097", "201000709700", "2010007097a", "", "abcdefghijk", "２０１００００７０９７"})
    void rejectsWrongCheckDigitLengthOrNonDigits(String ruc) {
        assertThat(Ruc.isValid(ruc)).isFalse();
        assertThatThrownBy(() -> new Ruc(ruc)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsNull() {
        assertThat(Ruc.isValid(null)).isFalse();
        assertThatThrownBy(() -> new Ruc(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void trimsSurroundingSpaces() {
        assertThat(new Ruc(" 20100070970 ").value()).isEqualTo("20100070970");
    }

    @ParameterizedTest(name = "cuerpo {0}")
    @MethodSource("randomTenDigitPrefixes")
    void everyBodyWithAValidPrefixHasExactlyOneValidCheckDigit(String prefix) {
        long valid = IntStream.rangeClosed(0, 9).filter(digit -> Ruc.isValid(prefix + digit)).count();
        assertThat(valid).isEqualTo(1);
    }

    @ParameterizedTest(name = "cuerpo {0}")
    @MethodSource("randomTenDigitPrefixes")
    void checkDigitForBuildsAValidRuc(String prefix) {
        assertThat(Ruc.isValid(prefix + Ruc.checkDigitFor(prefix))).isTrue();
    }

    /** Los 10 primeros dígitos de un RUC con prefijo válido: los extremos más {@value #CASES} cuerpos al azar con semilla fija. */
    static Stream<String> randomTenDigitPrefixes() {
        Random random = new Random(SEED);
        String[] prefixes = Ruc.VALID_PREFIXES.stream().sorted().toArray(String[]::new);
        Stream<String> edges = Stream.of("1000000000", "2099999999", "2010007097", "2013131295");
        Stream<String> generated = Stream.generate(() -> {
            StringBuilder body = new StringBuilder(prefixes[random.nextInt(prefixes.length)]);
            for (int i = 0; i < 8; i++) {
                body.append((char) ('0' + random.nextInt(10)));
            }
            return body.toString();
        }).limit(CASES);
        return Stream.concat(edges, generated);
    }
}
