package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionRollbackIT extends ApiTestBase {
    @MockitoSpyBean
    AuditLogPort audit;

    @Test
    void aFailureAfterSavingTheBatchEventAndAuditRollsEverythingBackAndTheSameKeyCanRetry() throws Exception {
        String key = UUID.randomUUID().toString();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Interrupción controlada después de guardar la auditoría");
        }).when(audit).record(any(), any(), any(), any(), any(), any());

        postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key)
                .andExpect(status().isInternalServerError());
        for (String table : new String[]{"batches", "batch_compositions", "outbox", "audit_log", "idempotency_keys"}) {
            assertThat(count("SELECT count(*) FROM " + table)).as(table + " debe revertirse").isZero();
        }

        doCallRealMethod().when(audit).record(any(), any(), any(), any(), any(), any());
        var original = read(postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key)
                .andExpect(status().isCreated()));
        var repeated = read(postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key)
                .andExpect(status().isCreated()));
        assertThat(repeated).isEqualTo(original);
        for (String table : new String[]{"batches", "outbox", "audit_log", "idempotency_keys"}) {
            assertThat(count("SELECT count(*) FROM " + table)).as(table + " sin duplicados").isEqualTo(1);
        }
    }
}
