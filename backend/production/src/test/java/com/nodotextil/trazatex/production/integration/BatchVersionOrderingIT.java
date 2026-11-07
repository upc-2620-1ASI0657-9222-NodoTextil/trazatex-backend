package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code BatchPhaseChanged.batchVersion} (docs/architecture/communication.md): el {@code @Version} del lote tras el cambio.
 * Traceability ordena con él los cambios de un lote que llegan por tópicos distintos, así que debe crecer en cada
 * {@code BatchPhaseChanged} del mismo lote <b>aunque entre medias llegue un {@code QualityStatusChanged}</b>, que sube la
 * versión sin publicar este evento (habrá saltos, nunca retrocesos ni repeticiones).
 */
class BatchVersionOrderingIT extends ApiTestBase {

    @Autowired
    ApplyQualityStatusUseCase qualityStatus;

    private long versionInDatabase(UUID batch) {
        return jdbc.sql("SELECT version FROM batches WHERE id = :id").param("id", batch).query(Long.class).single();
    }

    private List<Long> publishedVersions(UUID batch) {
        List<Long> versions = new ArrayList<>();
        for (String payload : jdbc.sql("SELECT payload::text FROM outbox WHERE event_type = 'BatchPhaseChanged' AND aggregate_id = :id "
                + "ORDER BY created_at").param("id", batch.toString()).query(String.class).list()) {
            JsonNode node = json.readTree(payload).path("payload");
            assertThat(node.path("batchVersion").isIntegralNumber()).as("batchVersion es un número entero").isTrue();
            versions.add(node.path("batchVersion").asLong());
        }
        return versions;
    }

    @Test
    void batchVersionGrowsOnEveryPhaseChangeEvenWithQualityStatusChangesInBetween() throws Exception {
        UUID batch = idOf(registerBatch(10));
        UUID machine = idOf(registerMachine("MQ-V"));
        List<Long> versionAfterEachEvent = new ArrayList<>();
        List<Long> versionAfterEachQualityChange = new ArrayList<>();

        internalCommand(batch, phaseCommand("HOLD", "v-1")).andExpect(status().isOk());
        versionAfterEachEvent.add(versionInDatabase(batch));
        qualityStatus.apply(batch, COMPANY_A, QualityStatus.POTENTIAL_DERIVED_FAILURE, UUID.randomUUID());
        versionAfterEachQualityChange.add(versionInDatabase(batch));

        internalCommand(batch, phaseCommand("RELEASE_HOLD", "v-2")).andExpect(status().isOk());
        versionAfterEachEvent.add(versionInDatabase(batch));
        qualityStatus.apply(batch, COMPANY_A, QualityStatus.PENDING_REEVALUATION, UUID.randomUUID());
        versionAfterEachQualityChange.add(versionInDatabase(batch));

        internalCommand(batch, phaseCommand("START_EVALUATION", "v-3")).andExpect(status().isOk());
        versionAfterEachEvent.add(versionInDatabase(batch));
        internalCommand(batch, phaseCommand("RELEASE", "v-3", false, "Conforme")).andExpect(status().isOk());
        versionAfterEachEvent.add(versionInDatabase(batch));
        qualityStatus.apply(batch, COMPANY_A, QualityStatus.CONFORMING, UUID.randomUUID());
        versionAfterEachQualityChange.add(versionInDatabase(batch));

        UUID transformation = idOf(startTransformation("SPINNING", machine, batch));
        versionAfterEachEvent.add(versionInDatabase(batch));
        postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete",
                obj("outputs", List.of(obj("materialType", "Hilo", "quantityKg", 9)), "wasteKg", 0.5, "wasteReason", "Orillo")).andExpect(status().isOk());
        versionAfterEachEvent.add(versionInDatabase(batch));

        List<Long> published = publishedVersions(batch);

        assertThat(published).as("un BatchPhaseChanged por cada cambio de fase o retención del lote").hasSize(6);
        assertThat(published).as("cada evento lleva la versión que tenía el lote justo después de su cambio")
                .containsExactlyElementsOf(versionAfterEachEvent);
        assertThat(published).as("estrictamente creciente: Traceability aplica solo si es mayor que la guardada")
                .isSorted().doesNotHaveDuplicates();
        assertThat(versionAfterEachQualityChange).as("cada QualityStatusChanged sube la versión sin publicar BatchPhaseChanged")
                .allSatisfy(version -> assertThat(published).doesNotContain(version));
        assertThat(published.get(1) - published.get(0)).as("HOLD → QualityStatusChanged → RELEASE_HOLD deja un salto").isGreaterThanOrEqualTo(2);
        assertThat(published.get(3) - published.get(2)).as("RELEASE consecutivo a START_EVALUATION: sin salto").isEqualTo(1);
    }

    @Test
    void theVersionOfEachBatchOfAnOperationIsItsOwnNotAGlobalCounter() throws Exception {
        UUID first = idOf(registerBatch(10));
        UUID second = idOf(registerBatch(20));
        qualityStatus.apply(second, COMPANY_A, QualityStatus.CONFORMING, UUID.randomUUID());
        qualityStatus.apply(second, COMPANY_A, QualityStatus.NOT_REVIEWED, UUID.randomUUID());
        UUID machine = idOf(registerMachine("MQ-W"));

        startTransformation("SPINNING", machine, first, second);

        assertThat(publishedVersions(first)).containsExactly(versionInDatabase(first));
        assertThat(publishedVersions(second)).containsExactly(versionInDatabase(second));
        assertThat(versionInDatabase(second)).as("el segundo lote tuvo dos cambios de calidad más").isGreaterThan(versionInDatabase(first));
    }

    @Test
    void aRepeatedCommandPublishesNothingSoNoVersionIsReused() throws Exception {
        UUID batch = idOf(registerBatch(10));

        internalCommand(batch, phaseCommand("HOLD", "r-1")).andExpect(status().isOk());
        internalCommand(batch, phaseCommand("HOLD", "r-1")).andExpect(status().isOk());
        internalCommand(batch, phaseCommand("HOLD", "r-2")).andExpect(status().isOk());

        assertThat(publishedVersions(batch)).as("la repetición y el HOLD sin cambio no publican ni suben la versión")
                .containsExactly(versionInDatabase(batch));
    }
}
