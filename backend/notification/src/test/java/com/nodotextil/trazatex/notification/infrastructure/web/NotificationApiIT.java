package com.nodotextil.trazatex.notification.infrastructure.web;

import com.nodotextil.trazatex.notification.application.NotificationInboxService;
import com.nodotextil.trazatex.notification.infrastructure.persistence.JdbcNotificationStoreAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationApiIT {
    @Test
    void countsOnlyAuthorizedUnreadNotificationsAndKeepsReadOperationsIdempotent() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString())) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword()).load().migrate();
            var jdbc=JdbcClient.create(new DriverManagerDataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword()));
            MockMvc api=api(jdbc);
            var company=UUID.randomUUID();
            var otherCompany=UUID.randomUUID();
            var user=UUID.randomUUID();
            var colleague=UUID.randomUUID();
            var shared=notice(jdbc,company,null,"COMPANY_ADMIN","TRANSFER_RECEIVED",0);
            var personal=notice(jdbc,company,user,"COMPANY_ADMIN","ADMIN_REQUEST_RESOLVED",1);
            var colleagueNotice=notice(jdbc,company,colleague,"COMPANY_ADMIN","ADMIN_REQUEST_RESOLVED",2);
            notice(jdbc,otherCompany,null,"COMPANY_ADMIN","TRANSFER_RECEIVED",3);
            notice(jdbc,company,null,"OPERATOR","TRANSFER_RECEIVED",4);
            notice(jdbc,null,null,"LICENSE_OWNER","ADMIN_REQUEST_SUBMITTED",5);

            api.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                    .andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.timestamp").exists());
            api.perform(get("/api/notifications")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            api.perform(actor(get("/api/notifications"),user,company,"ROL_RARO")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            count(api,user,company,"COMPANY_ADMIN",2);
            count(api,colleague,company,"COMPANY_ADMIN",2);
            count(api,user,otherCompany,"COMPANY_ADMIN",1);
            count(api,user,company,"OPERATOR",1);
            count(api,user,null,"LICENSE_OWNER",1);
            api.perform(actor(get("/api/notifications"),user,company,"COMPANY_ADMIN"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2));
            api.perform(actor(patch("/api/notifications/{id}/read",colleagueNotice),user,company,"COMPANY_ADMIN"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
            api.perform(actor(patch("/api/notifications/{id}/read",personal),user,otherCompany,"COMPANY_ADMIN"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
            api.perform(actor(patch("/api/notifications/{id}/read",personal),user,company,"OPERATOR"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
            // una ajena y una que no existe se ven exactamente igual (código y mensaje): no se revela que existe, ni con PATCH ni con PUT
            String missing=api.perform(actor(patch("/api/notifications/{id}/read",UUID.randomUUID()),user,company,"COMPANY_ADMIN"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND")).andReturn().getResponse().getContentAsString();
            String foreign=api.perform(actor(patch("/api/notifications/{id}/read",colleagueNotice),user,company,"COMPANY_ADMIN"))
                    .andReturn().getResponse().getContentAsString();
            var mapper=JsonMapper.builder().build();
            assertThat(mapper.readTree(foreign).path("code")).isEqualTo(mapper.readTree(missing).path("code"));
            assertThat(mapper.readTree(foreign).path("message")).isEqualTo(mapper.readTree(missing).path("message"));
            api.perform(actor(put("/api/notifications/{id}/read",colleagueNotice),user,company,"COMPANY_ADMIN"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
            api.perform(actor(put("/api/notifications/{id}/read",UUID.randomUUID()),user,company,"COMPANY_ADMIN"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

            for (int retry=0;retry<2;retry++) {
                api.perform(actor(patch("/api/notifications/{id}/read",personal),user,company,"COMPANY_ADMIN"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(personal.toString()))
                        .andExpect(jsonPath("$.read").value(true));
            }
            count(api,user,company,"COMPANY_ADMIN",1);
            count(api,colleague,company,"COMPANY_ADMIN",2);
            assertThat(jdbc.sql("SELECT count(*) FROM notification_read").query(Long.class).single()).isEqualTo(1);
            api.perform(actor(put("/api/notifications/{id}/read",shared),user,company,"COMPANY_ADMIN"))
                    .andExpect(status().isOk());
            count(api,user,company,"COMPANY_ADMIN",0);
            api.perform(actor(put("/api/notifications/{id}/read",personal).param("value","false"),user,company,"COMPANY_ADMIN"))
                    .andExpect(status().isOk());
            count(api,user,company,"COMPANY_ADMIN",1);
            api.perform(actor(put("/api/notifications/read-all"),user,company,"COMPANY_ADMIN"))
                    .andExpect(status().isOk());
            count(api,user,company,"COMPANY_ADMIN",0);
            count(api,colleague,company,"COMPANY_ADMIN",2);
        }
    }

    @Test
    void pagesFiltersByReadStateAndTypeAndCarriesTheStableCodeAndData() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString())) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword()).load().migrate();
            var jdbc=JdbcClient.create(new DriverManagerDataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword()));
            MockMvc api=api(jdbc);
            var company=UUID.randomUUID();
            var user=UUID.randomUUID();
            // 25 notificaciones: la 0 es la más reciente. Las pares son TRANSFER_RECEIVED y las impares POTENTIAL_DERIVED_FAILURE.
            var ids=new UUID[25];
            for (int i=0;i<25;i++) ids[i]=notice(jdbc,company,null,"COMPANY_ADMIN",i%2==0?"TRANSFER_RECEIVED":"POTENTIAL_DERIVED_FAILURE",i);
            for (int i=0;i<25;i+=5) api.perform(actor(put("/api/notifications/{id}/read",ids[i]),user,company,"COMPANY_ADMIN")).andExpect(status().isOk());

            // por defecto: página 0 de 20, más recientes primero
            api.perform(actor(get("/api/notifications"),user,company,"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.items.length()").value(20))
                    .andExpect(jsonPath("$.items[0].id").value(ids[0].toString()))
                    .andExpect(jsonPath("$.items[19].id").value(ids[19].toString()));
            api.perform(actor(get("/api/notifications").param("page","1"),user,company,"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.items[0].id").value(ids[20].toString()));
            // más de 100 se recorta a 100 y la respuesta trae el tamaño efectivo
            api.perform(actor(get("/api/notifications").param("size","5000"),user,company,"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.items.length()").value(25));
            // no leídas: 25 - 5 leídas
            api.perform(actor(get("/api/notifications").param("unread","true").param("size","50"),user,company,"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(20)).andExpect(jsonPath("$.items[?(@.read == true)]").isEmpty());
            api.perform(actor(get("/api/notifications").param("unread","false"),user,company,"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.items[?(@.read == false)]").isEmpty());
            count(api,user,company,"COMPANY_ADMIN",20);
            // por tipo y combinado: pares (13) menos las leídas 0,10,20 (3) = 10
            api.perform(actor(get("/api/notifications").param("type","TRANSFER_RECEIVED"),user,company,"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(13));
            api.perform(actor(get("/api/notifications").param("type","TRANSFER_RECEIVED").param("unread","true"),user,company,"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(10));
            // forma de cada elemento: código estable y data
            api.perform(actor(get("/api/notifications").param("size","1"),user,company,"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].type").value("TRANSFER_RECEIVED"))
                    .andExpect(jsonPath("$.items[0].data.transferId").value("tr-0"))
                    .andExpect(jsonPath("$.items[0].data.batchIds[0]").value("lote-0"))
                    .andExpect(jsonPath("$.items[0].resourceType").value("transferencia"))
                    .andExpect(jsonPath("$.items[0].role").value("COMPANY_ADMIN"))
                    .andExpect(jsonPath("$.items[0].companyId").value(company.toString()))
                    .andExpect(jsonPath("$.items[0].createdAt").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")));
            // entradas inválidas
            api.perform(actor(get("/api/notifications").param("page","-1"),user,company,"COMPANY_ADMIN")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
            api.perform(actor(get("/api/notifications").param("size","0"),user,company,"COMPANY_ADMIN")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
            api.perform(actor(get("/api/notifications").param("type","Transferencia entrante"),user,company,"COMPANY_ADMIN")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            api.perform(actor(get("/api/notifications").param("unread","quizás"),user,company,"COMPANY_ADMIN")).andExpect(status().isBadRequest());
            // las de otra empresa no se cuelan en ninguna página
            api.perform(actor(get("/api/notifications"),user,UUID.randomUUID(),"COMPANY_ADMIN")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }
    }

    @Test
    void theMigrationTurnsTheOldInterfaceTextTypesIntoStableCodesAndFillsData() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString())) {
            database.start();
            var flyway=Flyway.configure().dataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword()).target("2");
            flyway.load().migrate();
            var jdbc=JdbcClient.create(new DriverManagerDataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword()));
            old(jdbc,"Solicitud de administrador","Nueva solicitud de administrador","solicitud","req-1");
            old(jdbc,"Solicitud de administrador","Solicitud revisada","solicitud","req-2");
            old(jdbc,"Transferencia entrante","Transferencia pendiente de aceptación","transferencia","tr-1");
            old(jdbc,"Transferencia aceptada","Transferencia aceptada","transferencia","tr-2");
            old(jdbc,"Transferencia rechazada","Transferencia rechazada","transferencia","tr-3");
            old(jdbc,"Posible falla derivada","Un lote requiere revisión preventiva","lote","lote-1");
            jdbc.sql("INSERT INTO deferred_email(id,event_id,recipient,subject,body,status) VALUES(gen_random_uuid(),gen_random_uuid(),'a@x.controlado','Invitación a TrazaTex','https://app/invitations/token-viejo','SENT')").update();
            jdbc.sql("INSERT INTO deferred_email(id,event_id,recipient,subject,body,status) VALUES(gen_random_uuid(),gen_random_uuid(),'b@x.controlado','Invitación a TrazaTex','https://app/invitations/token-pendiente','PENDING')").update();

            Flyway.configure().dataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword()).load().migrate();

            assertThat(jdbc.sql("SELECT type FROM notification WHERE resource_id='req-1'").query(String.class).single()).isEqualTo("ADMIN_REQUEST_SUBMITTED");
            assertThat(jdbc.sql("SELECT type FROM notification WHERE resource_id='req-2'").query(String.class).single()).isEqualTo("ADMIN_REQUEST_RESOLVED");
            assertThat(jdbc.sql("SELECT type FROM notification WHERE resource_id='tr-1'").query(String.class).single()).isEqualTo("TRANSFER_RECEIVED");
            assertThat(jdbc.sql("SELECT type FROM notification WHERE resource_id='tr-2'").query(String.class).single()).isEqualTo("TRANSFER_ACCEPTED");
            assertThat(jdbc.sql("SELECT type FROM notification WHERE resource_id='tr-3'").query(String.class).single()).isEqualTo("TRANSFER_REJECTED");
            assertThat(jdbc.sql("SELECT type FROM notification WHERE resource_id='lote-1'").query(String.class).single()).isEqualTo("POTENTIAL_DERIVED_FAILURE");
            assertThat(jdbc.sql("SELECT data->>'requestId' FROM notification WHERE resource_id='req-1'").query(String.class).single()).isEqualTo("req-1");
            assertThat(jdbc.sql("SELECT data->>'transferId' FROM notification WHERE resource_id='tr-1'").query(String.class).single()).isEqualTo("tr-1");
            assertThat(jdbc.sql("SELECT data->'batchIds'->>0 FROM notification WHERE resource_id='lote-1'").query(String.class).single()).isEqualTo("lote-1");
            // I2: el cuerpo ya enviado se vacía; el pendiente lo conserva hasta que se envíe
            assertThat(jdbc.sql("SELECT body FROM deferred_email WHERE recipient='a@x.controlado'").query(String.class).single()).isEmpty();
            assertThat(jdbc.sql("SELECT body FROM deferred_email WHERE recipient='b@x.controlado'").query(String.class).single()).contains("token-pendiente");
            // y la tabla ya no admite un cuerpo en un estado final
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                    jdbc.sql("UPDATE deferred_email SET status='SENT' WHERE recipient='b@x.controlado'").update());
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                    jdbc.sql("UPDATE deferred_email SET status='ENVIADO',body='' WHERE recipient='b@x.controlado'").update());
        }
    }

    private static MockMvc api(JdbcClient jdbc) {
        var inbox=new NotificationInboxService(new JdbcNotificationStoreAdapter(jdbc,JsonMapper.builder().build()));
        return MockMvcBuilders.standaloneSetup(new NotificationController(inbox)).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private static void old(JdbcClient jdbc,String type,String title,String resource,String resourceId) {
        jdbc.sql("""
                INSERT INTO notification(id,event_id,company_id,recipient_role,type,title,message,resource_type,resource_id,created_at)
                VALUES(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'COMPANY_ADMIN',:type,:title,'x',:resource,:id,now())
                """).param("type",type).param("title",title).param("resource",resource).param("id",resourceId).update();
    }

    /** {@code ageIndex}: cuanto mayor, más antigua (un minuto por índice). */
    private static UUID notice(JdbcClient jdbc,UUID company,UUID user,String role,String type,int ageIndex) {
        UUID id=UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO notification(id,event_id,recipient_user_id,company_id,recipient_role,type,title,message,resource_type,resource_id,data,created_at)
                VALUES(:id,:event,:user,:company,:role,:type,'Título','Contenido autorizado','transferencia',:resourceId,
                    jsonb_build_object('transferId',:resourceId,'batchIds',jsonb_build_array(:batch)),:at)
                """).param("id",id).param("event",UUID.randomUUID()).param("user",user,Types.OTHER)
                .param("company",company,Types.OTHER).param("role",role).param("type",type)
                .param("resourceId","tr-"+ageIndex).param("batch","lote-"+ageIndex)
                .param("at",OffsetDateTime.now().minusMinutes(ageIndex)).update();
        return id;
    }

    private static MockHttpServletRequestBuilder actor(MockHttpServletRequestBuilder request,UUID user,UUID company,String role) {
        request.header("X-User-Id",user.toString()).header("X-User-Role",role);
        return company==null?request:request.header("X-Company-Id",company.toString());
    }

    private static void count(MockMvc api,UUID user,UUID company,String role,int expected) throws Exception {
        api.perform(actor(get("/api/notifications/unread-count"),user,company,role))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(expected));
    }
}
