package com.nodotextil.trazatex.organizationaccess.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;


public final class OrganizationUser {

	
	public static final int MAX_FAILED_LOGIN_ATTEMPTS = 3;
	public static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

	private final UUID id;
	private final String email;
	private final String firstName;
	private final String lastName;
	private final String jobTitle;
	private final String passwordHash;
	private final Role role;
	private final UUID companyId;
	private final UserStatus status;
	private final int failedLoginAttempts;
	private final LocalDateTime lockedUntil;
	private final LocalDateTime createdAt;

	public OrganizationUser(UUID id, String email, String firstName, String lastName,
			String jobTitle, String passwordHash, Role role, UUID companyId, UserStatus status,
			int failedLoginAttempts, LocalDateTime lockedUntil, LocalDateTime createdAt) {
		this.role = DomainText.notNull(role, "role");
		if (role.belongsToCompany() && companyId == null) {
			throw new OrganizationValidationException("A " + role + " must belong to a company");
		}
		if (!role.belongsToCompany() && companyId != null) {
			throw new OrganizationValidationException("A " + role + " cannot belong to a company");
		}
		if (failedLoginAttempts < 0) {
			throw new OrganizationValidationException("failedLoginAttempts cannot be negative");
		}
		this.id = DomainText.notNull(id, "id");
		this.email = DomainText.email(email);
		this.firstName = DomainText.required(firstName, "firstName");
		this.lastName = DomainText.required(lastName, "lastName");
		this.jobTitle = DomainText.optional(jobTitle);
		this.passwordHash = DomainText.required(passwordHash, "passwordHash");
		this.companyId = companyId;
		this.status = DomainText.notNull(status, "status");
		this.failedLoginAttempts = failedLoginAttempts;
		this.lockedUntil = lockedUntil;
		this.createdAt = DomainText.notNull(createdAt, "createdAt");
	}

	
	public static OrganizationUser create(String email, String firstName, String lastName,
			String jobTitle, String passwordHash, Role role, UUID companyId, LocalDateTime now) {
		return new OrganizationUser(UUID.randomUUID(), email, firstName, lastName, jobTitle,
				passwordHash, role, companyId, UserStatus.ACTIVE, 0, null, now);
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	
	public OrganizationUser withStatus(UserStatus newStatus) {
		if (newStatus == null || newStatus == UserStatus.PENDING) {
			throw new OrganizationValidationException("A user can only be ACTIVE or INACTIVE",
					java.util.Map.of("status", "Must be ACTIVE or INACTIVE"));
		}
		return new OrganizationUser(id, email, firstName, lastName, jobTitle, passwordHash, role,
				companyId, newStatus, failedLoginAttempts, lockedUntil, createdAt);
	}

	public boolean isLockedAt(LocalDateTime now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	


	public OrganizationUser withFailedLogin(LocalDateTime now) {
		boolean previousLockExpired = lockedUntil != null && !lockedUntil.isAfter(now);
		int attempts = (previousLockExpired ? 0 : failedLoginAttempts) + 1;
		LocalDateTime lock = attempts >= MAX_FAILED_LOGIN_ATTEMPTS
				? now.plus(LOGIN_LOCK_DURATION) : null;
		return new OrganizationUser(id, email, firstName, lastName, jobTitle, passwordHash, role,
				companyId, status, attempts, lock, createdAt);
	}

	
	public OrganizationUser withSuccessfulLogin() {
		return new OrganizationUser(id, email, firstName, lastName, jobTitle, passwordHash, role,
				companyId, status, 0, null, createdAt);
	}

	public UUID id() {
		return id;
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

	public String passwordHash() {
		return passwordHash;
	}

	public Role role() {
		return role;
	}

	
	public UUID companyId() {
		return companyId;
	}

	public UserStatus status() {
		return status;
	}

	public int failedLoginAttempts() {
		return failedLoginAttempts;
	}

	public LocalDateTime lockedUntil() {
		return lockedUntil;
	}

	public LocalDateTime createdAt() {
		return createdAt;
	}
}
