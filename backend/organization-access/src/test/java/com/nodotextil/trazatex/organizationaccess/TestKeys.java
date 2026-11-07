package com.nodotextil.trazatex.organizationaccess;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Pares RSA para pruebas, en el formato de las variables de entorno: el PEM completo en Base64, en una línea. */
public final class TestKeys {

    public static final String KEY_ID = "test-key-20261019";

    private static final KeyPair DEFAULT_PAIR = generate(2048);

    private TestKeys() {
    }

    public static KeyPair generate(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String defaultPrivateKey() {
        return privateKeyEnv(DEFAULT_PAIR);
    }

    public static String defaultPublicKey() {
        return publicKeyEnv(DEFAULT_PAIR);
    }

    public static String privateKeyEnv(KeyPair pair) {
        return envValue(pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    }

    public static String publicKeyEnv(KeyPair pair) {
        return envValue(pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    }

    public static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    public static String envValue(String pem) {
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
    }
}
