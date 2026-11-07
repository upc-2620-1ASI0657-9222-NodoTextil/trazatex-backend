package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nodotextil.trazatex.organizationaccess.application.dto.IssuedToken;
import com.nodotextil.trazatex.organizationaccess.application.port.out.TokenIssuerPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT RS256 (ADR-005): {@code sub} = userId, {@code role}, {@code companyId} (ausente para el Dueño), {@code iat} y
 * {@code exp}; el {@code kid} del encabezado apunta a la clave publicada en el JWKS. Dura {@code ttl} (8 h, sin refresh token).
 */
public class NimbusJwtTokenIssuerAdapter implements TokenIssuerPort {

    private final JwtKeyMaterial keys;
    private final Duration ttl;
    private final RSASSASigner signer;

    public NimbusJwtTokenIssuerAdapter(JwtKeyMaterial keys, Duration ttl) {
        this.keys = keys;
        this.ttl = ttl;
        this.signer = new RSASSASigner(keys.privateKey());
    }

    @Override
    public IssuedToken issue(User user, Instant now) {
        Instant expiresAt = now.plus(ttl);
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(user.id().toString())
                .claim("role", user.role().name())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt));
        if (user.companyId() != null) {
            claims.claim("companyId", user.companyId().toString());
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keys.keyId()).type(JOSEObjectType.JWT).build(),
                claims.build());
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el JWT", e);
        }
        return new IssuedToken(jwt.serialize(), expiresAt);
    }
}
