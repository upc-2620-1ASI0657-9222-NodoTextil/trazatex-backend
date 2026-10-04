package com.nodotextil.trazatex.organizationaccess.application.contract;

import java.util.Set;
import java.util.UUID;

/**
 * Contract published to the other modules to ask how a company shares its data (RF-015 to
 * RF-018).
 *
 * <p>The protection is an <b>authorization policy evaluated at query time</b>, per node (which
 * company owns the batch) and per attribute (the {@link PrivacyCategory} of the field). It is not
 * an Anti-Corruption Layer: nothing is translated or copied. This module only stores the
 * configuration and exposes it here; Traceability applies the filter when it builds the
 * genealogy, hiding the fields that are {@code PRIVATE} for the company that owns each node.
 *
 * <p>Rules: an {@code ALWAYS_SHARED} field is {@code SHARED}, an {@code ALWAYS_PRIVATE} field is
 * {@code PRIVATE}, and a {@code CONFIGURABLE} field is what its company stored or, if it never
 * chose, {@code PRIVATE}.
 */
public interface CompanyPrivacyAccess {

	/** The effective visibility of {@code field} for the data of {@code companyId}. */
	PrivacyVisibility visibilityOf(UUID companyId, PrivacyField field);

	/** Every field that is effectively {@code SHARED} for the data of {@code companyId}. */
	Set<PrivacyField> sharedFields(UUID companyId);
}
