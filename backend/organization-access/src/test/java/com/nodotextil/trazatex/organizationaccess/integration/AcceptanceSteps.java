package com.nodotextil.trazatex.organizationaccess.integration;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageInvitationsUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.RUC;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.forRuc;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.json;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.required;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pasos de los escenarios de {@code src/test/resources/features/}. Cada paso usa la API real (MockMvc) con las cabeceras que
 * inyecta el Gateway, PostgreSQL real y, para Chequea.pe y HIBP, los adaptadores reales contra WireMock. Los datos de partida se
 * insertan directamente en la base; todo lo que el escenario "hace" pasa por los endpoints.
 */
public class AcceptanceSteps extends IntegrationTestBase {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String FRONTEND = "http://localhost:5173";

    @Autowired
    ManageInvitationsUseCase invitationJob;

    record Reply(int status, JsonNode body) {

        String text(String field) {
            JsonNode node = body.get(field);
            return node == null || node.isNull() ? "" : node.asString();
        }
    }

    // ---- Estado del escenario ----
    private UUID company;
    private UUID admin;
    private String adminEmail = "ana@hilos.pe";
    private UUID user;
    private String userEmail;
    private UUID invitation;
    private String token;
    private String targetEmail;
    private UUID adminRequest;
    private Reply last;
    private final List<Reply> replies = new ArrayList<>();
    private long companiesBefore;
    private long invitationsBefore;
    private String ownerJwt;
    private String loginEmail;
    private String loginPassword;
    private String acceptFirstName = "Luis";
    private String acceptLastName = "Rojas Díaz";
    private String acceptPassword = "contraseña-segura-1";
    private String acceptConfirmation = "contraseña-segura-1";
    private String requestBody;
    private String operatorBody;
    private String newCompanyBody;
    private List<String> fixedFieldNames = List.of();
    private final List<UUID> invitations = new ArrayList<>();

    @Before
    public void freshWorld() {
        resetDatabaseAndWorld();
    }

    // ================================================================ utilidades

    private UUID company() {
        if (company == null) {
            company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
            admin = insertUserWithPassword(company, adminEmail, "COMPANY_ADMIN", "ACTIVE");
        }
        return company;
    }

    private Reply reply(ResultActions result) throws Exception {
        MockHttpServletResponse response = result.andReturn().getResponse();
        String content = response.getContentAsString();
        return new Reply(response.getStatus(), json.readTree(content.isBlank() ? "{}" : content));
    }

    private Reply send(ResultActions result) throws Exception {
        last = reply(result);
        replies.add(last);
        return last;
    }

    private MockHttpServletRequestBuilder byAdmin(MockHttpServletRequestBuilder request) {
        return asAdmin(request, admin, company());
    }

    private static String operatorJson(String email) {
        return "{\"firstName\":\"Luis\",\"lastName\":\"Rojas\",\"email\":\"%s\",\"position\":\"Tejedor\"}".formatted(email);
    }

    private static String jsonOf(String... pairs) {
        StringBuilder text = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            text.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return text.append('}').toString();
    }

    private Reply login(String email, String password) throws Exception {
        return send(perform(body(POST("/api/auth/login"), jsonOf("email", email, "password", password))));
    }

