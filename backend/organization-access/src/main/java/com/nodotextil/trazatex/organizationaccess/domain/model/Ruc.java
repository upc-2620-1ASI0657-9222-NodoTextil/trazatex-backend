package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.List;
import java.util.Set;

/**
 * RUC peruano (primer paso de ADR-012; el estado ACTIVO/HABIDO lo valida Chequea.pe): 11 dígitos, que empiezan con
 * {@value #PREFIXES_TEXT} (los únicos que acepta Chequea.pe) y con dígito verificador módulo 11. El prefijo se comprueba
 * <b>antes</b> del módulo 11, así que un RUC con prefijo inválido ni siquiera llega a la API.
 */
public record Ruc(String value) {

    public static final int LENGTH = 11;
    public static final Set<String> VALID_PREFIXES = Set.of("10", "15", "16", "17", "20");
    private static final String PREFIXES_TEXT = "10, 15, 16, 17 o 20";
    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    public Ruc {
        value = value == null ? null : value.trim();
        if (!isValid(value)) {
            throw new ValidationException(ErrorCodes.RUC_INVALID, "El RUC no es válido.", List.of());
        }
    }

    public static boolean isValid(String candidate) {
        if (candidate == null || candidate.length() != LENGTH || !allAsciiDigits(candidate)
                || !VALID_PREFIXES.contains(candidate.substring(0, 2))) {
            return false;
        }
        return checkDigitFor(candidate.substring(0, LENGTH - 1)) == candidate.charAt(LENGTH - 1) - '0';
    }

    /** Dígito verificador de los 10 primeros dígitos: {@code (11 - suma mod 11) mod 10}. */
    public static int checkDigitFor(String firstTenDigits) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (firstTenDigits.charAt(i) - '0') * WEIGHTS[i];
        }
        return (11 - sum % 11) % 10;
    }

    private static boolean allAsciiDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return value;
    }
}
