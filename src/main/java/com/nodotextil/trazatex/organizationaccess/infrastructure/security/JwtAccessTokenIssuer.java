package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nodotextil.trazatex.organizationaccess.application.port.AccessTokenIssuer;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    static final long TOKEN_LIFETIME_SECONDS = 3600;

    private final JwtEncoder encoder;
    private final String issuer;
    private final Clock clock;

    @Autowired
    JwtAccessTokenIssuer(
            JwtEncoder encoder,
            @Value("${app.security.jwt-issuer:trazatex}") String issuer) {
        this(encoder, issuer, Clock.systemUTC());
    }

    JwtAccessTokenIssuer(JwtEncoder encoder, String issuer, Clock clock) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(OrganizationUser user) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(TOKEN_LIFETIME_SECONDS))
                .claim("userId", user.id().toString())
                .claim("role", user.role().name());
        if (user.companyId() != null) {
            claims.claim("companyId", user.companyId().toString());
        }
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims.build())).getTokenValue();
        return new IssuedAccessToken(token, TOKEN_LIFETIME_SECONDS);
    }
}
