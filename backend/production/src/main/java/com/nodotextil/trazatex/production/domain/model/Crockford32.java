package com.nodotextil.trazatex.production.domain.model;

import java.util.Locale;
import java.util.random.RandomGenerator;

final class Crockford32 {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private Crockford32() {
    }

    static String generate(int length, RandomGenerator random) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    static boolean isValid(String code, int length) {
        if (code == null || code.length() != length) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    static String normalize(String code) {
        String upper = code.trim().toUpperCase(Locale.ROOT);
        StringBuilder canonical = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            canonical.append(switch (c) {
                case 'O' -> '0';
                case 'I', 'L' -> '1';
                default -> c;
            });
        }
        return canonical.toString();
    }
}
