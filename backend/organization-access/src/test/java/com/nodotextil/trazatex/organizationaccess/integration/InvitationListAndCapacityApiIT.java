package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/invitations} y {@code GET /api/companies/current/capacity}, que V2 agregó para el frontend. Ahora pasan por un caso de uso
 * ({@code ManageInvitationsUseCase.listIssuedBy}, {@code ConsultCompanyCapacityUseCase}) en vez de consultar la base desde el controlador o desde la
 * aplicación; estas pruebas fijan el contrato que ya tenían.
 */
class InvitationListAndCapacityApiIT extends IntegrationTestBase {

    UUID company;
    UUID admin;
    UUID otherAdmin;

    @BeforeEach
    void companyWithTwoAdmins() {
        company = insertCompany(VALID_RUC, "Tejidos Andinos SAC", "ACTIVE");
        admin = insertUser(company, "Ana", "ana@tejidos.pe", "COMPANY_ADMIN", "ACTIVE");
        otherAdmin = insertUser(company, "Rosa", "rosa@tejidos.pe", "COMPANY_ADMIN", "ACTIVE");
    }

    // ---- invitaciones emitidas ----

    @Test
    void theAdminListsOnlyTheInvitationsItIssuedNewestFirstWithTheirEffectiveStatusAndNeverTheToken() throws Exception {
        UUID expired = insertInvitation(company, "vencida@tejidos.pe", "OPERATOR", "PENDING", admin, START.minus(Duration.ofDays(8)));
        UUID live = insertInvitation(company, "vigente@tejidos.pe", "OPERATOR", "PENDING", admin, START.minus(Duration.ofDays(1)));
        insertInvitation(company, "de-rosa@tejidos.pe", "OPERATOR", "PENDING", otherAdmin, START);

        perform(asAdmin(GET("/api/invitations"), admin, company)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(live.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].email").value("vigente@tejidos.pe"))
                .andExpect(jsonPath("$.items[0].issuedBy").value(admin.toString()))
                .andExpect(jsonPath("$.items[1].id").value(expired.toString()))
                .andExpect(jsonPath("$.items[1].status").value("EXPIRED"))
                .andExpect(jsonPath("$.items[0]", not(hasKey("token"))));
    }

    /** I12: la lista se pagina ({@code page}, {@code size}; por defecto 20, máximo 100) y no mezcla las invitaciones de otro administrador. */
    @Test
    void theListIsPagedNewestFirstWithoutRepeatingOrSkippingAndRejectsInvalidPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertInvitation(company, "inv" + i + "@tejidos.pe", "OPERATOR", "PENDING", admin, START.minus(Duration.ofHours(i)));
        }
        insertInvitation(company, "de-rosa@tejidos.pe", "OPERATOR", "PENDING", otherAdmin, START);

        perform(asAdmin(GET("/api/invitations?size=2&page=0"), admin, company)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].email").value("inv0@tejidos.pe")).andExpect(jsonPath("$.items[1].email").value("inv1@tejidos.pe"));
        perform(asAdmin(GET("/api/invitations?size=2&page=2"), admin, company)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].email").value("inv4@tejidos.pe"));
        perform(asAdmin(GET("/api/invitations?size=500"), admin, company)).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.items.length()").value(5));
        perform(asAdmin(GET("/api/invitations?page=-1"), admin, company)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        perform(asAdmin(GET("/api/invitations?size=0"), admin, company)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }

    @Test
    void theLicenseOwnerListsTheInvitationsItIssuedAndAnOperatorIsForbidden() throws Exception {
        insertInvitation(company, "admin2@tejidos.pe", "COMPANY_ADMIN", "PENDING", ownerId, START);
        UUID operator = insertUser(company, "Luis", "luis@tejidos.pe", "OPERATOR", "ACTIVE");

        perform(asOwner(GET("/api/invitations"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].role").value("COMPANY_ADMIN"));
        perform(as(GET("/api/invitations"), operator, company, "OPERATOR")).andExpect(status().isForbidden());
    }

    // ---- cupo de la empresa ----

    @Test
    void theCapacityCountsActiveUsersAndLivePendingInvitationsOfTheOwnCompanyAgainstTheLicenseLimit() throws Exception {
        insertUser(company, "Inactivo", "inactivo@tejidos.pe", "OPERATOR", "INACTIVE");
        insertInvitation(company, "vigente@tejidos.pe", "OPERATOR", "PENDING", admin, START.minus(Duration.ofDays(1)));
        insertInvitation(company, "vencida@tejidos.pe", "OPERATOR", "PENDING", admin, START.minus(Duration.ofDays(8)));
        insertInvitation(company, "aceptada@tejidos.pe", "OPERATOR", "ACCEPTED", admin, START.minus(Duration.ofDays(1)));
        UUID other = insertCompany(OTHER_RUC, "Hilados del Sur SAC", "ACTIVE");
        insertUser(other, "Otro", "otro@hilados.pe", "OPERATOR", "ACTIVE");
        insertInvitation(other, "ajena@hilados.pe", "OPERATOR", "PENDING", null, START);

        perform(asAdmin(GET("/api/companies/current/capacity"), admin, company)).andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.activeUsers").value(2))
                .andExpect(jsonPath("$.pendingInvitations").value(1));
    }

    @Test
    void onlyACompanyAdminSeesTheCapacity() throws Exception {
        UUID operator = insertUser(company, "Luis", "luis@tejidos.pe", "OPERATOR", "ACTIVE");

        perform(as(GET("/api/companies/current/capacity"), operator, company, "OPERATOR")).andExpect(status().isForbidden());
        perform(asOwner(GET("/api/companies/current/capacity"))).andExpect(status().isForbidden());
    }
}
