package com.nodotextil.trazatex.notification.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.nodotextil.trazatex.notification.application.CreateNotificationUseCase;
import com.nodotextil.trazatex.notification.application.DeliverInvitationEmailUseCase;
import com.nodotextil.trazatex.notification.domain.NotificationType;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organizationaccess.application.event.InvitationCreatedEvent;
import com.nodotextil.trazatex.production.application.event.TransferAcceptedEvent;
import com.nodotextil.trazatex.production.application.event.TransferRejectedEvent;
import com.nodotextil.trazatex.production.application.event.TransferStartedEvent;
import com.nodotextil.trazatex.traceability.application.event.PotentialDerivedFailureDetectedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationEventListenerTest {

    private CreateNotificationUseCase createNotification;
    private DeliverInvitationEmailUseCase deliverInvitationEmail;
    private OrganizationAccess organizations;
    private NotificationEventListener listener;
    private UUID source, destination, user;

    @BeforeEach void setup() {
        createNotification = mock(CreateNotificationUseCase.class);
        deliverInvitationEmail = mock(DeliverInvitationEmailUseCase.class);
        organizations = mock(OrganizationAccess.class);
        listener = new NotificationEventListener(createNotification, deliverInvitationEmail, organizations);
        source = UUID.randomUUID(); destination = UUID.randomUUID(); user = UUID.randomUUID();
    }

    @Test void startedTransferNotifiesDestinationAdmins() {
        when(organizations.activeUserIdsByCompanyAndRole(destination, "COMPANY_ADMIN")).thenReturn(List.of(user));
        UUID id = UUID.randomUUID();
        listener.transferStarted(new TransferStartedEvent(id, source, destination, List.of(), LocalDateTime.now()));
        verify(createNotification).create(user, NotificationType.TRANSFER_PENDING, "Incoming transfer", "Transfer " + id + " is pending");
    }

    @Test void acceptedTransferNotifiesSourceAdmins() {
        when(organizations.activeUserIdsByCompanyAndRole(source, "COMPANY_ADMIN")).thenReturn(List.of(user));
        UUID id = UUID.randomUUID();
        listener.transferAccepted(new TransferAcceptedEvent(id, source, destination, LocalDateTime.now()));
        verify(createNotification).create(user, NotificationType.TRANSFER_ACCEPTED, "Transfer accepted", "Transfer " + id + " was received");
    }

    @Test void rejectedTransferNotifiesSourceAdmins() {
        when(organizations.activeUserIdsByCompanyAndRole(source, "COMPANY_ADMIN")).thenReturn(List.of(user));
        UUID id = UUID.randomUUID();
        listener.transferRejected(new TransferRejectedEvent(id, source, destination, "reason", LocalDateTime.now()));
        verify(createNotification).create(user, NotificationType.TRANSFER_REJECTED, "Transfer rejected", "Transfer " + id + " rejected: reason");
    }

    @Test void transferEventsNeverUseCompanyWideLookup() {
        listener.transferAccepted(new TransferAcceptedEvent(UUID.randomUUID(), source, destination, LocalDateTime.now()));
        listener.transferRejected(new TransferRejectedEvent(UUID.randomUUID(), source, destination, "r", LocalDateTime.now()));
        verify(organizations, never()).activeUserIdsByCompany(any());
    }

    @Test void invitationEventCreatesNotificationAndDeliversEmail() {
        UUID inviter = UUID.randomUUID();
        InvitationCreatedEvent event = new InvitationCreatedEvent(UUID.randomUUID(), "invite@example.test", destination, "OPERATOR", LocalDateTime.now().plusDays(7), inviter, "secret-token");
        listener.invitationCreated(event);
        verify(createNotification).create(inviter, NotificationType.INVITATION, "Invitation created", "Invitation for invite@example.test expires at " + event.expiresAt());
        verify(deliverInvitationEmail).deliver("invite@example.test", "secret-token", "OPERATOR");
    }

    @Test void potentialDerivedFailureNotifiesResponsibleCompanyAdmins() {
        UUID batch = UUID.randomUUID();
        when(organizations.activeUserIdsByCompanyAndRole(eq(destination), eq("COMPANY_ADMIN"))).thenReturn(List.of(user));
        UUID failure = UUID.randomUUID();
        listener.potentialDerivedFailure(new PotentialDerivedFailureDetectedEvent(failure, UUID.randomUUID(), List.of(batch), UUID.randomUUID(), source, LocalDateTime.now(), Map.of(batch, destination)));
        verify(createNotification).create(user, NotificationType.POTENTIAL_DERIVED_FAILURE, "Potential derived failure", "Batch " + batch + " may be affected by failure " + failure);
    }
}