package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nimbusds.jose.jwk.RSAKey;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Par RSA con el que organization-access firma los JWT (Identity Provider, ADR-005). Se carga de las variables de entorno
 * {@code JWT_PRIVATE_KEY} y {@code JWT_PUBLIC_KEY} (el PEM completo codificado en Base64, en una sola línea: privada PKCS#8,
 * pública X.509) y {@code JWT_KEY_ID} (el {@code kid}). Si algo falta o no es válido el servicio no arranca, con un mensaje
 * que nombra la variable; los mensajes nunca incluyen el contenido de las claves.
 */
public final class JwtKeyMaterial {

    public static final int MIN_KEY_BITS = 2048;
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    private JwtKeyMaterial(RSAPrivateCrtKey privateKey, RSAPublicKey publicKey, String keyId) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keyId = keyId;
    }

    public static JwtKeyMaterial load(String privateKeyBase64, String publicKeyBase64, String keyId) {
        require("JWT_PRIVATE_KEY", privateKeyBase64);
        require("JWT_PUBLIC_KEY", publicKeyBase64);
        require("JWT_KEY_ID", keyId);
        if (!KEY_ID.matcher(keyId.trim()).matches()) {
            throw new IllegalStateException("JWT_KEY_ID solo admite letras, dígitos, '.', '_' y '-' (máx. 64 caracteres).");
        }
        RSAPrivateCrtKey privateKey;
        RSAPublicKey publicKey;
        try {
            byte[] privateDer = pemBody("JWT_PRIVATE_KEY", privateKeyBase64, "PRIVATE KEY", "RSA PRIVATE KEY");
            privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateDer));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("JWT_PRIVATE_KEY no es una clave privada RSA PKCS#8 válida.");
        }
        try {
            byte[] publicDer = pemBody("JWT_PUBLIC_KEY", publicKeyBase64, "PUBLIC KEY", null);
            publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicDer));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("JWT_PUBLIC_KEY no es una clave pública RSA X.509 válida.");
        }
        if (privateKey.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalStateException("La clave RSA debe tener al menos " + MIN_KEY_BITS + " bits.");
        }
        if (!privateKey.getModulus().equals(publicKey.getModulus())
                || !privateKey.getPublicExponent().equals(publicKey.getPublicExponent())) {
            throw new IllegalStateException("JWT_PUBLIC_KEY no corresponde a JWT_PRIVATE_KEY.");
        }
        return new JwtKeyMaterial(privateKey, publicKey, keyId.trim());
    }

    private static void require(String variable, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + variable
                    + ". Genera el par de claves con infra/scripts/generate-jwt-keys.sh.");
        }
    }

    /** Base64 externo → PEM → cuerpo Base64 del PEM → DER. */
    private static byte[] pemBody(String variable, String base64Pem, String expectedLabel, String unsupportedLabel) {
        String pem;
        try {
            pem = new String(Base64.getDecoder().decode(base64Pem.replaceAll("\\s", "")), StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(variable + " debe ser el PEM completo codificado en Base64 (en una sola línea).");
        }
        if (unsupportedLabel != null && pem.contains("-----BEGIN " + unsupportedLabel + "-----")) {
            throw new IllegalStateException(variable + " está en formato PKCS#1; conviértela a PKCS#8 (openssl pkcs8 -topk8 -nocrypt).");
        }
        String begin = "-----BEGIN " + expectedLabel + "-----";
        String end = "-----END " + expectedLabel + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < start) {
            throw new IllegalStateException(variable + " no contiene un PEM '" + expectedLabel + "'.");
        }
        String body = pem.substring(start + begin.length(), stop).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(variable + " contiene un PEM dañado.");
        }
    }

    public RSAPrivateCrtKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    /** JWK público para {@code /.well-known/jwks.json}: sin ningún componente privado. */
    public RSAKey publicJwk() {
        return new RSAKey.Builder(publicKey)
                .keyID(keyId)
                .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
    }

    @Override
    public String toString() {
        return "JwtKeyMaterial[keyId=" + keyId + "]";
    }
}
