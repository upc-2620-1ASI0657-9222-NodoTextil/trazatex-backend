package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US07, US08. */
class AdminRequestApiIT extends IntegrationTestBase {

    UUID company;
    UUID admin;

    @BeforeEach
    void company() {
        company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
    }

    private UUID requestAdmin() throws Exception {
        String id = read(perform(body(asAdmin(POST("/api/admin-requests"), admin, company),
                "{\"fullName\":\"Marta Quispe\",\"email\":\"marta@hilos.pe\",\"position\":\"Gerente\"}"))
                .andExpect(status().isCreated())).get("id").asString();
        return UUID.fromString(id);
    }

    private org.springframework.test.web.servlet.ResultActions decide(UUID request, String decision) throws Exception {
        return perform(body(asOwner(PATCH("/api/admin-requests/" + request + "/decision")), "{\"decision\":\"" + decision + "\"}"));
    }

    @Test
    void adminRequestsAnotherAdministrator() throws Exception {
        perform(body(asAdmin(POST("/api/admin-requests"), admin, company),
                "{\"fullName\":\"Marta Quispe\",\"email\":\"marta@hilos.pe\",\"position\":\"Gerente\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedBy").value(admin.toString()))
                .andExpect(jsonPath("$.companyId").value(company.toString()));
    }

    @Test
    void incompleteRequestIsNotRegisteredAndMissingDataIsListed() throws Exception {
        perform(body(asAdmin(POST("/api/admin-requests"), admin, company), "{\"email\":\"marta@hilos.pe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", containsInAnyOrder("fullName", "position")));
        assertThat(count("SELECT count(*) FROM admin_requests")).isZero();
    }

    @Test
    void ownerApprovesWithCapacityAndTheNewAdministratorIsInvited() throws Exception {
        UUID request = requestAdmin();

        decide(request, "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.resolvedBy").value(ownerId.toString()))
                .andExpect(jsonPath("$.invitationId").exists());

        assertThat(jdbc.sql("SELECT role || '/' || status || '/' || position || '/' || issued_by FROM invitations WHERE email = 'marta@hilos.pe'")
                .query(String.class).single()).isEqualTo("COMPANY_ADMIN/PENDING/Gerente/" + ownerId);
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload->'payload'->>'fullName' FROM outbox WHERE event_type = 'InvitationCreated'")
                .query(String.class).single()).isEqualTo("Marta Quispe");
        assertThat(jdbc.sql("SELECT display_name || '|' || coalesce(first_name, '-') FROM invitations WHERE email = 'marta@hilos.pe'")
                .query(String.class).single()).isEqualTo("Marta Quispe|-");
        assertThat(outboxCount("AdminRequestResolved")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload->'payload'->>'decision' FROM outbox WHERE event_type = 'AdminRequestResolved'")
                .query(String.class).single()).isEqualTo("APPROVED");
        assertThat(jdbc.sql("SELECT payload->'payload'->>'requesterUserId' FROM outbox WHERE event_type = 'AdminRequestResolved'")
                .query(String.class).single()).isEqualTo(admin.toString());
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'ADMIN_REQUEST_APPROVED'")).isEqualTo(1);
    }

    @Test
    void approvalWithoutCapacityIsRejectedAndTheRequestStaysPending() throws Exception {
        UUID request = requestAdmin();
        insertInvitation(company, "uno@hilos.pe", "OPERATOR", "PENDING", admin, START);
        insertInvitation(company, "dos@hilos.pe", "OPERATOR", "PENDING", admin, START);
        long eventsBefore = count("SELECT count(*) FROM outbox");

        decide(request, "APPROVED").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_LIMIT_REACHED"));

        assertThat(jdbc.sql("SELECT status FROM admin_requests WHERE id = :id").param("id", request).query(String.class).single()).isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'marta@hilos.pe'")).isZero();
        assertThat(count("SELECT count(*) FROM outbox")).isEqualTo(eventsBefore);
        assertThat(count("SELECT count(*) FROM audit_log")).isZero();
    }

    @Test
    void ownerRejectsAndNoInvitationIsCreated() throws Exception {
        UUID request = requestAdmin();

        decide(request, "REJECTED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'marta@hilos.pe'")).isZero();
        assertThat(jdbc.sql("SELECT payload->'payload'->>'decision' FROM outbox WHERE event_type = 'AdminRequestResolved'")
                .query(String.class).single()).isEqualTo("REJECTED");
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'ADMIN_REQUEST_REJECTED'")).isEqualTo(1);
    }

    @Test
    void aResolvedRequestCannotBeResolvedAgain() throws Exception {
        UUID request = requestAdmin();
        decide(request, "REJECTED").andExpect(status().isOk());

        decide(request, "APPROVED").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_REQUEST_NOT_PENDING"));
        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'marta@hilos.pe'")).isZero();
    }

    @Test
    void theDecisionMustBeApprovedOrRejectedAndTheRequestMustExist() throws Exception {
        UUID request = requestAdmin();
        decide(request, "PENDING").andExpect(status().isBadRequest());
        decide(UUID.randomUUID(), "APPROVED").andExpect(status().isNotFound());
    }

    @Test
    void onlyTheOwnerDecidesAndOnlyAdminsRequest() throws Exception {
        UUID request = requestAdmin();
        perform(body(asAdmin(PATCH("/api/admin-requests/" + request + "/decision"), admin, company), "{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        perform(body(asOwner(POST("/api/admin-requests")), "{\"fullName\":\"X Y\",\"email\":\"x@y.pe\",\"position\":\"Z\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerSeesAllRequestsAndAdminOnlyItsOwn() throws Exception {
        requestAdmin();
        UUID other = insertCompany(OTHER_RUC, "Telas SAC", "ACTIVE");
        UUID otherAdmin = insertUser(other, "Beto", "beto@telas.pe", "COMPANY_ADMIN", "ACTIVE");
        perform(body(asAdmin(POST("/api/admin-requests"), otherAdmin, other),
                "{\"fullName\":\"Carla Ríos\",\"email\":\"carla@telas.pe\",\"position\":\"Jefa\"}")).andExpect(status().isCreated());

        perform(asOwner(GET("/api/admin-requests"))).andExpect(jsonPath("$", hasSize(2)));
        perform(asAdmin(GET("/api/admin-requests"), admin, company)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("marta@hilos.pe"));
    }

    @Test
    void usersOfAnInactiveCompanyCannotRequest() throws Exception {
        jdbc.sql("UPDATE companies SET status = 'INACTIVE' WHERE id = :id").param("id", company).update();
        perform(body(asAdmin(POST("/api/admin-requests"), admin, company),
                "{\"fullName\":\"Marta Quispe\",\"email\":\"marta@hilos.pe\",\"position\":\"Gerente\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
    }
}
