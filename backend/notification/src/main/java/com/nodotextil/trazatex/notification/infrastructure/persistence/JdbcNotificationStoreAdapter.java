package com.nodotextil.trazatex.notification.infrastructure.persistence;

import com.nodotextil.trazatex.notification.application.dto.NotificationPage;
import com.nodotextil.trazatex.notification.application.dto.NotificationView;
import com.nodotextil.trazatex.notification.application.port.out.NotificationStorePort;
import com.nodotextil.trazatex.notification.domain.model.NotificationNotice;
import com.nodotextil.trazatex.notification.domain.model.NotificationRecipient;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNotificationStoreAdapter implements NotificationStorePort {
    /** Las notificaciones de este destinatario: su rol y empresa, y las dirigidas a él o a todos. */
    private static final String SCOPE = """
            n.recipient_role = :role AND n.company_id IS NOT DISTINCT FROM CAST(:company AS uuid)
            AND (n.recipient_user_id IS NULL OR n.recipient_user_id = :user)
            """;
    private static final String FROM = "FROM notification n LEFT JOIN notification_read r ON r.notification_id=n.id AND r.user_id=:user WHERE ";
    private static final String FILTERS = """
            AND (CAST(:unread AS boolean) IS NULL OR (r.user_id IS NULL) = CAST(:unread AS boolean))
            AND (CAST(:type AS text) IS NULL OR n.type = CAST(:type AS text))
            """;
    private static final String SELECT = """
            SELECT n.id, n.type, n.title, n.message, n.resource_type, n.resource_id, n.data::text AS data,
                n.company_id, n.recipient_role,
                to_char(n.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at,
                (r.user_id IS NOT NULL) AS read
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    public JdbcNotificationStoreAdapter(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }

    @Override public boolean claim(UUID event,String type) {
        return jdbc.sql("INSERT INTO processed_events(event_id,event_type) VALUES(:id,:type) ON CONFLICT DO NOTHING")
                .param("id",event).param("type",type).update()!=0;
    }
    @Override public void projectUser(UUID user,UUID company,String role,String email,String status,OffsetDateTime at) {
        jdbc.sql("""
                INSERT INTO user_projection(user_id,company_id,role,email,status,updated_at,registered_at)
                VALUES(:id,:company,:role,:email,:status,:at,:at)
                ON CONFLICT(user_id) DO UPDATE SET company_id=excluded.company_id,role=excluded.role,
                    email=excluded.email,status=excluded.status,updated_at=excluded.updated_at
                WHERE excluded.updated_at >= user_projection.updated_at
                """).param("id",user).param("company",company,Types.OTHER).param("role",role)
                .param("email",email).param("status",status).param("at",at).update();
    }
    @Override public void save(NotificationNotice n) {
        jdbc.sql("""
                INSERT INTO notification(id,event_id,recipient_user_id,company_id,recipient_role,type,title,message,resource_type,resource_id,data,created_at)
                VALUES(:id,:event,:user,:company,:role,:type,:title,:message,:resource,:resourceId,CAST(:data AS jsonb),:at) ON CONFLICT DO NOTHING
                """).param("id",UUID.randomUUID()).param("event",n.eventId()).param("user",n.userId(),Types.OTHER)
                .param("company",n.companyId(),Types.OTHER).param("role",n.role()).param("type",n.type().name())
                .param("title",n.title()).param("message",n.message()).param("resource",n.resource())
                .param("resourceId",n.resourceId()).param("data",mapper.writeValueAsString(n.data()))
                .param("at",n.occurredAt()).update();
    }
    @Override public int enqueueEmails(NotificationNotice n) {
        return jdbc.sql("""
                INSERT INTO deferred_email(id,event_id,recipient,subject,body)
                SELECT gen_random_uuid(),:event,u.email,:subject,:body FROM user_projection u
                WHERE u.role=:role AND u.company_id IS NOT DISTINCT FROM CAST(:company AS uuid) AND u.status='ACTIVE' AND btrim(u.email) <> ''
                    AND (CAST(:user AS uuid) IS NULL OR u.user_id=CAST(:user AS uuid))
                ON CONFLICT(event_id,recipient) DO NOTHING
                """).param("event",n.eventId()).param("subject",n.title()).param("body",n.message()).param("role",n.role())
                .param("company",n.companyId(),Types.OTHER).param("user",n.userId(),Types.OTHER).update();
    }
    @Override public void enqueue(UUID event,String email,String subject,String body,Instant expires) {
        jdbc.sql("""
                INSERT INTO deferred_email(id,event_id,recipient,subject,body,expires_at)
                VALUES(:id,:event,:email,:subject,:body,:expires) ON CONFLICT(event_id,recipient) DO NOTHING
                """).param("id",UUID.randomUUID()).param("event",event).param("email",email).param("subject",subject)
                .param("body",body).param("expires",expires==null?null:expires.atOffset(java.time.ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE).update();
    }
    @Override public NotificationPage list(NotificationRecipient a,Boolean unread,String type,int page,int size) {
        long total = filtered("SELECT count(*) " + FROM + SCOPE + FILTERS,a,unread,type).query(Long.class).single();
        List<NotificationView> items = filtered(SELECT + FROM + SCOPE + FILTERS + " ORDER BY n.created_at DESC, n.id LIMIT :limit OFFSET :offset",a,unread,type)
                .param("limit",size).param("offset",(long)page*size).query(this::view).list();
        return NotificationPage.of(items,page,size,total);
    }
    @Override public Optional<NotificationView> find(UUID id,NotificationRecipient a) {
        return scope(SELECT + FROM + SCOPE + " AND n.id=:id",a).param("id",id).query(this::view).optional();
    }
    @Override public long unreadCount(NotificationRecipient a) {
        return scope("SELECT count(*) " + FROM + SCOPE + " AND r.user_id IS NULL",a).query(Long.class).single();
    }
    @Override public void setRead(UUID id,boolean value,NotificationRecipient a) {
        if (value) jdbc.sql("INSERT INTO notification_read(notification_id,user_id) VALUES(:id,:user) ON CONFLICT DO NOTHING")
                .param("id",id).param("user",a.userId()).update();
        else jdbc.sql("DELETE FROM notification_read WHERE notification_id=:id AND user_id=:user")
                .param("id",id).param("user",a.userId()).update();
    }
    @Override public void readAll(NotificationRecipient a) {
        scope("INSERT INTO notification_read(notification_id,user_id) SELECT n.id,:user FROM notification n WHERE " + SCOPE
                + " ON CONFLICT DO NOTHING",a).update();
    }
    private JdbcClient.StatementSpec filtered(String sql,NotificationRecipient a,Boolean unread,String type) {
        return scope(sql,a).param("unread",unread,Types.BOOLEAN).param("type",type,Types.VARCHAR);
    }
    private JdbcClient.StatementSpec scope(String sql,NotificationRecipient a) {
        return jdbc.sql(sql).param("user",a.userId()).param("role",a.role()).param("company",a.companyId(),Types.OTHER);
    }
    @SuppressWarnings("unchecked")
    private NotificationView view(ResultSet rs,int row) throws SQLException {
        return new NotificationView(rs.getObject("id",UUID.class),rs.getString("type"),rs.getString("title"),rs.getString("message"),
                rs.getString("resource_type"),rs.getString("resource_id"),mapper.readValue(rs.getString("data"),Map.class),
                rs.getObject("company_id",UUID.class),rs.getString("recipient_role"),rs.getString("created_at"),rs.getBoolean("read"));
    }
}
