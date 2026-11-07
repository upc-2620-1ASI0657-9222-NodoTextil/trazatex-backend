package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nodotextil.trazatex.organizationaccess.infrastructure.security.JwtKeyMaterial;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Claves públicas para que el Gateway valide los JWT (ADR-005). Público, sin JWT. */
@RestController
class JwksController {

    private final JwtKeyMaterial keys;

    JwksController(JwtKeyMaterial keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new JWKSet(keys.publicJwk()).toJSONObject(true));
    }
}
