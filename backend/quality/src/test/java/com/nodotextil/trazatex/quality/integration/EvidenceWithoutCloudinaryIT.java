package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.port.in.UploadPendingEvidenceUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.nodotextil.trazatex.quality.domain.SampleImages.jpeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin {@code CLOUDINARY_URL} Quality arranca igual: avisa con un WARN y todas las evidencias se aceptan y quedan PENDING en la cola
 * local hasta que se configure (ADR-007). Mientras tanto el job no consume intentos: no se intentó nada.
 */
// "test.context": contexto propio (si no, se reutilizaría uno ya creado y el WARN del arranque no saldría en esta clase)
@SpringBootTest(properties = {"trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false", "trazatex.cloudinary.url=",
        "trazatex.evidence.upload.enabled=false", "test.context=sin-cloudinary"})
@ExtendWith(OutputCaptureExtension.class)
class EvidenceWithoutCloudinaryIT extends FlowTestBase {

    @Autowired
    UploadPendingEvidenceUseCase pendingUploads;

    private org.springframework.test.web.servlet.ResultActions attach(UUID control, MockMultipartFile... files) throws Exception {
        var request = multipart("/api/quality/evidence").param("controlId", control.toString());
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        request.header("X-User-Id", ADMIN_OF_A.userId().toString()).header("X-User-Role", ADMIN_OF_A.role()).header("X-Company-Id", ADMIN_OF_A.companyId().toString());
        return mockMvc.perform(request);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    @Test
    void theServiceStartedWithAWarnAndEveryEvidenceIsQueued(CapturedOutput output) throws Exception {
        assertThat(output.getAll()).contains("CLOUDINARY_URL no está configurada").contains("PENDING");
        UUID control = startControl(seedAvailable());

        JsonNode created = read(attach(control, new MockMultipartFile("files", "a.jpg", "image/jpeg", jpeg()),
                new MockMultipartFile("files", "b.jpg", "image/jpeg", jpeg())).andExpect(status().isCreated())).path("items");

        assertThat(created).hasSize(2);
        assertThat(created).extracting(i -> i.path("status").asString()).containsOnly("PENDING");
        assertThat(created).allMatch(i -> i.path("url").isNull());
        assertThat(count("SELECT count(*) FROM evidence_upload_queue")).isEqualTo(2);
        JsonNode listed = read(getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/evidence").andExpect(status().isOk())).path("items");
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).path("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void theJobWaitsWithoutConsumingAttemptsUntilCloudinaryIsConfigured() throws Exception {
        UUID control = startControl(seedAvailable());
        attach(control, new MockMultipartFile("files", "a.jpg", "image/jpeg", jpeg())).andExpect(status().isCreated());

        for (int i = 0; i < 40; i++) {
            clock.advance(Duration.ofMinutes(6));
            pendingUploads.uploadDue();
        }

        assertThat(count("SELECT attempts FROM evidence")).as("no se intentó nunca: 0 intentos tras 40 pasadas").isZero();
        assertThat(jdbc.sql("SELECT status FROM evidence").query(String.class).single()).isEqualTo("PENDING");
        OffsetDateTime next = jdbc.sql("SELECT next_attempt_at FROM evidence").query((rs, n) -> rs.getObject(1, OffsetDateTime.class)).single();
        assertThat(Duration.between(clock.instant(), next.toInstant())).isEqualTo(Duration.ofMinutes(5));
        assertThat(jdbc.sql("SELECT last_error FROM evidence").query(String.class).single()).contains("no está configurado");
        assertThat(count("SELECT count(*) FROM evidence_upload_queue")).isEqualTo(1);
    }
}
