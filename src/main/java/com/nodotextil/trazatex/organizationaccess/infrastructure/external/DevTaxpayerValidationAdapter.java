package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.port.TaxpayerValidationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development stand-in: accepts any well-formed RUC without calling SUNAT. */
@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "false",
		matchIfMissing = true)
class DevTaxpayerValidationAdapter implements TaxpayerValidationPort {

	@Override
	public boolean isActiveAndHabido(String ruc) {
		return ruc != null && ruc.matches("\\d{11}");
	}
}
