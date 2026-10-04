package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_access_invitations", indexes = {
		@Index(name = "idx_oa_invitations_company", columnList = "company_id, status"),
		@Index(name = "idx_oa_invitations_status_expires", columnList = "status, expires_at")})
class InvitationJpaEntity {

	@Id
	UUID id;

	@Column(nullable = false)
	String email;

	@Column(name = "company_id", nullable = false)
	UUID companyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	Role role;

	@Column(nullable = false, unique = true)
	String token;

	@Column(name = "first_name")
	String firstName;

	@Column(name = "last_name")
	String lastName;

	@Column(name = "job_title")
	String jobTitle;

	@Column(name = "created_at", nullable = false)
	LocalDateTime createdAt;

	@Column(name = "expires_at", nullable = false)
	LocalDateTime expiresAt;

	@Column(name = "created_by_user_id", nullable = false)
	UUID createdByUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	InvitationStatus status;

	protected InvitationJpaEntity() {
	}

	static InvitationJpaEntity from(Invitation invitation) {
		InvitationJpaEntity entity = new InvitationJpaEntity();
		entity.id = invitation.id();
		entity.email = invitation.email();
		entity.companyId = invitation.companyId();
		entity.role = invitation.role();
		entity.token = invitation.token();
		entity.firstName = invitation.firstName();
		entity.lastName = invitation.lastName();
		entity.jobTitle = invitation.jobTitle();
		entity.createdAt = invitation.createdAt();
		entity.expiresAt = invitation.expiresAt();
		entity.createdByUserId = invitation.createdByUserId();
		entity.status = invitation.status();
		return entity;
	}

	Invitation toDomain() {
		return new Invitation(id, email, companyId, role, token, firstName, lastName, jobTitle,
				createdAt, expiresAt, createdByUserId, status);
	}
}
