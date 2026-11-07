package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.application.dto.NotificationPage;
import com.nodotextil.trazatex.notification.domain.model.NotificationRecipient;
import com.nodotextil.trazatex.notification.domain.model.NotificationType;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationInboxServiceTest {
    private final AtomicReference<Object[]> received = new AtomicReference<>();
    private final NotificationRecipient actor = new NotificationRecipient(UUID.randomUUID(), UUID.randomUUID(), "COMPANY_ADMIN");
    private final NotificationInboxService inbox = new NotificationInboxService(new InMemoryNotificationStore() {
        @Override public NotificationPage list(NotificationRecipient r, Boolean unread, String type, int page, int size) {
            received.set(new Object[]{unread, type, page, size});
            return NotificationPage.of(List.of(), page, size, 0);
        }
    });

    @Test void aPageSizeAboveTheMaximumIsCutToOneHundred() {
        inbox.list(actor, null, null, 0, 5000);
        assertThat(received.get()[3]).isEqualTo(100);
    }

    @Test void filtersReachTheStoreAndTheTypeMustBeAKnownCode() {
        inbox.list(actor, true, " TRANSFER_RECEIVED ", 2, 10);
        assertThat(received.get()).containsExactly(true, "TRANSFER_RECEIVED", 2, 10);
        inbox.list(actor, null, "  ", 0, 10);
        assertThat(received.get()[1]).isNull();
        assertThatThrownBy(() -> inbox.list(actor, null, "Transferencia entrante", 0, 10))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo("INVALID_PARAMETER"));
    }

    @Test void aNegativePageOrAnEmptySizeIsInvalidPagination() {
        assertThatThrownBy(() -> inbox.list(actor, null, null, -1, 10)).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.code()).isEqualTo("INVALID_PAGINATION"));
        assertThatThrownBy(() -> inbox.list(actor, null, null, 0, 0)).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.code()).isEqualTo("INVALID_PAGINATION"));
    }

    @Test void thePageReportsTheNumberOfPages() {
        assertThat(NotificationPage.of(List.of(), 0, 20, 41).totalPages()).isEqualTo(3);
        assertThat(NotificationPage.of(List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(NotificationType.parse("TRANSFER_ACCEPTED")).isEqualTo(NotificationType.TRANSFER_ACCEPTED);
    }
}
