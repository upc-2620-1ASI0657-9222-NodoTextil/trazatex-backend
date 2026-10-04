package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nodotextil.trazatex.organizationaccess.application.ExpirePendingInvitationsUseCase;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

class InvitationExpirationJobTest {

	@Test
	void eachRunDelegatesToTheUseCase() {
		ExpirePendingInvitationsUseCase useCase = mock(ExpirePendingInvitationsUseCase.class);

		new InvitationExpirationJob(useCase).expireInvitations();

		verify(useCase).execute();
	}

	@Test
	void isScheduledEveryFiveMinutesByDefaultAndConfigurable() throws Exception {
		Method method = InvitationExpirationJob.class.getDeclaredMethod("expireInvitations");

		Scheduled scheduled = method.getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.fixedDelayString())
				.isEqualTo("${app.invitations.expiration-interval-ms:300000}");
	}

	@Test
	void theModuleEnablesScheduling() {
		assertThat(SchedulingConfiguration.class.getAnnotation(EnableScheduling.class))
				.isNotNull();
	}
}
