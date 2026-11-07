package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.application.port.out.TokenIssuerPort;
import com.nodotextil.trazatex.organizationaccess.infrastructure.security.JwtKeyMaterial;
import com.nodotextil.trazatex.organizationaccess.infrastructure.security.NimbusJwtTokenIssuerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Claves JWT del Identity Provider (solo por variables de entorno, nunca en archivos del proyecto). */
@Configuration
class SecurityConfig {

    @Bean
    JwtKeyMaterial jwtKeyMaterial(@Value("${trazatex.jwt.private-key:}") String privateKey,
                                  @Value("${trazatex.jwt.public-key:}") String publicKey,
                                  @Value("${trazatex.jwt.key-id:}") String keyId) {
        return JwtKeyMaterial.load(privateKey, publicKey, keyId);
    }

    @Bean
    TokenIssuerPort tokenIssuer(JwtKeyMaterial keys, @Value("${trazatex.jwt.ttl:PT8H}") Duration ttl) {
        return new NimbusJwtTokenIssuerAdapter(keys, ttl);
    }
}
