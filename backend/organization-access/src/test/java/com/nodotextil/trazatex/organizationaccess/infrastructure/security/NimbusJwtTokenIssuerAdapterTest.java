package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.nodotextil.trazatex.organizationaccess.TestKeys;
import com.nodotextil.trazatex.organizationaccess.application.dto.IssuedToken;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NimbusJwtTokenIssuerAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final KeyPair PAIR = TestKeys.generate(2048);
    private final JwtKeyMaterial keys = JwtKeyMaterial.load(TestKeys.privateKeyEnv(PAIR), TestKeys.publicKeyEnv(PAIR), "kid-7");
    private final NimbusJwtTokenIssuerAdapter issuer = new NimbusJwtTokenIssuerAdapter(keys, Duration.ofHours(8));

    private static User user(Role role, UUID companyId) {
        return User.restore(UUID.randomUUID(), companyId, new Email("u@textil.pe"), "Ana", "Pérez", null, "hash", role,
                UserStatus.ACTIVE, NOW);
    }

    @Test
    void signsRs256WithTheKidAndTheAgreedClaims() throws Exception {
        UUID company = UUID.randomUUID();
        User admin = user(Role.COMPANY_ADMIN, company);

        IssuedToken issued = issuer.issue(admin, NOW);

        SignedJWT jwt = SignedJWT.parse(issued.token());
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("kid-7");
        assertThat(jwt.verify(new RSASSAVerifier(keys.publicKey()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(admin.id().toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("COMPANY_ADMIN");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("companyId")).isEqualTo(company.toString());
        assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(8)));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));
    }

    @Test
    void theOwnerTokenCarriesNoCompanyClaim() throws Exception {
        SignedJWT jwt = SignedJWT.parse(issuer.issue(user(Role.LICENSE_OWNER, null), NOW).token());

        assertThat(jwt.getJWTClaimsSet().getClaims()).doesNotContainKey("companyId");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("LICENSE_OWNER");
    }

    @Test
    void aTokenSignedWithAnotherKeyDoesNotVerify() throws Exception {
        KeyPair other = TestKeys.generate(2048);
        SignedJWT jwt = SignedJWT.parse(issuer.issue(user(Role.OPERATOR, UUID.randomUUID()), NOW).token());

        assertThat(jwt.verify(new RSASSAVerifier((java.security.interfaces.RSAPublicKey) other.getPublic()))).isFalse();
    }
}
