package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
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
@Table(name = "organization_access_users",
		indexes = @Index(name = "idx_oa_users_company", columnList = "company_id"))
class OrganizationUserJpaEntity {

	@Id
	UUID id;

	@Column(nullable = false, unique = true)
	String email;

	@Column(name = "first_name", nullable = false)
	String firstName;

	@Column(name = "last_name", nullable = false)
	String lastName;

	@Column(name = "job_title")
	String jobTitle;

	@Column(name = "password_hash", nullable = false)
	String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	Role role;

	@Column(name = "company_id")
	UUID companyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	UserStatus status;

	@Column(name = "failed_login_attempts", nullable = false)
	int failedLoginAttempts;

	@Column(name = "locked_until")
	LocalDateTime lockedUntil;

	@Column(name = "created_at", nullable = false)
	LocalDateTime createdAt;

	protected OrganizationUserJpaEntity() {
	}

	static OrganizationUserJpaEntity from(OrganizationUser user) {
		OrganizationUserJpaEntity entity = new OrganizationUserJpaEntity();
		entity.id = user.id();
		entity.email = user.email();
		entity.firstName = user.firstName();
		entity.lastName = user.lastName();
		entity.jobTitle = user.jobTitle();
		entity.passwordHash = user.passwordHash();
		entity.role = user.role();
		entity.companyId = user.companyId();
		entity.status = user.status();
		entity.failedLoginAttempts = user.failedLoginAttempts();
		entity.lockedUntil = user.lockedUntil();
		entity.createdAt = user.createdAt();
		return entity;
	}

	OrganizationUser toDomain() {
		return new OrganizationUser(id, email, firstName, lastName, jobTitle, passwordHash, role,
				companyId, status, failedLoginAttempts, lockedUntil, createdAt);
	}
}
