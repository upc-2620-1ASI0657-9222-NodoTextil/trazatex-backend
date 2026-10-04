package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.nodotextil.trazatex.organizationaccess.application.GetPrivacySettingsUseCase.PrivacySettingView;
import com.nodotextil.trazatex.organizationaccess.application.UpdatePrivacySettingsUseCase.Requested;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyCategory;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.application.port.PrivacyAuditPort.PrivacyChange;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryPrivacySettingRepository;
import com.nodotextil.trazatex.organizationaccess.support.RecordingPrivacyAudit;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PrivacySettingsUseCasesTest {

	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final InMemoryPrivacySettingRepository settings = new InMemoryPrivacySettingRepository();
	private final RecordingPrivacyAudit audit = new RecordingPrivacyAudit();
	private final GetPrivacySettingsUseCase get = new GetPrivacySettingsUseCase(users, settings);
	private final UpdatePrivacySettingsUseCase update = new UpdatePrivacySettingsUseCase(users,
			settings, audit, FixedClocks.at(NOW));

	private final UUID companyA = UUID.randomUUID();
	private final UUID companyB = UUID.randomUUID();
	private final OrganizationUser adminA = users
			.save(TestData.user("admin-a@example.com", Role.COMPANY_ADMIN, companyA));
	private final OrganizationUser adminB = users
			.save(TestData.user("admin-b@example.com", Role.COMPANY_ADMIN, companyB));

	private static Requested share(PrivacyField field) {
		return new Requested(field, PrivacyVisibility.SHARED);
	}

	private static Requested hide(PrivacyField field) {
		return new Requested(field, PrivacyVisibility.PRIVATE);
	}

	private static PrivacyVisibility visibility(List<PrivacySettingView> view, PrivacyField field) {
		return view.stream().filter(setting -> setting.field() == field).findFirst().orElseThrow()
				.visibility();
	}

	// ---- reading

	@Test
	void showsEveryFieldWithItsCategoryAndEffectiveVisibility() {
		List<PrivacySettingView> view = get.execute(adminA.id());

		assertThat(view).extracting(PrivacySettingView::field)
				.containsExactly(PrivacyField.values());
		for (PrivacySettingView setting : view) {
			assertThat(setting.category()).isEqualTo(setting.field().category());
			PrivacyVisibility expected = switch (setting.category()) {
				case ALWAYS_SHARED -> PrivacyVisibility.SHARED;
				case ALWAYS_PRIVATE, CONFIGURABLE -> PrivacyVisibility.PRIVATE;
			};
			assertThat(setting.visibility()).isEqualTo(expected);
		}
	}

	@Test
	void configurableFieldsAreDefaultedToPrivateWhenNothingWasChosen() {
		assertThat(settings.size()).isZero();

		assertThat(get.execute(adminA.id())).filteredOn(
				setting -> setting.category() == PrivacyCategory.CONFIGURABLE)
				.hasSize(11).allSatisfy(setting -> assertThat(setting.visibility())
						.isEqualTo(PrivacyVisibility.PRIVATE));
	}

	// ---- changing

	@Test
	void anAdministratorSharesConfigurableFieldsAndGetsTheFullConfigurationBack() {
		List<PrivacySettingView> result = update.execute(adminA.id(),
				List.of(share(PrivacyField.QUANTITY), share(PrivacyField.SUPPLIER)));

		assertThat(result).hasSize(PrivacyField.values().length);
		assertThat(visibility(result, PrivacyField.QUANTITY)).isEqualTo(PrivacyVisibility.SHARED);
		assertThat(visibility(result, PrivacyField.SUPPLIER)).isEqualTo(PrivacyVisibility.SHARED);
		assertThat(visibility(result, PrivacyField.WASTE)).isEqualTo(PrivacyVisibility.PRIVATE);
		assertThat(visibility(get.execute(adminA.id()), PrivacyField.QUANTITY))
				.isEqualTo(PrivacyVisibility.SHARED);
	}

	@Test
	void aFieldCanBeMadePrivateAgain() {
		update.execute(adminA.id(), List.of(share(PrivacyField.QUANTITY)));

		List<PrivacySettingView> result = update.execute(adminA.id(),
				List.of(hide(PrivacyField.QUANTITY)));

		assertThat(visibility(result, PrivacyField.QUANTITY)).isEqualTo(PrivacyVisibility.PRIVATE);
	}

	@Test
	void changingAnAlwaysSharedOrAlwaysPrivateFieldIsRejected() {
		for (PrivacyField field : PrivacyField.values()) {
			if (field.isConfigurable()) {
				continue;
			}
			for (PrivacyVisibility requested : PrivacyVisibility.values()) {
				OrganizationValidationException error = catchThrowableOfType(
						OrganizationValidationException.class, () -> update.execute(adminA.id(),
								List.of(new Requested(field, requested))));
				assertThat(error).as("%s -> %s", field, requested).isNotNull();
				assertThat(error.fieldErrors()).containsKey(field.name());
			}
		}
		assertThat(settings.size()).isZero();
		assertThat(audit.changes).isEmpty();
	}

	@Test
	void oneInvalidFieldRejectsTheWholeChangeWithoutPartialUpdates() {
		OrganizationValidationException error = catchThrowableOfType(
				OrganizationValidationException.class, () -> update.execute(adminA.id(),
						List.of(share(PrivacyField.QUANTITY), share(PrivacyField.QUALITY_STATUS),
								share(PrivacyField.SUPPLIER),
								share(PrivacyField.OPERATOR_IDENTITY))));

		assertThat(error.fieldErrors()).containsOnlyKeys("QUALITY_STATUS", "OPERATOR_IDENTITY");
		assertThat(settings.size()).isZero();
		assertThat(settings.saveAllCalls).isZero();
		assertThat(audit.changes).isEmpty();
		assertThat(visibility(get.execute(adminA.id()), PrivacyField.QUANTITY))
				.isEqualTo(PrivacyVisibility.PRIVATE);
	}

	@Test
	void anEmptyOrRepeatedOrIncompleteRequestIsRejected() {
		assertThatThrownBy(() -> update.execute(adminA.id(), List.of()))
				.isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> update.execute(adminA.id(), null))
				.isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> update.execute(adminA.id(),
				List.of(share(PrivacyField.QUANTITY), hide(PrivacyField.QUANTITY))))
				.isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> update.execute(adminA.id(),
				List.of(new Requested(PrivacyField.QUANTITY, null))))
				.isInstanceOf(OrganizationValidationException.class);
		assertThat(settings.size()).isZero();
	}

	@Test
	void eachAdministratorOnlyAffectsTheirOwnCompany() {
		update.execute(adminA.id(), List.of(share(PrivacyField.QUANTITY)));
		update.execute(adminB.id(), List.of(share(PrivacyField.MACHINERY)));

		List<PrivacySettingView> companyAView = get.execute(adminA.id());
		List<PrivacySettingView> companyBView = get.execute(adminB.id());
		assertThat(visibility(companyAView, PrivacyField.QUANTITY))
				.isEqualTo(PrivacyVisibility.SHARED);
		assertThat(visibility(companyAView, PrivacyField.MACHINERY))
				.isEqualTo(PrivacyVisibility.PRIVATE);
		assertThat(visibility(companyBView, PrivacyField.QUANTITY))
				.isEqualTo(PrivacyVisibility.PRIVATE);
		assertThat(visibility(companyBView, PrivacyField.MACHINERY))
				.isEqualTo(PrivacyVisibility.SHARED);
		assertThat(settings.findByCompanyId(companyA)).hasSize(1);
		assertThat(settings.findByCompanyId(companyB)).hasSize(1);
	}

	// ---- who can

	@Test
	void operatorsLicenseOwnersInactiveAdministratorsAndStrangersCannotReadOrChange() {
		OrganizationUser operator = users.save(TestData.user("op@example.com", Role.OPERATOR,
				companyA));
		OrganizationUser owner = users.save(TestData.user("owner@example.com",
				Role.LICENSE_OWNER, null));
		OrganizationUser inactiveAdmin = users.save(new OrganizationUser(UUID.randomUUID(),
				"old@example.com", "Old", "Admin", null, "hash", Role.COMPANY_ADMIN, companyA,
				UserStatus.INACTIVE, 0, null, NOW));

		for (UUID actor : new UUID[] { operator.id(), owner.id(), inactiveAdmin.id(),
				UUID.randomUUID() }) {
			assertThatThrownBy(() -> get.execute(actor))
					.isInstanceOf(OrganizationAccessDeniedException.class);
			assertThatThrownBy(() -> update.execute(actor, List.of(share(PrivacyField.QUANTITY))))
					.isInstanceOf(OrganizationAccessDeniedException.class);
		}
		assertThat(settings.size()).isZero();
		assertThat(audit.changes).isEmpty();
	}

	// ---- audit

	@Test
	void everyRealChangeIsAudited() {
		update.execute(adminA.id(), List.of(share(PrivacyField.QUANTITY),
				share(PrivacyField.SUPPLIER)));
		update.execute(adminA.id(), List.of(hide(PrivacyField.QUANTITY)));

		assertThat(audit.changes).extracting(PrivacyChange::field, PrivacyChange::previous,
				PrivacyChange::current).containsExactly(
						org.assertj.core.groups.Tuple.tuple(PrivacyField.QUANTITY,
								PrivacyVisibility.PRIVATE, PrivacyVisibility.SHARED),
						org.assertj.core.groups.Tuple.tuple(PrivacyField.SUPPLIER,
								PrivacyVisibility.PRIVATE, PrivacyVisibility.SHARED),
						org.assertj.core.groups.Tuple.tuple(PrivacyField.QUANTITY,
								PrivacyVisibility.SHARED, PrivacyVisibility.PRIVATE));
		assertThat(audit.changes).allSatisfy(change -> {
			assertThat(change.companyId()).isEqualTo(companyA);
			assertThat(change.changedByUserId()).isEqualTo(adminA.id());
			assertThat(change.occurredAt()).isEqualTo(NOW);
		});
	}

	@Test
	void repeatingTheCurrentValueChangesAndAuditsNothing() {
		update.execute(adminA.id(), List.of(share(PrivacyField.QUANTITY)));
		int writes = settings.saveAllCalls;
		int audited = audit.changes.size();

		update.execute(adminA.id(), List.of(share(PrivacyField.QUANTITY)));
		update.execute(adminA.id(), List.of(hide(PrivacyField.WASTE))); // already private

		assertThat(settings.saveAllCalls).isEqualTo(writes);
		assertThat(audit.changes).hasSize(audited);
	}
}
