package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I11 e I12: {@code GET /api/quality/controls|failures|batches} son páginas {@code {items,page,size,totalElements,totalPages}} de la propia empresa
 * (RNF-03), las arma un caso de uso y no el controlador, y una página cuesta las mismas consultas con 3 filas que con 25 (sin N+1).
 */
class QualityListingApiIT extends FlowTestBase {

    /** Inserta {@code count} controles de la empresa, el 0 es el más reciente; cada uno con {@code testsEach} pruebas. */
    List<UUID> controls(UUID company, int count, int testsEach) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            jdbc.sql("""
                    INSERT INTO quality_control (id, batch_id, company_id, control_type, status, inspector_id, start_date, cancel_reason)
                    VALUES (:id, gen_random_uuid(), :company, 'EVALUATION', 'CANCELLED', :inspector, :start, 'COMPANY_INACTIVE')
                    """).param("id", id).param("company", company).param("inspector", ADMIN_A)
                    .param("start", java.sql.Timestamp.from(START.minus(Duration.ofMinutes(i)))).update();
            for (int t = 0; t < testsEach; t++) {
                jdbc.sql("""
                        INSERT INTO quality_test (id, control_id, criterion_name, expected_value, obtained_value, unit, test_result)
                        VALUES (gen_random_uuid(), :control, :name, '50', '52', 'N', 'CONFORMING')
                        """).param("control", id).param("name", "Prueba " + t).update();
            }
        }
        return ids;
    }

    /** Una falla {@code OPEN} por control (y por lote), la 0 es la más reciente. */
    List<UUID> failures(UUID company, int count) {
        List<UUID> controls = controls(company, count, 0);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            jdbc.sql("""
                    INSERT INTO quality_failure (id, control_id, batch_id, company_id, status, created_at)
                    SELECT :id, id, batch_id, company_id, 'OPEN', :createdAt FROM quality_control WHERE id = :control
                    """).param("id", id).param("control", controls.get(i)).param("createdAt", java.sql.Timestamp.from(START.minus(Duration.ofMinutes(i)))).update();
        }
        return ids;
    }

    static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }

    @Test
    void controlsArePagedNewestFirstWithTheirTestsAndOnlyOfTheOwnCompany() throws Exception {
        List<UUID> own = controls(COMPANY_A, 25, 2);
        controls(COMPANY_B, 2, 1);

        JsonNode first = read(getAs(ADMIN_OF_A, "/api/quality/controls?size=10").andExpect(status().isOk()));
        assertThat(first.path("totalElements").asLong()).isEqualTo(25);
        assertThat(first.path("totalPages").asInt()).isEqualTo(3);
        assertThat(first.path("page").asInt()).isZero();
        assertThat(first.path("size").asInt()).isEqualTo(10);
        assertThat(first.path("items")).hasSize(10);
        assertThat(first.path("items").get(0).path("tests")).as("las pruebas vienen en el listado, en su orden").hasSize(2);
        assertThat(first.path("items").get(0).path("tests").get(1).path("criterionName").asString()).isEqualTo("Prueba 1");

        Set<String> seen = new HashSet<>();
        List<String> order = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode current = read(getAs(ADMIN_OF_A, "/api/quality/controls?size=10&page=" + page).andExpect(status().isOk()));
            for (String id : ids(current)) {
                assertThat(seen.add(id)).as("no repite " + id).isTrue();
                order.add(id);
            }
        }
        assertThat(order).as("del más reciente al más antiguo, sin saltos").containsExactlyElementsOf(own.stream().map(UUID::toString).toList());

        JsonNode other = read(getAs(ADMIN_OF_B, "/api/quality/controls").andExpect(status().isOk()));
        assertThat(other.path("totalElements").asLong()).isEqualTo(2);
        assertThat(ids(other)).doesNotContainAnyElementsOf(order);
    }

    @Test
    void aPageCostsTheSameQueriesWithThreeRowsAsWithTwentyFive() throws Exception {
        controls(COMPANY_A, 3, 2);
        getAs(ADMIN_OF_A, "/api/quality/controls?size=100").andExpect(status().isOk());      // calienta
        int few = StatementCounter.during(() -> getAs(ADMIN_OF_A, "/api/quality/controls?size=100").andExpect(status().isOk()));
        controls(COMPANY_A, 22, 2);
        int many = StatementCounter.during(() -> getAs(ADMIN_OF_A, "/api/quality/controls?size=100").andExpect(jsonPath("$.items.length()").value(25)));

        assertThat(few).isPositive();
        assertThat(many).as("una consulta por fila sería un N+1").isEqualTo(few);

        failures(COMPANY_A, 3);
        int fewFailures = StatementCounter.during(() -> getAs(ADMIN_OF_A, "/api/quality/failures?size=100").andExpect(status().isOk()));
        failures(COMPANY_A, 20);
        int manyFailures = StatementCounter.during(() -> getAs(ADMIN_OF_A, "/api/quality/failures?size=100").andExpect(jsonPath("$.items.length()").value(23)));
        assertThat(manyFailures).isEqualTo(fewFailures);
    }

    @Test
    void failuresArePagedNewestFirstAndOnlyOfTheOwnCompany() throws Exception {
        List<UUID> own = failures(COMPANY_A, 12);
        failures(COMPANY_B, 1);

        JsonNode first = read(getAs(ADMIN_OF_A, "/api/quality/failures?size=5").andExpect(status().isOk()));
        assertThat(first.path("totalElements").asLong()).isEqualTo(12);
        assertThat(first.path("totalPages").asInt()).isEqualTo(3);
        assertThat(ids(first)).containsExactlyElementsOf(own.subList(0, 5).stream().map(UUID::toString).toList());
        assertThat(first.path("items").get(0).path("status").asString()).isEqualTo("OPEN");
        JsonNode last = read(getAs(ADMIN_OF_A, "/api/quality/failures?size=5&page=2").andExpect(status().isOk()));
        assertThat(ids(last)).containsExactlyElementsOf(own.subList(10, 12).stream().map(UUID::toString).toList());
        assertThat(read(getAs(ADMIN_OF_B, "/api/quality/failures")).path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void batchesListTheQualityStatusAndHoldOfTheOwnCompanyAndSkipThoseWithoutStatus() throws Exception {
        UUID a1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1"), a2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        UUID partial = UUID.fromString("00000000-0000-0000-0000-0000000000a3"), foreign = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        projections.insert(BatchProjection.restore(a2, COMPANY_A, BatchPhase.AVAILABLE, true, QualityStatus.FAILED, true, 2, START, "TT-A2"));
        projections.insert(BatchProjection.restore(a1, COMPANY_A, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, 1, START, "TT-A1"));
        projections.insert(BatchProjection.restore(partial, COMPANY_A, BatchPhase.AVAILABLE, false, null, false, 1, START, null));
        projections.insert(BatchProjection.restore(foreign, COMPANY_B, BatchPhase.AVAILABLE, false, QualityStatus.CONFORMING, true, 1, START, "TT-B1"));

        JsonNode page = read(getAs(ADMIN_OF_A, "/api/quality/batches").andExpect(status().isOk()));
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("batchId").asString()).as("por id, orden estable").isEqualTo(a1.toString());
        assertThat(page.path("items").get(0).path("qualityStatus").asString()).isEqualTo("NOT_REVIEWED");
        assertThat(page.path("items").get(0).path("qualityHold").asBoolean()).isFalse();
        assertThat(page.path("items").get(1).path("qualityStatus").asString()).isEqualTo("FAILED");
        assertThat(page.path("items").get(1).path("qualityHold").asBoolean()).isTrue();
        assertThat(page.path("items").get(0).propertyNames()).as("la forma de cada elemento no cambió").containsExactlyInAnyOrder("batchId", "qualityStatus", "qualityHold");
        assertThat(read(getAs(ADMIN_OF_A, "/api/quality/batches?size=1&page=1")).path("items").get(0).path("batchId").asString()).isEqualTo(a2.toString());
    }

    @Test
    void invalidPaginationIsRejectedAndAHugeSizeIsCutToOneHundred() throws Exception {
        for (String path : List.of("/api/quality/controls", "/api/quality/failures", "/api/quality/batches")) {
            getAs(ADMIN_OF_A, path + "?page=-1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
            getAs(ADMIN_OF_A, path + "?size=0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
            getAs(ADMIN_OF_A, path + "?size=5000").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
            getAs(ADMIN_OF_A, path).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalPages").value(0));
        }
    }

    @Test
    void onlyTheCompanyAdministratorLists() throws Exception {
        for (String path : List.of("/api/quality/controls", "/api/quality/failures", "/api/quality/batches")) {
            getAs(OPERATOR_OF_A, path).andExpect(status().isForbidden());
        }
    }
}
