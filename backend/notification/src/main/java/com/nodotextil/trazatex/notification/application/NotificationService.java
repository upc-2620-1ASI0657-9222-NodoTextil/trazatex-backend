package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.application.port.out.NotificationStorePort;
import com.nodotextil.trazatex.notification.domain.model.NotificationNotice;
import com.nodotextil.trazatex.notification.domain.model.NotificationType;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.EventEnvelopes;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Convierte un hecho que otro servicio publicó en (a) una notificación dentro de la aplicación y/o (b) un correo. Todo el efecto de un evento
 * —marca de procesado, notificación y correos de sus destinatarios— ocurre en <b>una</b> transacción: o queda todo o no queda nada, y reentregar
 * el evento no duplica nada.
 */
@Service
public class NotificationService {
    private final NotificationStorePort store;
    private final ObjectMapper mapper;

    public NotificationService(NotificationStorePort store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Transactional
    public void consume(String json) {
        BaseDomainEvent e = EventEnvelopes.parse(mapper, json);
        if (!store.claim(e.eventId(),e.type())) return;
        JsonNode p = mapper.valueToTree(e.payload());
        var at = e.occurredAt().atOffset(ZoneOffset.UTC);
        switch (e.type()) {
            case "UserAccountUpdated" -> store.projectUser(id(p,"userId"),id(p,"companyId"),text(p,"role"),
                    text(p,"email"),text(p,"status"),at);
            case "InvitationCreated" -> {
                var expires = java.time.Instant.parse(text(p,"expiresAt"));
                if (!expires.isAfter(java.time.Instant.now())) return;
                String body = "Has recibido una invitación a TrazaTex. Define tu contraseña en " + text(p,"acceptUrl")
                        + "\nVálida hasta " + text(p,"expiresAt") + ". Si no esperabas esta invitación, ignórala.";
                store.enqueue(e.eventId(),text(p,"email"),"Invitación a TrazaTex",body,expires);
            }
            case "AdminRequestSubmitted" -> notice(e,null,null,"LICENSE_OWNER",NotificationType.ADMIN_REQUEST_SUBMITTED,
                    "Nueva solicitud de administrador",
                    "Una empresa solicitó incorporar un administrador. Revisa la solicitud en Usuarios.",
                    "solicitud",text(p,"requestId"),Map.of("requestId",text(p,"requestId"),"companyId",text(p,"companyId")));
            case "AdminRequestResolved" -> notice(e,id(p,"requesterUserId"),id(p,"companyId"),"COMPANY_ADMIN",NotificationType.ADMIN_REQUEST_RESOLVED,
                    "Solicitud revisada",
                    "La solicitud de administrador fue " + ("APPROVED".equals(text(p,"decision")) ? "aprobada." : "rechazada."),
                    "solicitud",text(p,"requestId"),Map.of("requestId",text(p,"requestId"),"decision",text(p,"decision")));
            case "TransferInitiated" -> notice(e,null,id(p,"toCompanyId"),"COMPANY_ADMIN",NotificationType.TRANSFER_RECEIVED,
                    "Transferencia pendiente de aceptación",
                    "Tu empresa recibió una transferencia. Revisa los lotes antes de aceptarla o rechazarla.",
                    "transferencia",text(p,"transferId"),transferData(p));
            case "TransferAccepted" -> notice(e,null,id(p,"fromCompanyId"),"COMPANY_ADMIN",NotificationType.TRANSFER_ACCEPTED,
                    "Transferencia aceptada","La empresa destinataria resolvió tu transferencia. Consulta su detalle.",
                    "transferencia",text(p,"transferId"),transferData(p));
            case "TransferRejected" -> notice(e,null,id(p,"fromCompanyId"),"COMPANY_ADMIN",NotificationType.TRANSFER_REJECTED,
                    "Transferencia rechazada","La empresa destinataria resolvió tu transferencia. Consulta su detalle.",
                    "transferencia",text(p,"transferId"),transferData(p));
            case "PossibleDerivedFailureDetected" -> derivedFailure(e,p);
            default -> { }
        }
    }

    /**
     * US35: <b>una alerta por falla y por empresa afectada</b> (no una por lote), con todos sus lotes en {@code data.batchIds}. Se genera por el
     * aviso de Traceability, sin importar qué haga Quality con cada lote después (retenerlo o {@code NO_ACTION}): quien administra la empresa
     * debe enterarse igual.
     */
    private void derivedFailure(BaseDomainEvent e, JsonNode p) {
        UUID failure = id(p,"failureId"), source = id(p,"sourceBatchId");
        if (failure == null || source == null || !p.path("affected").isArray())
            throw new MalformedEventException("PossibleDerivedFailureDetected sin failureId, sourceBatchId o affected",null);
        Map<UUID,Set<String>> byCompany = new LinkedHashMap<>();
        for (JsonNode affected : p.path("affected")) {
            UUID company = id(affected,"companyId");
            String batch = text(affected,"batchId");
            if (company == null || batch.isBlank()) continue;
            byCompany.computeIfAbsent(company,c -> new LinkedHashSet<>()).add(batch);
        }
        byCompany.forEach((company,batches) -> {
            List<String> ids = new ArrayList<>(batches);
            String message = ids.size() == 1
                    ? "Un lote de tu empresa podría estar afectado por una falla confirmada. Revisa su estado en Calidad."
                    : ids.size() + " lotes de tu empresa podrían estar afectados por una falla confirmada. Revisa su estado en Calidad.";
            notice(e,null,company,"COMPANY_ADMIN",NotificationType.POTENTIAL_DERIVED_FAILURE,
                    "Un lote requiere revisión preventiva",message,"lote",ids.get(0),
                    Map.of("failureId",failure.toString(),"sourceBatchId",source.toString(),"batchIds",ids));
        });
    }

    private void notice(BaseDomainEvent e, UUID user, UUID company, String role, NotificationType type,
                        String title, String message, String resource, String resourceId, Map<String,Object> data) {
        var notice = new NotificationNotice(e.eventId(),user,company,role,type,title,message,resource,resourceId,data,
                e.occurredAt().atOffset(ZoneOffset.UTC));
        store.save(notice);
        store.enqueueEmails(notice);
    }

    private static Map<String,Object> transferData(JsonNode p) {
        List<String> batches = new ArrayList<>();
        for (JsonNode batch : p.path("batchIds")) batches.add(batch.asText());
        return Map.of("transferId",text(p,"transferId"),"batchIds",batches,
                "fromCompanyId",text(p,"fromCompanyId"),"toCompanyId",text(p,"toCompanyId"));
    }
    private static String text(JsonNode p,String name) { return p.path(name).asText(""); }
    private static UUID id(JsonNode p,String name) { return p.path(name).isNull() || text(p,name).isBlank() ? null : UUID.fromString(text(p,name)); }
}
