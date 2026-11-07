package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I11 e I12: {@code GET /api/transfers/recipients} lo arma un caso de uso sobre el puerto de la proyección de empresas (el controlador ya no usa
 * JDBC) y es una página {@code {items,page,size,totalElements,totalPages}} (RNF-03): solo empresas {@code ACTIVE}, por razón social.
 */
class TransferRecipientsApiIT extends ApiTestBase {

    private void company(String name, CompanyStatus status) {
        inTx(() -> companies.save(CompanyProjection.of(UUID.randomUUID(), name, status, START)));
    }

    @Test
    void recipientsAreTheActiveCompaniesByBusinessNameInPages() throws Exception {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            String name = "Zeta %02d SAC".formatted(i);
            names.add(name);
            company(name, CompanyStatus.ACTIVE);
        }
        company("Inactiva SAC", CompanyStatus.INACTIVE);

        JsonNode first = read(getAs(OPERATOR_OF_A, "/api/transfers/recipients?size=10").andExpect(status().isOk()));
        // las dos empresas de la base de pruebas ("Empresa …") van antes que las "Zeta …" y la inactiva no sale
        assertThat(first.path("totalElements").asLong()).isEqualTo(25);
        assertThat(first.path("totalPages").asInt()).isEqualTo(3);
        assertThat(first.path("page").asInt()).isZero();
        assertThat(first.path("items")).hasSize(10);
        assertThat(first.path("items").get(0).propertyNames()).containsExactlyInAnyOrder("id", "businessName");

        List<String> all = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            read(getAs(ADMIN_OF_A, "/api/transfers/recipients?size=10&page=" + page)).path("items").forEach(item -> all.add(item.path("businessName").asString()));
        }
        assertThat(all).hasSize(25).doesNotContain("Inactiva SAC").doesNotHaveDuplicates().isSorted();
        assertThat(all).containsAll(names);
    }

    @Test
    void invalidPaginationIsRejectedAndTheDefaultAndMaximumSizesApply() throws Exception {
        getAs(OPERATOR_OF_A, "/api/transfers/recipients?page=-1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        getAs(OPERATOR_OF_A, "/api/transfers/recipients?size=0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        getAs(OPERATOR_OF_A, "/api/transfers/recipients?size=9999").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        getAs(OPERATOR_OF_A, "/api/transfers/recipients").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void onlyOperatorsAndAdministratorsOfACompanyList() throws Exception {
        getAs(ADMIN_OF_B, "/api/transfers/recipients").andExpect(status().isOk());
        getAs(LICENSE_OWNER, "/api/transfers/recipients").andExpect(status().isForbidden());
    }
}
