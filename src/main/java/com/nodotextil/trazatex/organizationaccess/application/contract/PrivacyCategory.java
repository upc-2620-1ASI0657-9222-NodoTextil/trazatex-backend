package com.nodotextil.trazatex.organizationaccess.application.contract;

/**
 * How the B2B privacy of a data field is decided (RF-015 to RF-018). The category of each
 * {@link PrivacyField} is fixed in code.
 */
public enum PrivacyCategory {

	/** Always visible to the other companies of the chain (RF-016). Cannot be changed. */
	ALWAYS_SHARED,

	/** Each company decides; private until it chooses to share (RF-015, RF-017). */
	CONFIGURABLE,

	/** Never visible to other companies (RF-018). Cannot be changed. */
	ALWAYS_PRIVATE
}
