package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.port.CompromisedPasswordPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development stand-in: no password is considered compromised and nothing leaves the process. */
@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "false",
		matchIfMissing = true)
class DevCompromisedPasswordAdapter implements CompromisedPasswordPort {

	@Override
	public boolean isCompromised(String password) {
		return false;
	}
}
