package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "organization_access", name = "organization_access_admin_requests")
class AdminRequestJpaEntity {

	@Id
	UUID id;

	@Column(name = "company_id", nullable = false)
	UUID companyId;

	@Column(nullable = false)
	String email;

	@Column(name = "first_name")
	String firstName;

	@Column(name = "last_name")
	String lastName;

	@Column(name = "job_title")
	String jobTitle;

	@Column(name = "requested_by_user_id", nullable = false)
	UUID requestedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	AdminRequestStatus status;

	@Column(name = "created_at", nullable = false)
	LocalDateTime createdAt;

	@Column(name = "decided_by_user_id")
	UUID decidedByUserId;

	@Column(name = "decided_at")
	LocalDateTime decidedAt;

	protected AdminRequestJpaEntity() {
	}

	static AdminRequestJpaEntity from(AdminRequest request) {
		AdminRequestJpaEntity entity = new AdminRequestJpaEntity();
		entity.id = request.id();
		entity.companyId = request.companyId();
		entity.email = request.email();
		entity.firstName = request.firstName();
		entity.lastName = request.lastName();
		entity.jobTitle = request.jobTitle();
		entity.requestedByUserId = request.requestedByUserId();
		entity.status = request.status();
		entity.createdAt = request.createdAt();
		entity.decidedByUserId = request.decidedByUserId();
		entity.decidedAt = request.decidedAt();
		return entity;
	}

	AdminRequest toDomain() {
		return new AdminRequest(id, companyId, email, firstName, lastName, jobTitle,
				requestedByUserId, status, createdAt, decidedByUserId, decidedAt);
	}
}