    /** Valida el JWT con la clave pública del JWKS (como hará el Gateway) y devuelve sus claims. */
    private JWTClaimsSet verified(String jwt) throws Exception {
        String jwks = perform(GET("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
        SignedJWT signed = SignedJWT.parse(jwt);
        RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeyByKeyId(signed.getHeader().getKeyID());
        assertThat(signed.verify(new RSASSAVerifier(key))).as("el JWT debe validarse con la clave del JWKS").isTrue();
        return signed.getJWTClaimsSet();
    }

    /** Lo que hace el Gateway: toma los claims del JWT validado y los pasa como cabeceras. */
    private MockHttpServletRequestBuilder withJwt(MockHttpServletRequestBuilder request, String jwt) throws Exception {
        JWTClaimsSet claims = verified(jwt);
        MockHttpServletRequestBuilder builder = request.header("X-User-Id", claims.getSubject()).header("X-User-Role", claims.getStringClaim("role"));
        String companyClaim = claims.getStringClaim("companyId");
        return companyClaim == null ? builder : builder.header("X-Company-Id", companyClaim);
    }

    private List<JsonNode> events(String type) throws Exception {
        List<JsonNode> payloads = new ArrayList<>();
        for (String raw : jdbc.sql("SELECT payload::text FROM outbox WHERE event_type = :t ORDER BY created_at").param("t", type).query(String.class).list()) {
            payloads.add(json.readTree(raw).get("payload"));
        }
        return payloads;
    }

    private String invitationStatus(UUID id) {
        return jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", id).query(String.class).single();
    }

    private UUID invitationOf(String email) {
        return jdbc.sql("SELECT id FROM invitations WHERE email = :e ORDER BY created_at DESC LIMIT 1").param("e", email).query(UUID.class).single();
    }

    private void fillTheCompanyToTheUserLimit() {
        // administrador activo + invitaciones pendientes hasta los 3 usuarios de la licencia de partida
        company();
        insertInvitation(company, "pendiente1@hilos.pe", "OPERATOR", "PENDING", admin, START);
        insertInvitation(company, "pendiente2@hilos.pe", "OPERATOR", "PENDING", admin, START);
    }

    private ResultActions acceptWithTheForm() throws Exception {
        return perform(body(POST("/api/invitations/" + token + "/accept"), jsonOf("firstName", acceptFirstName, "lastName", acceptLastName,
                "password", acceptPassword, "passwordConfirmation", acceptConfirmation)));
    }

    private void pendingOperatorInvitation(String email) {
        company();
        targetEmail = email;
        invitation = insertInvitation(company, email, "OPERATOR", "PENDING", admin, START);
        token = tokenOf(invitation);
    }

    private void stubChequea(String file) {
        useRealExternalServices();
        WIREMOCK.stubFor(forRuc(RUC).willReturn(json(200, required(file))));
    }

    private void aCompanyCreationRequest() {
        newCompanyBody = "{\"ruc\":\"%s\",\"businessName\":\"Superintendencia de Prueba SAC\",\"activities\":[\"WEAVING\",\"DYEING\"],\"adminEmail\":\"admin@sunat.pe\"}"
                .formatted(RUC);
    }

    // ================================================================ US01 — Consultar licencia

    @Dado("que el dueño de licencia ha iniciado sesión")
    public void ownerHasLoggedIn() throws Exception {
        jdbc.sql("UPDATE users SET password_hash = :h WHERE id = :id").param("h", ENCODER.encode(PASSWORD)).param("id", ownerId).update();
        company();
        insertUserWithPassword(company, "operario@hilos.pe", "OPERATOR", "ACTIVE");
        Reply session = login(OWNER_EMAIL, PASSWORD);
        assertThat(session.status()).isEqualTo(200);
        ownerJwt = session.text("token");
    }

    @Cuando("consulta la información de su licencia")
    public void ownerReadsTheLicense() throws Exception {
        send(perform(withJwt(GET("/api/license"), ownerJwt)));
    }

    @Entonces("el sistema muestra las condiciones y límites contratados")
    public void licenseShowsLimits() {
        assertThat(last.status()).isEqualTo(200);
        assertThat(last.body().get("maxCompanies").asInt()).isEqualTo(3);
        assertThat(last.body().get("maxUsersPerCompany").asInt()).isEqualTo(3);
    }

    @Y("muestra las empresas utilizadas y usuarios registrados por empresa.")
    public void licenseShowsUsage() {
        assertThat(last.body().get("companiesUsed").asInt()).isEqualTo(1);
        assertThat(last.body().get("companiesAvailable").asInt()).isEqualTo(2);
        JsonNode usage = last.body().get("companies").get(0);
        assertThat(usage.get("businessName").asString()).isEqualTo("Hilos SAC");
        assertThat(usage.get("usedSlots").asInt()).as("administrador + operario activos").isEqualTo(2);
        assertThat(usage.get("maxUsers").asInt()).isEqualTo(3);
    }

    // ================================================================ US02 — Crear empresa textil

    @Dado("que existe capacidad disponible en la licencia")
    public void licenseHasCompanyCapacity() {
        assertThat(count("SELECT count(*) FROM companies")).isLessThan(3);
    }

    @Y("el dueño completa la razón social, identificador empresarial, actividades textiles y correo del primer administrador")
    public void ownerFillsTheCompanyForm() {
        stubChequea("ruc-activo-habido.json");
        aCompanyCreationRequest();
    }

    @Cuando("confirma la creación")
    public void ownerConfirmsTheCreation() throws Exception {
        companiesBefore = count("SELECT count(*) FROM companies");
        send(perform(body(asOwner(POST("/api/companies")), newCompanyBody)));
    }

    @Entonces("el sistema registra la empresa dentro de la cadena")
    public void companyIsRegistered() throws Exception {
        assertThat(last.status()).isEqualTo(201);
        assertThat(count("SELECT count(*) FROM companies")).isEqualTo(companiesBefore + 1);
        assertThat(jdbc.sql("SELECT status FROM companies WHERE ruc = :r").param("r", RUC).query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(events("CompanyRegistered")).singleElement().satisfies(event -> assertThat(event.get("ruc").asString()).isEqualTo(RUC));
    }

    @Y("envía una invitación al primer administrador.")
    public void firstAdminIsInvited() throws Exception {
        assertThat(jdbc.sql("SELECT role || '/' || status FROM invitations WHERE email = 'admin@sunat.pe'").query(String.class).single())
                .isEqualTo("COMPANY_ADMIN/PENDING");
        assertThat(events("InvitationCreated")).singleElement().satisfies(event -> {
            assertThat(event.get("email").asString()).isEqualTo("admin@sunat.pe");
            assertThat(event.get("acceptUrl").asString()).startsWith(FRONTEND + "/invitations/");
        });
    }

    @Dado("que se alcanzó la cantidad máxima de empresas permitidas")
    public void companyLimitReached() {
        insertCompany("20100070970", "Una SAC", "ACTIVE");
        insertCompany("20100047218", "Dos SAC", "INACTIVE");
        insertCompany("20512528458", "Tres SAC", "ACTIVE");
        stubChequea("ruc-activo-habido.json");
        aCompanyCreationRequest();
    }

    @Cuando("intenta crear una nueva empresa")
    public void ownerTriesToCreateAnotherCompany() throws Exception {
        ownerConfirmsTheCreation();
    }

    @Entonces("el sistema no realiza la creación")
    public void nothingWasCreated() {
        assertThat(last.status()).isGreaterThanOrEqualTo(400);
        assertThat(count("SELECT count(*) FROM companies")).isEqualTo(companiesBefore);
        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'admin@sunat.pe'")).isZero();
    }

    @Y("comunica que se alcanzó el límite contratado.")
    public void limitIsCommunicated() {
        assertThat(last.text("code")).isEqualTo("COMPANY_LIMIT_REACHED");
        assertThat(last.text("message")).contains("límite");
    }

    @Dado("que el identificador empresarial no corresponde a un contribuyente Activo y Habido")
    public void rucIsNotActiveAndHabido() {
        stubChequea("ruc-baja.json");
        aCompanyCreationRequest();
    }

    @Cuando("el dueño de licencia intenta crear la empresa")
    public void ownerTriesToCreateTheCompany() throws Exception {
        ownerConfirmsTheCreation();
    }

    @Y("comunica que el RUC no es válido.")
    public void rucIsInvalid() {
        assertThat(last.text("code")).isEqualTo("RUC_INVALID");
        assertThat(last.text("message")).contains("RUC no es válido");
    }

    // ================================================================ US03 — Gestionar estado de empresa

    private String companyName;
    private long usersBefore;

    @Dado("que existe una empresa Activa")
    public void anActiveCompanyExists() {
        company();
        insertUserWithPassword(company, "operario@hilos.pe", "OPERATOR", "ACTIVE");
        companyName = "Hilos SAC";
        usersBefore = count("SELECT count(*) FROM users WHERE company_id = ?", company);
    }

    @Cuando("el dueño de licencia decide inactivarla")
    public void ownerInactivates() throws Exception {
        send(perform(body(asOwner(PATCH("/api/companies/" + company + "/status")), "{\"status\":\"INACTIVE\"}")));
    }

    @Entonces("el sistema cambia su estado a Inactiva")
    public void companyIsInactive() {
        assertThat(last.status()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT status FROM companies WHERE id = :id").param("id", company).query(String.class).single()).isEqualTo("INACTIVE");
    }

    @Y("conserva toda su información e historial")
    public void companyKeepsEverything() {
        assertThat(jdbc.sql("SELECT business_name || '/' || ruc FROM companies WHERE id = :id").param("id", company).query(String.class).single())
                .isEqualTo(companyName + "/" + VALID_RUC);
        assertThat(count("SELECT count(*) FROM company_activities WHERE company_id = ?", company)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM users WHERE company_id = ?", company)).isEqualTo(usersBefore);
    }

    @Y("sus usuarios no pueden iniciar nuevas operaciones productivas.")
    public void usersCannotStartProductiveOperations() throws Exception {
        // Production lo aplica con su proyección: recibe CompanyStatusChanged = INACTIVE; aquí, además, sus escrituras se rechazan
        assertThat(events("CompanyStatusChanged")).last().satisfies(event -> {
            assertThat(event.get("companyId").asString()).isEqualTo(company.toString());
            assertThat(event.get("status").asString()).isEqualTo("INACTIVE");
        });
        Reply write = reply(perform(body(byAdmin(POST("/api/invitations/operators")), operatorJson("nuevo@hilos.pe"))));
        assertThat(write.status()).isEqualTo(403);
        assertThat(write.text("code")).isEqualTo("COMPANY_INACTIVE");
    }

    @Dado("que existe una empresa Inactiva")
    public void anInactiveCompanyExists() {
        company();
        jdbc.sql("UPDATE companies SET status = 'INACTIVE' WHERE id = :id").param("id", company).update();
    }

    @Cuando("el dueño de licencia decide activarla")
    public void ownerActivates() throws Exception {
        send(perform(body(asOwner(PATCH("/api/companies/" + company + "/status")), "{\"status\":\"ACTIVE\"}")));
    }

    @Entonces("el sistema cambia su estado a Activa")
    public void companyIsActive() {
        assertThat(last.status()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT status FROM companies WHERE id = :id").param("id", company).query(String.class).single()).isEqualTo("ACTIVE");
    }

    @Y("permite nuevamente las operaciones correspondientes a sus usuarios.")
    public void usersCanOperateAgain() throws Exception {
        assertThat(events("CompanyStatusChanged")).last().satisfies(event -> assertThat(event.get("status").asString()).isEqualTo("ACTIVE"));
        Reply write = reply(perform(body(byAdmin(POST("/api/invitations/operators")), operatorJson("nuevo@hilos.pe"))));
        assertThat(write.status()).isEqualTo(201);
    }

    // ================================================================ US04 — Aceptar invitación

    @Dado("que el usuario posee una invitación Pendiente y vigente")
    public void userHasAValidInvitation() {
        pendingOperatorInvitation("luis@hilos.pe");
    }

    @Y("completa sus nombres, apellidos, contraseña y confirmación de contraseña")
    public void userFillsTheForm() {
        acceptFirstName = "Luis";
        acceptLastName = "Rojas Díaz";
        acceptPassword = "contraseña-segura-1";
        acceptConfirmation = "contraseña-segura-1";
    }

    @Cuando("acepta la invitación")
    public void userAccepts() throws Exception {
        send(acceptWithTheForm());
    }

    @Entonces("el sistema crea y activa su cuenta")
    public void accountIsCreatedAndActive() {
        assertThat(last.status()).isEqualTo(201);
        assertThat(jdbc.sql("SELECT status || '/' || role || '/' || company_id FROM users WHERE email = :e").param("e", targetEmail).query(String.class).single())
                .isEqualTo("ACTIVE/OPERATOR/" + company);
    }

    @Y("cambia la invitación a Aceptada.")
    public void invitationIsAccepted() {
        assertThat(invitationStatus(invitation)).isEqualTo("ACCEPTED");
    }

    @Dado("que han transcurrido 7 días desde el envío de una invitación Pendiente")
    public void sevenDaysHavePassed() {
        pendingOperatorInvitation("luis@hilos.pe");
        clock.advance(Duration.ofDays(7));
    }

    @Cuando("el usuario intenta aceptarla")
    public void userTriesToAccept() throws Exception {
        send(acceptWithTheForm());
    }

    @Entonces("el sistema no crea la cuenta")
    public void noAccountIsCreated() {
        assertThat(last.status()).isEqualTo(409);
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", targetEmail)).isZero();
    }

    @Y("la invitación permanece Expirada.")
    public void invitationStaysExpired() throws Exception {
        assertThat(last.text("code")).isEqualTo("INVITATION_EXPIRED");
        assertThat(reply(perform(GET("/api/invitations/" + token))).text("status")).isEqualTo("EXPIRED");
        assertThat(invitationJob.expireDueInvitations()).isEqualTo(1);
        assertThat(invitationStatus(invitation)).isEqualTo("EXPIRED");
    }

    @Dado("que la invitación se encuentra Cancelada")
    public void invitationIsCancelled() {
        pendingOperatorInvitation("luis@hilos.pe");
        jdbc.sql("UPDATE invitations SET status = 'CANCELLED' WHERE id = :id").param("id", invitation).update();
    }

    @Entonces("el sistema no permite utilizarla.")
    public void invitationCannotBeUsed() {
        assertThat(last.status()).isEqualTo(409);
        assertThat(last.text("code")).isEqualTo("INVITATION_CANCELLED");
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", targetEmail)).isZero();
    }

    @Dado("que la invitación se encuentra Aceptada")
    public void invitationIsAlreadyAccepted() throws Exception {
        pendingOperatorInvitation("luis@hilos.pe");
        assertThat(reply(acceptWithTheForm()).status()).isEqualTo(201);
        assertThat(invitationStatus(invitation)).isEqualTo("ACCEPTED");
    }

    @Cuando("se intenta utilizar nuevamente")
    public void invitationIsUsedAgain() throws Exception {
        send(acceptWithTheForm());
    }

    @Entonces("el sistema no permite crear otra cuenta con ella.")
    public void noSecondAccount() {
        assertThat(last.status()).isEqualTo(409);
        assertThat(last.text("code")).isEqualTo("INVITATION_ALREADY_ACCEPTED");
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", targetEmail)).isEqualTo(1);
    }

    @Dado("que la contraseña elegida se encuentra en bases de datos de credenciales filtradas")
    public void passwordIsLeaked() {
        pendingOperatorInvitation("luis@hilos.pe");
        useRealExternalServices();
        acceptPassword = "password";
        acceptConfirmation = "password";
        // SHA-1("password") = 5BAA6 1E4C9B93F3F0682250B6CF8331B7EE68FD8
        WIREMOCK.stubFor(get(urlEqualTo("/range/5BAA6")).willReturn(aResponse().withStatus(200)
                .withBody("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\r\n")));
    }

    @Cuando("el usuario intenta aceptar la invitación")
    public void userTriesToAcceptTheInvitation() throws Exception {
        send(acceptWithTheForm());
    }

    @Entonces("el sistema rechaza la contraseña")
    public void passwordIsRejected() {
        assertThat(last.status()).isEqualTo(400);
        assertThat(last.text("code")).isEqualTo("PASSWORD_COMPROMISED");
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", targetEmail)).isZero();
        assertThat(invitationStatus(invitation)).isEqualTo("PENDING");
    }

    @Y("solicita una diferente.")
    public void aDifferentPasswordIsRequested() {
        assertThat(last.text("message")).contains("diferente");
    }

    // ================================================================ US05 — Iniciar sesión

    @Dado("que el usuario posee una cuenta Activa")
    public void userHasAnActiveAccount() {
        company();
        loginEmail = adminEmail;
    }

    @Y("proporciona su correo y contraseña correctos")
    public void userGivesCorrectCredentials() {
        loginPassword = PASSWORD;
    }

    @Cuando("inicia sesión")
    public void userLogsIn() throws Exception {
        login(loginEmail, loginPassword);
    }

    @Entonces("el sistema autentica al usuario")
    public void userIsAuthenticated() throws Exception {
        assertThat(last.status()).isEqualTo(200);
        assertThat(last.text("tokenType")).isEqualTo("Bearer");
        assertThat(verified(last.text("token")).getSubject()).isEqualTo(admin.toString());
    }

    @Y("permite el acceso según su rol.")
    public void accessFollowsTheRole() throws Exception {
        String jwt = last.text("token");
        assertThat(verified(jwt).getStringClaim("role")).isEqualTo("COMPANY_ADMIN");
        assertThat(reply(perform(withJwt(GET("/api/users"), jwt))).status()).as("el administrador ve los usuarios de su empresa").isEqualTo(200);
        assertThat(reply(perform(withJwt(GET("/api/license"), jwt))).status()).as("la licencia es del Dueño").isEqualTo(403);
    }

    @Dado("que el usuario proporciona credenciales incorrectas")
    public void userGivesWrongCredentials() {
        company();
        loginEmail = adminEmail;
        loginPassword = "contraseña-equivocada";
    }

    @Cuando("intenta iniciar sesión")
    public void userTriesToLogIn() throws Exception {
        login(loginEmail, loginPassword);
    }

    @Entonces("el sistema no permite el acceso")
    public void accessIsDenied() {
        assertThat(last.status()).isEqualTo(401);
        assertThat(last.body().has("token")).isFalse();
    }

    @Y("comunica que las credenciales no son válidas.")
    public void credentialsAreInvalid() {
        assertThat(last.text("code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(last.text("message")).contains("credenciales no son válidas");
    }

    // ================================================================ US06 — Invitar operario

    @Dado("que existe capacidad de usuarios disponible")
    public void userCapacityAvailable() {
        company();
        assertThat(count("SELECT count(*) FROM users WHERE company_id = ? AND status = 'ACTIVE'", company)).isLessThan(3);
    }

    @Y("el administrador completa los datos del operario")
    public void adminFillsTheOperatorData() {
        targetEmail = "luis@hilos.pe";
        operatorBody = operatorJson(targetEmail);
    }

    @Cuando("confirma la invitación")
    public void adminConfirmsTheInvitation() throws Exception {
        send(perform(body(byAdmin(POST("/api/invitations/operators")), operatorBody)));
    }

    @Entonces("el sistema genera una invitación Pendiente")
    public void aPendingInvitationIsGenerated() {
        assertThat(last.status()).isEqualTo(201);
        assertThat(last.text("status")).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT role || '/' || status FROM invitations WHERE email = :e").param("e", targetEmail).query(String.class).single())
                .isEqualTo("OPERATOR/PENDING");
    }

    @Y("la envía al correo registrado.")
    public void itIsSentToTheRegisteredEmail() throws Exception {
        assertThat(events("InvitationCreated")).singleElement().satisfies(event -> {
            assertThat(event.get("email").asString()).isEqualTo(targetEmail);
            assertThat(event.get("acceptUrl").asString()).isEqualTo(FRONTEND + "/invitations/" + tokenOfEmail(targetEmail));
        });
    }

    @Dado("que la suma de usuarios Activos e invitaciones Pendientes alcanza el límite contratado")
    public void activeUsersPlusPendingInvitationsReachTheLimit() {
        company();
        insertUserWithPassword(company, "operario@hilos.pe", "OPERATOR", "ACTIVE");
        insertInvitation(company, "pendiente@hilos.pe", "OPERATOR", "PENDING", admin, START);
        assertThat(count("SELECT count(*) FROM users WHERE company_id = ? AND status = 'ACTIVE'", company)
                + count("SELECT count(*) FROM invitations WHERE company_id = ? AND status = 'PENDING'", company)).isEqualTo(3);
    }

    @Cuando("el administrador intenta invitar a otro operario")
    public void adminTriesToInviteAnotherOperator() throws Exception {
        targetEmail = "otro@hilos.pe";
        invitationsBefore = count("SELECT count(*) FROM invitations");
        send(perform(body(byAdmin(POST("/api/invitations/operators")), operatorJson(targetEmail))));
    }

    @Entonces("el sistema no genera la invitación")
    public void noInvitationIsGenerated() {
        assertThat(last.status()).isEqualTo(409);
        assertThat(count("SELECT count(*) FROM invitations WHERE email = ?", targetEmail)).isZero();
        assertThat(count("SELECT count(*) FROM invitations")).isEqualTo(invitationsBefore);
    }

    @Y("comunica que no existe capacidad disponible.")
    public void noCapacityIsCommunicated() {
        assertThat(last.text("code")).isEqualTo("USER_LIMIT_REACHED");
        assertThat(last.text("message")).contains("capacidad");
    }

    // ================================================================ US07 — Solicitar administrador

    @Dado("que el administrador completa el nombre, correo y cargo del nuevo administrador")
    public void adminFillsTheRequest() {
        company();
        requestBody = jsonOf("fullName", "Marta Quispe", "email", "marta@hilos.pe", "position", "Gerente");
    }

    @Cuando("confirma la solicitud")
    public void adminConfirmsTheRequest() throws Exception {
        send(perform(body(byAdmin(POST("/api/admin-requests")), requestBody)));
    }

    @Entonces("el sistema registra la solicitud")
    public void requestIsRegistered() {
        assertThat(last.status()).isEqualTo(201);
        assertThat(last.text("status")).isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM admin_requests WHERE email = 'marta@hilos.pe' AND status = 'PENDING'")).isEqualTo(1);
    }

    @Y("la envía al dueño de licencia para su revisión.")
    public void requestReachesTheOwner() throws Exception {
        Reply owned = reply(perform(asOwner(GET("/api/admin-requests"))));
        assertThat(owned.status()).isEqualTo(200);
        assertThat(owned.body().get(0).get("email").asString()).isEqualTo("marta@hilos.pe");
        assertThat(owned.body().get(0).get("status").asString()).isEqualTo("PENDING");
    }

    @Dado("que faltan datos obligatorios")
    public void requiredDataIsMissing() {
        company();
        requestBody = jsonOf("email", "marta@hilos.pe");
    }

    @Cuando("intenta enviar la solicitud")
    public void adminTriesToSendTheRequest() throws Exception {
        adminConfirmsTheRequest();
    }

    @Entonces("el sistema no la registra")
    public void requestIsNotRegistered() {
        assertThat(last.status()).isEqualTo(400);
        assertThat(count("SELECT count(*) FROM admin_requests")).isZero();
    }

    @Y("señala los datos pendientes.")
    public void pendingDataIsPointedOut() {
        assertThat(last.text("code")).isEqualTo("MISSING_FIELDS");
        List<String> details = new ArrayList<>();
        last.body().get("details").forEach(detail -> details.add(detail.asString()));
        assertThat(details).containsExactlyInAnyOrder("fullName", "position");
    }

    // ================================================================ US08 — Revisar solicitud de administrador

    @Dado("que existe una solicitud Pendiente")
    public void aPendingRequestExists() throws Exception {
        company();
        targetEmail = "marta@hilos.pe";
        Reply created = reply(perform(body(byAdmin(POST("/api/admin-requests")), jsonOf("fullName", "Marta Quispe", "email", targetEmail, "position", "Gerente"))));
        assertThat(created.status()).isEqualTo(201);
        adminRequest = UUID.fromString(created.text("id"));
    }

    @Y("la empresa posee capacidad de usuarios disponible")
    public void companyHasUserCapacity() {
        assertThat(count("SELECT count(*) FROM users WHERE company_id = ? AND status = 'ACTIVE'", company)).isLessThan(3);
    }

    @Cuando("el dueño de licencia la aprueba")
    public void ownerApproves() throws Exception {
        send(perform(body(asOwner(PATCH("/api/admin-requests/" + adminRequest + "/decision")), "{\"decision\":\"APPROVED\"}")));
    }

    @Entonces("el sistema genera una invitación Pendiente para el nuevo administrador.")
    public void anAdminInvitationIsGenerated() {
        assertThat(last.status()).isEqualTo(200);
        assertThat(last.text("status")).isEqualTo("APPROVED");
        assertThat(jdbc.sql("SELECT role || '/' || status || '/' || company_id FROM invitations WHERE email = :e").param("e", targetEmail).query(String.class).single())
                .isEqualTo("COMPANY_ADMIN/PENDING/" + company);
    }

    @Y("la empresa alcanzó su límite de usuarios")
    public void companyReachedItsUserLimit() {
        fillTheCompanyToTheUserLimit();
    }

    @Cuando("el dueño intenta aprobarla")
    public void ownerTriesToApprove() throws Exception {
        invitationsBefore = count("SELECT count(*) FROM invitations");
        ownerApproves();
    }

    @Cuando("el dueño de licencia la rechaza")
    public void ownerRejects() throws Exception {
        invitationsBefore = count("SELECT count(*) FROM invitations");
        send(perform(body(asOwner(PATCH("/api/admin-requests/" + adminRequest + "/decision")), "{\"decision\":\"REJECTED\"}")));
    }

    @Entonces("el sistema registra el rechazo")
    public void rejectionIsRegistered() throws Exception {
        assertThat(last.status()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT status || '/' || resolved_by FROM admin_requests WHERE id = :id").param("id", adminRequest).query(String.class).single())
                .isEqualTo("REJECTED/" + ownerId);
        assertThat(events("AdminRequestResolved")).singleElement().satisfies(event -> assertThat(event.get("decision").asString()).isEqualTo("REJECTED"));
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'ADMIN_REQUEST_REJECTED'")).isEqualTo(1);
    }

    @Y("no genera una invitación.")
    public void noInvitationIsCreatedForTheRejectedRequest() {
        assertThat(count("SELECT count(*) FROM invitations WHERE email = ?", targetEmail)).isZero();
        assertThat(count("SELECT count(*) FROM invitations")).isEqualTo(invitationsBefore);
    }

    // ================================================================ US09 — Gestionar estado de usuario

    private void anOperator(String status) {
        company();
        userEmail = "operario@hilos.pe";
        user = insertUserWithPassword(company, userEmail, "OPERATOR", status);
    }

    private String userStatus() {
        return jdbc.sql("SELECT status FROM users WHERE id = :id").param("id", user).query(String.class).single();
    }

    @Dado("que existe un usuario Activo")
    public void anActiveUserExists() {
        anOperator("ACTIVE");
    }

    @Cuando("el administrador lo inactiva")
    public void adminInactivatesTheUser() throws Exception {
        send(perform(body(byAdmin(PATCH("/api/users/" + user + "/status")), "{\"status\":\"INACTIVE\"}")));
    }

    @Entonces("cambia a Inactivo")
    public void userIsInactive() {
        assertThat(last.status()).isEqualTo(200);
        assertThat(userStatus()).isEqualTo("INACTIVE");
    }

    @Y("conserva su historial")
    public void userKeepsTheHistory() {
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", user)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'USER_STATUS_CHANGED'", user.toString())).isEqualTo(1);
    }

    @Y("no puede autenticarse ni realizar operaciones.")
    public void userCannotAuthenticate() throws Exception {
        Reply attempt = login(userEmail, PASSWORD);
        assertThat(attempt.status()).isEqualTo(401);
        assertThat(attempt.body().has("token")).isFalse();
        // las demás partes del sistema se enteran por el evento
        assertThat(events("UserAccountUpdated")).last().satisfies(event -> {
            assertThat(event.get("userId").asString()).isEqualTo(user.toString());
            assertThat(event.get("status").asString()).isEqualTo("INACTIVE");
        });
    }

    @Dado("que existe un usuario Inactivo")
    public void anInactiveUserExists() {
        anOperator("INACTIVE");
    }

    @Y("existe capacidad disponible")
    public void capacityIsAvailable() {
        assertThat(count("SELECT count(*) FROM users WHERE company_id = ? AND status = 'ACTIVE'", company)).isLessThan(3);
    }

    @Cuando("el administrador lo reactiva")
    public void adminReactivatesTheUser() throws Exception {
        send(perform(body(byAdmin(PATCH("/api/users/" + user + "/status")), "{\"status\":\"ACTIVE\"}")));
    }

    @Entonces("cambia a Activo")
    public void userIsActive() {
        assertThat(last.status()).isEqualTo(200);
        assertThat(userStatus()).isEqualTo("ACTIVE");
    }

    @Y("recupera el acceso según su rol.")
    public void userRecoversTheAccess() throws Exception {
        Reply session = login(userEmail, PASSWORD);
        assertThat(session.status()).isEqualTo(200);
        assertThat(verified(session.text("token")).getStringClaim("role")).isEqualTo("OPERATOR");
        assertThat(reply(perform(withJwt(GET("/api/users"), session.text("token")))).status()).as("un operario no administra usuarios").isEqualTo(403);
    }

    @Dado("que no existe capacidad disponible")
    public void noCapacityIsAvailable() {
        anOperator("INACTIVE");
        fillTheCompanyToTheUserLimit();
    }

    @Cuando("se intenta reactivar al usuario")
    public void reactivationIsAttempted() throws Exception {
        adminReactivatesTheUser();
    }

    @Entonces("el sistema no permite la reactivación.")
    public void reactivationIsNotAllowed() {
        assertThat(last.status()).isEqualTo(409);
        assertThat(last.text("code")).isEqualTo("USER_LIMIT_REACHED");
        assertThat(userStatus()).isEqualTo("INACTIVE");
    }

    // ================================================================ US11 — Configurar privacidad B2B

    private static final List<String> NOT_CONFIGURABLE_SHARED = List.of("TRACEABILITY_ID", "RESPONSIBLE_COMPANY", "GENEALOGY_RELATIONS",
            "MATERIAL_TYPE", "OPERATIONAL_PHASE", "QUALITY_STATUS", "REGISTERED_AT", "FINAL_PRODUCT_INDICATOR");
    private static final List<String> NOT_CONFIGURABLE_PRIVATE = List.of("OPERATOR_IDENTITY", "INTERNAL_OBSERVATIONS", "FINAL_PRODUCT_COMMERCIAL_DATA");

    private Reply setVisibility(String field, String visibility) throws Exception {
        return send(perform(body(byAdmin(PUT("/api/privacy-settings")), "{\"fields\":{\"%s\":\"%s\"}}".formatted(field, visibility))));
    }

    private JsonNode privacyOfTheCompany() throws Exception {
        return reply(perform(byAdmin(GET("/api/privacy-settings")))).body().get("fields");
    }

    @Dado("que existe un campo Configurable")
    public void aConfigurableFieldExists() {
        stubChequea("ruc-activo-habido.json");
        aCompanyCreationRequest();
    }

    @Cuando("se habilita para la empresa")
    public void itIsEnabledForTheCompany() throws Exception {
        ownerConfirmsTheCreation();
        assertThat(last.status()).isEqualTo(201);
        company = UUID.fromString(last.body().get("company").get("id").asString());
    }

    @Entonces("el sistema lo establece como Privado por defecto.")
    public void itIsPrivateByDefault() throws Exception {
        assertThat(count("SELECT count(*) FROM privacy_settings WHERE company_id = ? AND visibility = 'PRIVATE'", company)).isEqualTo(11);
        assertThat(count("SELECT count(*) FROM privacy_settings WHERE company_id = ? AND visibility = 'SHARED'", company)).isZero();
        assertThat(events("CompanyPrivacyUpdated")).singleElement().satisfies(event -> {
            assertThat(event.get("fields").size()).isEqualTo(11);
            event.get("fields").forEach(visibility -> assertThat(visibility.asString()).isEqualTo("PRIVATE"));
        });
    }

    @Dado("que existe un campo Configurable Privado")
    public void aPrivateConfigurableFieldExists() throws Exception {
        company();
        assertThat(privacyOfTheCompany().get("SUPPLIER").asString()).isEqualTo("PRIVATE");
    }

    @Cuando("el administrador lo establece como Compartido")
    public void adminSharesIt() throws Exception {
        setVisibility("SUPPLIER", "SHARED");
    }

    @Entonces("el sistema guarda la configuración")
    public void configurationIsSaved() throws Exception {
        assertThat(last.status()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT visibility FROM privacy_settings WHERE company_id = :c AND field = 'SUPPLIER'").param("c", company).query(String.class).single())
                .isEqualTo("SHARED");
        assertThat(privacyOfTheCompany().get("SUPPLIER").asString()).isEqualTo("SHARED");
    }

    @Y("permite visualizarlo desde otras empresas cuando corresponda.")
    public void itCanBeSeenByOtherCompanies() throws Exception {
        // Traceability filtra con el mapa que recibe en CompanyPrivacyUpdated
        assertThat(events("CompanyPrivacyUpdated")).last().satisfies(event -> {
            assertThat(event.get("companyId").asString()).isEqualTo(company.toString());
            assertThat(event.get("fields").get("SUPPLIER").asString()).isEqualTo("SHARED");
            assertThat(event.get("fields").get("ORIGIN").asString()).isEqualTo("PRIVATE");
        });
    }

    @Dado("que existe un campo Configurable Compartido")
    public void aSharedConfigurableFieldExists() throws Exception {
        company();
        assertThat(setVisibility("SUPPLIER", "SHARED").status()).isEqualTo(200);
    }

    @Cuando("el administrador lo establece como Privado")
    public void adminMakesItPrivate() throws Exception {
        setVisibility("SUPPLIER", "PRIVATE");
    }

    @Entonces("el sistema deja de mostrarlo a otras empresas.")
    public void itIsNoLongerShown() throws Exception {
        assertThat(last.status()).isEqualTo(200);
        assertThat(privacyOfTheCompany().get("SUPPLIER").asString()).isEqualTo("PRIVATE");
        assertThat(events("CompanyPrivacyUpdated")).last().satisfies(event -> assertThat(event.get("fields").get("SUPPLIER").asString()).isEqualTo("PRIVATE"));
    }

    @Dado("que el administrador consulta Traceability ID, empresa responsable, relaciones de genealogía, tipo de material, fase operativa, estado de calidad, fecha de registro o indicador de producto final")
    public void adminConsultsTheAlwaysSharedInformation() throws Exception {
        company();
        fixedFieldNames = NOT_CONFIGURABLE_SHARED;
        JsonNode configurable = privacyOfTheCompany();
        fixedFieldNames.forEach(name -> assertThat(configurable.has(name)).as("%s no es un campo configurable", name).isFalse());
    }

    @Cuando("intenta establecerlo como Privado")
    public void adminTriesToMakeThemPrivate() throws Exception {
        replies.clear();
        for (String name : fixedFieldNames) {
            setVisibility(name, "PRIVATE");
        }
    }

    @Entonces("el sistema no permite modificar su visibilidad.")
    public void visibilityCannotBeModified() throws Exception {
        assertThat(replies).hasSize(fixedFieldNames.size()).allSatisfy(attempt -> {
            assertThat(attempt.status()).isEqualTo(400);
            assertThat(attempt.text("code")).isEqualTo("FIELD_NOT_CONFIGURABLE");
        });
        assertThat(count("SELECT count(*) FROM privacy_settings WHERE visibility = 'SHARED'")).isZero();
        assertThat(events("CompanyPrivacyUpdated")).isEmpty();
    }

    @Dado("que se consulta la identidad del operario, observaciones internas o datos comerciales de producto final")
    public void theAlwaysPrivateInformationIsConsulted() {
        company();
        fixedFieldNames = NOT_CONFIGURABLE_PRIVATE;
    }

    @Cuando("otra empresa accede a la trazabilidad")
    public void anotherCompanyAccessesTheTraceability() throws Exception {
        // Lo que organization-access controla de esto: lo que se publica para Traceability (CompanyPrivacyUpdated) y que
        // esa información no se pueda compartir. El filtro en sí (US36) lo ejecuta y prueba Traceability.
        replies.clear();
        for (String name : fixedFieldNames) {
            setVisibility(name, "SHARED");
        }
        assertThat(setVisibility("SUPPLIER", "SHARED").status()).isEqualTo(200);
    }

    @Entonces("el sistema mantiene dicha información oculta.")
    public void theInformationStaysHidden() throws Exception {
        assertThat(replies.subList(0, fixedFieldNames.size())).allSatisfy(attempt -> assertThat(attempt.status()).isEqualTo(400));
        assertThat(events("CompanyPrivacyUpdated")).hasSize(1).allSatisfy(event ->
                fixedFieldNames.forEach(name -> assertThat(event.get("fields").has(name)).as("%s no puede salir en el mapa de privacidad", name).isFalse()));
        assertThat(privacyOfTheCompany().size()).isEqualTo(11);
    }

    // ================================================================ US41 — Cancelar invitación

    @Dado("que existe una invitación Pendiente emitida por el usuario")
    public void aPendingInvitationIssuedByTheUserExists() throws Exception {
        company();
        targetEmail = "luis@hilos.pe";
        Reply created = reply(perform(body(byAdmin(POST("/api/invitations/operators")), operatorJson(targetEmail))));
        assertThat(created.status()).isEqualTo(201);
        invitation = UUID.fromString(created.text("id"));
        token = tokenOf(invitation);
    }

    @Cuando("decide cancelarla")
    public void issuerCancelsIt() throws Exception {
        send(perform(byAdmin(POST("/api/invitations/" + invitation + "/cancel"))));
    }

    @Entonces("el sistema cambia su estado a Cancelada")
    public void invitationIsCancelledNow() {
        assertThat(last.status()).isEqualTo(200);
        assertThat(invitationStatus(invitation)).isEqualTo("CANCELLED");
    }

    @Y("la invitación ya no puede utilizarse para crear una cuenta.")
    public void cancelledInvitationCannotCreateAnAccount() throws Exception {
        Reply attempt = reply(acceptWithTheForm());
        assertThat(attempt.status()).isEqualTo(409);
        assertThat(attempt.text("code")).isEqualTo("INVITATION_CANCELLED");
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", targetEmail)).isZero();
    }

    @Dado("que una invitación se encuentra Aceptada, Expirada o Cancelada")
    public void anInvitationIsAcceptedExpiredOrCancelled() {
        company();
        invitations.add(insertInvitation(company, "aceptada@hilos.pe", "OPERATOR", "ACCEPTED", admin, START));
        invitations.add(insertInvitation(company, "expirada@hilos.pe", "OPERATOR", "EXPIRED", admin, START));
        invitations.add(insertInvitation(company, "cancelada@hilos.pe", "OPERATOR", "CANCELLED", admin, START));
    }

    @Cuando("el usuario intenta cancelarla")
    public void userTriesToCancelIt() throws Exception {
        replies.clear();
        for (UUID id : invitations) {
            send(perform(byAdmin(POST("/api/invitations/" + id + "/cancel"))));
        }
    }

    @Entonces("el sistema no modifica su estado.")
    public void stateIsNotModified() {
        assertThat(replies).hasSize(3).allSatisfy(attempt -> {
            assertThat(attempt.status()).isEqualTo(409);
            assertThat(attempt.text("code")).isEqualTo("INVITATION_NOT_PENDING");
        });
        assertThat(List.of(invitationStatus(invitations.get(0)), invitationStatus(invitations.get(1)), invitationStatus(invitations.get(2))))
                .containsExactly("ACCEPTED", "EXPIRED", "CANCELLED");
    }
}
