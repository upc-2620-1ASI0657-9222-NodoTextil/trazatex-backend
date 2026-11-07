package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nodotextil.trazatex.organizationaccess.TestKeys;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyMaterialTest {

    private static final KeyPair PAIR = TestKeys.generate(2048);
    private static final String PRIVATE = TestKeys.privateKeyEnv(PAIR);
    private static final String PUBLIC = TestKeys.publicKeyEnv(PAIR);

    @Test
    void loadsAValidPair() {
        JwtKeyMaterial keys = JwtKeyMaterial.load(PRIVATE, PUBLIC, " kid-1 ");

        assertThat(keys.keyId()).isEqualTo("kid-1");
        assertThat(keys.privateKey().getModulus()).isEqualTo(((java.security.interfaces.RSAPublicKey) PAIR.getPublic()).getModulus());
        assertThat(keys.publicKey().getPublicExponent()).isEqualTo(((java.security.interfaces.RSAPublicKey) PAIR.getPublic()).getPublicExponent());
    }

    @Test
    void publicJwkNeverExposesPrivateComponents() {
        Map<String, Object> jwk = JwtKeyMaterial.load(PRIVATE, PUBLIC, "kid-1").publicJwk().toJSONObject();

        assertThat(jwk).containsEntry("kty", "RSA").containsEntry("kid", "kid-1").containsEntry("use", "sig")
                .containsEntry("alg", "RS256").containsKeys("n", "e")
                .doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }

    @Test
    void eachMissingVariableIsNamedInTheError() {
        assertThatThrownBy(() -> JwtKeyMaterial.load(null, PUBLIC, "kid")).hasMessageContaining("JWT_PRIVATE_KEY");
        assertThatThrownBy(() -> JwtKeyMaterial.load(PRIVATE, " ", "kid")).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> JwtKeyMaterial.load(PRIVATE, PUBLIC, "")).hasMessageContaining("JWT_KEY_ID");
        assertThatThrownBy(() -> JwtKeyMaterial.load("", "", "")).hasMessageContaining("generate-jwt-keys.sh");
    }

    @Test
    void theKeyIdMustBeSafeToPutInAHeader() {
        assertThatThrownBy(() -> JwtKeyMaterial.load(PRIVATE, PUBLIC, "con espacio")).hasMessageContaining("JWT_KEY_ID");
        assertThatThrownBy(() -> JwtKeyMaterial.load(PRIVATE, PUBLIC, "x".repeat(65))).hasMessageContaining("JWT_KEY_ID");
    }

    @Test
    void rejectsValuesThatAreNotBase64Pem() {
        assertThatThrownBy(() -> JwtKeyMaterial.load("esto no es base64 !!!", PUBLIC, "kid")).hasMessageContaining("JWT_PRIVATE_KEY").hasMessageContaining("Base64");
        assertThatThrownBy(() -> JwtKeyMaterial.load(TestKeys.envValue("hola"), PUBLIC, "kid")).hasMessageContaining("JWT_PRIVATE_KEY").hasMessageContaining("PEM");
        assertThatThrownBy(() -> JwtKeyMaterial.load(PRIVATE, TestKeys.envValue("-----BEGIN PUBLIC KEY-----\n@@@@\n-----END PUBLIC KEY-----"), "kid"))
                .hasMessageContaining("JWT_PUBLIC_KEY");
    }

    @Test
    void rejectsPkcs1PrivateKeysWithAHint() {
        String pkcs1 = TestKeys.envValue(TestKeys.pem("RSA PRIVATE KEY", PAIR.getPrivate().getEncoded()));
        assertThatThrownBy(() -> JwtKeyMaterial.load(pkcs1, PUBLIC, "kid")).hasMessageContaining("PKCS#1").hasMessageContaining("PKCS#8");
    }

    @Test
    void rejectsAPublicKeyThatDoesNotMatchThePrivateOne() {
        String other = TestKeys.publicKeyEnv(TestKeys.generate(2048));
        assertThatThrownBy(() -> JwtKeyMaterial.load(PRIVATE, other, "kid")).hasMessageContaining("no corresponde");
    }

    @Test
    void rejectsKeysShorterThan2048Bits() {
        KeyPair weak = TestKeys.generate(1024);
        assertThatThrownBy(() -> JwtKeyMaterial.load(TestKeys.privateKeyEnv(weak), TestKeys.publicKeyEnv(weak), "kid"))
                .hasMessageContaining("2048");
    }

    @Test
    void errorsAndToStringNeverContainKeyMaterial() {
        String other = TestKeys.publicKeyEnv(TestKeys.generate(2048));
        assertThatThrownBy(() -> JwtKeyMaterial.load(PRIVATE, other, "kid"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(PRIVATE).doesNotContain(other));
        assertThat(JwtKeyMaterial.load(PRIVATE, PUBLIC, "kid").toString()).doesNotContain(PRIVATE).doesNotContain(PUBLIC);
    }
}
