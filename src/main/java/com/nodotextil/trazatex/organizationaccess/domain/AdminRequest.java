package com.nodotextil.trazatex.organizationaccess.domain;

import java.time.LocalDateTime;
import java.util.UUID;


public final class AdminRequest {

	private final UUID id;
	private final UUID companyId;
	private final String email;
	private final String firstName;
	private final String lastName;
	private final String jobTitle;
	private final UUID requestedByUserId;
	private final AdminRequestStatus status;
	private final LocalDateTime createdAt;
	private final UUID decidedByUserId;
	private final LocalDateTime decidedAt;

	public AdminRequest(UUID id, UUID companyId, String email, String firstName, String lastName,
			String jobTitle, UUID requestedByUserId, AdminRequestStatus status,
			LocalDateTime createdAt, UUID decidedByUserId, LocalDateTime decidedAt) {
		this.id = DomainText.notNull(id, "id");
		this.companyId = DomainText.notNull(companyId, "companyId");
		this.email = DomainText.email(email);
		this.firstName = DomainText.optional(firstName);
		this.lastName = DomainText.optional(lastName);
		this.jobTitle = DomainText.optional(jobTitle);
		this.requestedByUserId = DomainText.notNull(requestedByUserId, "requestedByUserId");
		this.status = DomainText.notNull(status, "status");
		this.createdAt = DomainText.notNull(createdAt, "createdAt");
		this.decidedByUserId = decidedByUserId;
		this.decidedAt = decidedAt;
	}

	
	public static AdminRequest create(UUID companyId, String email, String firstName,
			String lastName, String jobTitle, UUID requestedByUserId, LocalDateTime now) {
		return new AdminRequest(UUID.randomUUID(), companyId, email, firstName, lastName,
				jobTitle, requestedByUserId, AdminRequestStatus.PENDING, now, null, null);
	}

	public boolean isPending() {
		return status == AdminRequestStatus.PENDING;
	}

	public AdminRequest approve(UUID decidedByUserId, LocalDateTime now) {
		return decide(AdminRequestStatus.APPROVED, decidedByUserId, now);
	}

	public AdminRequest reject(UUID decidedByUserId, LocalDateTime now) {
		return decide(AdminRequestStatus.REJECTED, decidedByUserId, now);
	}

	
	private AdminRequest decide(AdminRequestStatus decision, UUID decidedBy, LocalDateTime now) {
		if (!isPending()) {
			throw new OrganizationConflictException(
					"The request was already " + status + " and cannot be decided again");
		}
		return new AdminRequest(id, companyId, email, firstName, lastName, jobTitle,
				requestedByUserId, decision, createdAt, DomainText.notNull(decidedBy,
						"decidedByUserId"), now);
	}

	public UUID id() {
		return id;
	}

	public UUID companyId() {
		return companyId;
	}

	public String email() {
		return email;
	}

	public String firstName() {
		return firstName;
	}

	public String lastName() {
		return lastName;
	}

	public String jobTitle() {
		return jobTitle;
	}

	public UUID requestedByUserId() {
		return requestedByUserId;
	}

	public AdminRequestStatus status() {
		return status;
	}

	public LocalDateTime createdAt() {
		return createdAt;
	}

	public UUID decidedByUserId() {
		return decidedByUserId;
	}

	public LocalDateTime decidedAt() {
		return decidedAt;
	}
}
