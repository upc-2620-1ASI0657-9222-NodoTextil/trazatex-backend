package com.nodotextil.trazatex.organizationaccess.application.port;

/** Validates a company against the national taxpayer registry (SUNAT). */
public interface TaxpayerValidationPort {

	/**
	 * Whether the RUC belongs to a taxpayer that is active and "habido".
	 *
	 * @throws com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException
	 *         if the registry cannot be queried
	 */
	boolean isActiveAndHabido(String ruc);
}
