package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.model.BalanceSummary;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyStorePort;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El servicio arranca completo contra PostgreSQL real: Flyway, entidades validadas y las fábricas con la configuración por defecto. */
class ProductionContextIT extends IntegrationTestBase {

    @Autowired
    ApplicationContext context;

    @Test
    void theApplicationStartsWithFlywayAndTheValidatedEntities() {
        assertThat(jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank").query(String.class).list())
                .containsExactly("1", "2", "3", "4", "5", "6", "7"); // V6__evidence_deletion.sql (agregada en V2); V7__evidence_purge_retry.sql (ronda final: la purga aplaza la evidencia que falla)
        assertThat(context.getBean(IdempotencyStorePort.class)).isNotNull();
        assertThat(context.getBean(OutboxStorePort.class)).isNotNull();
    }

    @Test
    void theStrategiesUseTheDefaultMinimums() {
        BalanceSummary seventyPercent = new BalanceSummary(Quantity.ofKg("100"), Quantity.ofKg("70"), Quantity.ZERO,
                Quantity.nonNegative(new BigDecimal("30")), 1, 1);

        transformationFactory.strategyFor(TransformationType.SPINNING).validate(seventyPercent);
        assertThatThrownBy(() -> transformationFactory.strategyFor(TransformationType.WEAVING).validate(seventyPercent))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> transformationFactory.strategyFor(TransformationType.DYEING).validate(seventyPercent))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void theBatchFactoryAvoidsCodesThatAlreadyExist() {
        var saved = savedBatch("10");
        var factory = com.nodotextil.trazatex.production.domain.model.BatchFactory.secure(batches::isCodeTaken);

        for (int i = 0; i < 50; i++) {
            var batch = factory.register(registration("1"), saved.companyId(), saved.createdBy(), START);
            assertThat(batch.traceabilityId()).isNotEqualTo(saved.traceabilityId());
            assertThat(batch.qrCode()).isNotEqualTo(saved.qrCode());
        }
        assertThat(batchFactory).isNotNull();
    }
}
