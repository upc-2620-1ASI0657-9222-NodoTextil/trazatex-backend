package com.nodotextil.trazatex.notification.infrastructure.event;

import com.nodotextil.trazatex.notification.application.CreateNotificationUseCase;
import com.nodotextil.trazatex.notification.application.DeliverInvitationEmailUseCase;
import com.nodotextil.trazatex.notification.domain.NotificationType;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organizationaccess.application.event.InvitationCreatedEvent;
import com.nodotextil.trazatex.production.application.contract.BatchSnapshot;
import com.nodotextil.trazatex.production.application.contract.BatchSnapshotQuery;
import com.nodotextil.trazatex.production.application.event.TransferAcceptedEvent;
import com.nodotextil.trazatex.production.application.event.TransferRejectedEvent;
import com.nodotextil.trazatex.production.application.event.TransferStartedEvent;
import com.nodotextil.trazatex.traceability.application.event.PossibleDerivedFailureDetectedEvent;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final String COMPANY_ADMIN_ROLE = "COMPANY_ADMIN";

    private final CreateNotificationUseCase createNotification;
    private final DeliverInvitationEmailUseCase deliverInvitationEmail;
    private final OrganizationAccess organizationAccess;
    private final BatchSnapshotQuery batchSnapshots;

    public NotificationEventListener(CreateNotificationUseCase createNotification,
                                      DeliverInvitationEmailUseCase deliverInvitationEmail,
                                      OrganizationAccess organizationAccess,
                                      BatchSnapshotQuery batchSnapshots) {
        this.createNotification = createNotification;
        this.deliverInvitationEmail = deliverInvitationEmail;
        this.organizationAccess = organizationAccess;
        this.batchSnapshots = batchSnapshots;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void invitationCreated(InvitationCreatedEvent event) {
        createNotification.create(event.createdByUserId(), NotificationType.INVITATION,
                "Invitation created", "Invitation for " + event.email() + " expires at " + event.expiresAt());
        deliverInvitationEmail.deliver(event.email(), event.token(), event.role());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void transferStarted(TransferStartedEvent event) {
        organizationAccess.activeUserIdsByCompanyAndRole(event.destinationCompanyId(), COMPANY_ADMIN_ROLE)
                .forEach(user -> createNotification.create(user, NotificationType.TRANSFER_PENDING,
                        "Incoming transfer", "Transfer " + event.transferId() + " is pending"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void transferAccepted(TransferAcceptedEvent event) {
        organizationAccess.activeUserIdsByCompanyAndRole(event.sourceCompanyId(), COMPANY_ADMIN_ROLE)
                .forEach(user -> createNotification.create(user, NotificationType.TRANSFER_ACCEPTED,
                        "Transfer accepted", "Transfer " + event.transferId() + " was received"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void transferRejected(TransferRejectedEvent event) {
        organizationAccess.activeUserIdsByCompanyAndRole(event.sourceCompanyId(), COMPANY_ADMIN_ROLE)
                .forEach(user -> createNotification.create(user, NotificationType.TRANSFER_REJECTED,
                        "Transfer rejected", "Transfer " + event.transferId() + " rejected: " + event.rejectionReason()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void potentialDerivedFailure(PossibleDerivedFailureDetectedEvent event) {
        Map<UUID, UUID> companyByBatch = batchSnapshots.findAllByIds(event.affectedBatchIds()).stream()
                .collect(Collectors.toMap(BatchSnapshot::batchId, BatchSnapshot::responsibleCompanyId,
                        (first, second) -> first));
        for (var batchId : event.affectedBatchIds()) {
            var companyId = companyByBatch.get(batchId);
            if (companyId == null) continue;
            organizationAccess.activeUserIdsByCompanyAndRole(companyId, COMPANY_ADMIN_ROLE)
                    .forEach(user -> createNotification.create(user, NotificationType.POTENTIAL_DERIVED_FAILURE,
                            "Potential derived failure", "Batch " + batchId + " may be affected by failure " + event.failureId()));
        }
    }
}