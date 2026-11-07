package com.nodotextil.trazatex.notification.infrastructure.persistence;

import com.nodotextil.trazatex.notification.application.dto.DueEmail;
import com.nodotextil.trazatex.notification.application.port.out.DeferredEmailStorePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Todo estado final deja {@code body = ''} (también lo exige un CHECK de la tabla): un correo de invitación lleva un token de un solo uso. */
@Repository
public class JdbcDeferredEmailStoreAdapter implements DeferredEmailStorePort {
    private final JdbcClient jdbc;
    public JdbcDeferredEmailStoreAdapter(JdbcClient jdbc) { this.jdbc=jdbc; }

    @Override public int expireDue() {
        return jdbc.sql("UPDATE deferred_email SET status='EXPIRED',body='',last_error='La invitación ya venció.' WHERE status='PENDING' AND expires_at <= now()").update();
    }
    @Override public List<DueEmail> claimDue(int limit,Duration lease) {
        return jdbc.sql("""
                WITH due AS (SELECT id FROM deferred_email WHERE status='PENDING' AND next_attempt_at <= now()
                    ORDER BY next_attempt_at LIMIT :limit FOR UPDATE SKIP LOCKED)
                UPDATE deferred_email e SET next_attempt_at=now()+(:lease * interval '1 second')
                FROM due WHERE e.id=due.id RETURNING e.id,e.recipient,e.subject,e.body,e.attempts
                """).param("limit",limit).param("lease",lease.toSeconds())
                .query((rs,row)->new DueEmail(rs.getObject("id",UUID.class),rs.getString("recipient"),rs.getString("subject"),
                        rs.getString("body"),rs.getInt("attempts"))).list();
    }
    @Override public void markSent(UUID id) {
        jdbc.sql("UPDATE deferred_email SET status='SENT',accepted_at=now(),last_error=NULL,body='' WHERE id=:id AND status='PENDING'")
                .param("id",id).update();
    }
    @Override public void markUndeliverable(UUID id,String reason) {
        jdbc.sql("UPDATE deferred_email SET status='UNDELIVERABLE',last_error=:reason,body='' WHERE id=:id AND status='PENDING'")
                .param("id",id).param("reason",reason).update();
    }
    @Override public void markRetry(UUID id,Instant next,String reason) {
        jdbc.sql("UPDATE deferred_email SET attempts=attempts+1,next_attempt_at=:next,last_error=:reason WHERE id=:id AND status='PENDING'")
                .param("id",id).param("next",next.atOffset(ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE).param("reason",reason).update();
    }
    @Override public void markFailed(UUID id,String reason) {
        jdbc.sql("UPDATE deferred_email SET status='FAILED',attempts=attempts+1,last_error=:reason,body='' WHERE id=:id AND status='PENDING'")
                .param("id",id).param("reason",reason).update();
    }
    @Override public void release(UUID id,Instant next) {
        jdbc.sql("UPDATE deferred_email SET next_attempt_at=:next WHERE id=:id AND status='PENDING'")
                .param("id",id).param("next",next.atOffset(ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE).update();
    }
}
