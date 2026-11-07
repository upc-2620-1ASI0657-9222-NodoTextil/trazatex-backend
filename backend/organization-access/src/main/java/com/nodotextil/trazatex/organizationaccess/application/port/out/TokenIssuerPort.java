package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.application.dto.IssuedToken;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;

import java.time.Instant;

/** Emite el JWT RS256 (ADR-005) con {@code sub} (userId), {@code role} y {@code companyId}. */
public interface TokenIssuerPort {

    IssuedToken issue(User user, Instant now);
}
