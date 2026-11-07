package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.domain.model.NotificationNotice;
import com.nodotextil.trazatex.notification.domain.model.NotificationType;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private InMemoryNotificationStore store;
    private NotificationService service;
    private final UUID companyA = UUID.randomUUID(), companyB = UUID.randomUUID(), failure = UUID.randomUUID(), source = UUID.randomUUID();

    @BeforeEach void setUp() {
        store = new InMemoryNotificationStore();
        service = new NotificationService(store, mapper);
    }

    private String event(String type, String aggregate, Map<String, Object> payload) {
        return mapper.writeValueAsString(BaseDomainEvent.of(type, aggregate, payload));
    }
    private static Map<String, Object> affected(UUID batch, UUID company) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("batchId", batch.toString());
        a.put("companyId", company == null ? null : company.toString());
        a.put("commonAncestorBatchId", UUID.randomUUID().toString());
        return a;
    }

    @Test void oneAlertPerFailureAndAffectedCompanyWithAllItsBatches() {
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID(), b1 = UUID.randomUUID(), orphan = UUID.randomUUID();
        service.consume(event("PossibleDerivedFailureDetected", failure.toString(), Map.of("failureId", failure.toString(),
                "sourceBatchId", source.toString(), "affected", List.of(affected(a1, companyA), affected(b1, companyB), affected(a2, companyA), affected(orphan, null)))));

        assertThat(store.notices).hasSize(2);
        NotificationNotice forA = store.notices.stream().filter(n -> companyA.equals(n.companyId())).findFirst().orElseThrow();
        assertThat(forA.type()).isEqualTo(NotificationType.POTENTIAL_DERIVED_FAILURE);
        assertThat(forA.role()).isEqualTo("COMPANY_ADMIN");
        assertThat(forA.userId()).isNull();
        assertThat(forA.resource()).isEqualTo("lote");
        assertThat(forA.resourceId()).isEqualTo(a1.toString());
        assertThat(forA.data()).containsEntry("failureId", failure.toString()).containsEntry("sourceBatchId", source.toString())
                .containsEntry("batchIds", List.of(a1.toString(), a2.toString()));
        assertThat(forA.message()).startsWith("2 lotes");
        NotificationNotice forB = store.notices.stream().filter(n -> companyB.equals(n.companyId())).findFirst().orElseThrow();
        assertThat(forB.data()).containsEntry("batchIds", List.of(b1.toString()));
        assertThat(forB.message()).startsWith("Un lote");
        assertThat(store.emailed).containsExactlyElementsOf(store.notices);
    }

    @Test void theSameFailureEventTwiceCreatesNothingTheSecondTime() {
        String json = event("PossibleDerivedFailureDetected", failure.toString(), Map.of("failureId", failure.toString(),
                "sourceBatchId", source.toString(), "affected", List.of(affected(UUID.randomUUID(), companyA))));
        service.consume(json);
        service.consume(json);
        assertThat(store.notices).hasSize(1);
        assertThat(store.emailed).hasSize(1);
    }

    @Test void aDerivedFailureWithoutAffectedBatchesGoesToTheDeadLetterTopic() {
        assertThatThrownBy(() -> service.consume(event("PossibleDerivedFailureDetected", failure.toString(),
                Map.of("failureId", failure.toString(), "sourceBatchId", source.toString()))))
                .isInstanceOf(MalformedEventException.class);
        assertThat(store.notices).isEmpty();
    }

    @Test void qualityStatusChangedNoLongerRaisesTheAlertBecauseItDependsOnWhatQualityDoesWithTheBatch() {
        service.consume(event("QualityStatusChanged", UUID.randomUUID().toString(), Map.of("batchId", UUID.randomUUID().toString(),
                "companyId", companyA.toString(), "previousStatus", "NOT_REVIEWED", "newStatus", "POTENTIAL_DERIVED_FAILURE")));
        assertThat(store.notices).isEmpty();
    }

    @Test void aTransferInitiatedAlertsTheReceivingCompanyWithItsIds() {
        UUID transfer = UUID.randomUUID(), batch = UUID.randomUUID();
        service.consume(event("TransferInitiated", transfer.toString(), Map.of("transferId", transfer.toString(), "batchIds", List.of(batch.toString()),
                "fromCompanyId", companyA.toString(), "toCompanyId", companyB.toString())));
        var n = store.notices.getFirst();
        assertThat(n.type()).isEqualTo(NotificationType.TRANSFER_RECEIVED);
        assertThat(n.companyId()).isEqualTo(companyB);
        assertThat(n.resource()).isEqualTo("transferencia");
        assertThat(n.resourceId()).isEqualTo(transfer.toString());
        assertThat(n.data()).containsEntry("transferId", transfer.toString()).containsEntry("batchIds", List.of(batch.toString()))
                .containsEntry("fromCompanyId", companyA.toString());
    }

    @Test void acceptedAndRejectedTransfersAlertTheOriginCompanyWithDistinctCodes() {
        for (var type : List.of("TransferAccepted", "TransferRejected")) {
            UUID transfer = UUID.randomUUID();
            service.consume(event(type, transfer.toString(), Map.of("transferId", transfer.toString(), "batchIds", List.of(),
                    "fromCompanyId", companyA.toString(), "toCompanyId", companyB.toString())));
        }
        assertThat(store.notices).extracting(NotificationNotice::type)
                .containsExactly(NotificationType.TRANSFER_ACCEPTED, NotificationType.TRANSFER_REJECTED);
        assertThat(store.notices).allSatisfy(n -> assertThat(n.companyId()).isEqualTo(companyA));
    }

    @Test void adminRequestsAlertTheLicenseOwnerAndTheRequester() {
        UUID request = UUID.randomUUID(), requester = UUID.randomUUID();
        service.consume(event("AdminRequestSubmitted", request.toString(), Map.of("requestId", request.toString(), "companyId", companyA.toString())));
        service.consume(event("AdminRequestResolved", request.toString(), Map.of("requestId", request.toString(), "companyId", companyA.toString(),
                "requesterUserId", requester.toString(), "decision", "APPROVED")));
        var submitted = store.notices.get(0);
        assertThat(submitted.type()).isEqualTo(NotificationType.ADMIN_REQUEST_SUBMITTED);
        assertThat(submitted.role()).isEqualTo("LICENSE_OWNER");
        assertThat(submitted.companyId()).isNull();
        var resolved = store.notices.get(1);
        assertThat(resolved.type()).isEqualTo(NotificationType.ADMIN_REQUEST_RESOLVED);
        assertThat(resolved.userId()).isEqualTo(requester);
        assertThat(resolved.message()).contains("aprobada");
        assertThat(resolved.data()).containsEntry("decision", "APPROVED");
    }

    @Test void anInvitationBecomesAnEmailOnlyWhileItIsValid() {
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS), past = Instant.now().minus(1, ChronoUnit.HOURS);
        service.consume(event("InvitationCreated", UUID.randomUUID().toString(), Map.of("email", "nuevo@correo.controlado",
                "acceptUrl", "https://app.trazatex.example/invitations/tok", "expiresAt", future.toString())));
        service.consume(event("InvitationCreated", UUID.randomUUID().toString(), Map.of("email", "viejo@correo.controlado",
                "acceptUrl", "https://app.trazatex.example/invitations/old", "expiresAt", past.toString())));
        assertThat(store.invitations).hasSize(1);
        assertThat(store.invitations.getFirst()).startsWith("nuevo@correo.controlado|").contains("https://app.trazatex.example/invitations/tok");
        assertThat(store.notices).isEmpty();
    }

    @Test void aUserAccountUpdateOnlyFeedsTheDirectory() {
        UUID user = UUID.randomUUID();
        service.consume(event("UserAccountUpdated", user.toString(), Map.of("userId", user.toString(), "companyId", companyA.toString(),
                "role", "COMPANY_ADMIN", "email", "admin@correo.controlado", "status", "ACTIVE")));
        assertThat(store.projected).containsExactly(user + ":ACTIVE");
        assertThat(store.notices).isEmpty();
    }
}
