package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.application.port.PrivacyAuditPort;
import java.util.ArrayList;
import java.util.List;

/** Keeps the audited privacy changes so tests can inspect them. */
public class RecordingPrivacyAudit implements PrivacyAuditPort {

	public final List<PrivacyChange> changes = new ArrayList<>();

	@Override
	public void record(PrivacyChange change) {
		changes.add(change);
	}
}
