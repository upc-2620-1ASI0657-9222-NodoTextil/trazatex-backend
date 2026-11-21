package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.application.dto.IssuedToken;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;

import java.time.Instant;

public interface TokenIssuerPort {

    IssuedToken issue(User user, Instant now);
}
