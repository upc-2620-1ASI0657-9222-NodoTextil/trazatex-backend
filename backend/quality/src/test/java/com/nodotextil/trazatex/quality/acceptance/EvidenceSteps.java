package com.nodotextil.trazatex.quality.acceptance;

import com.nodotextil.trazatex.quality.domain.SampleImages;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US43: la evidencia fotográfica de un control o de una falla (Cloudinary simulado con WireMock; lo subido queda asociado a su dueño). */
public class EvidenceSteps extends StepSupport {

    public EvidenceSteps(World world, QualityApi api, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, jdbc, json);
    }

    private List<QualityApi.Part> photosFor(String owner, UUID id) {
        return List.of(QualityApi.Part.field(owner, id), QualityApi.Part.file("frente.jpg", "image/jpeg", SampleImages.jpeg()),
                QualityApi.Part.file("lateral.png", "image/png", SampleImages.png()));
    }

    private void assertKeptAndListed(String ownerType, UUID owner, String listing) {
        assertThat(world.last.status()).as("adjuntar las fotografías: %s", world.last.raw()).isEqualTo(201);
        JsonNode created = world.last.field("items");
        assertThat(created).hasSize(2);
        assertThat(count("SELECT count(*) FROM evidence WHERE owner_type = ? AND owner_id = ?", ownerType, owner)).isEqualTo(2);
        List<String> kept = jdbc.sql("SELECT original_filename FROM evidence WHERE owner_type = :t AND owner_id = :o AND company_id = :c AND status = 'UPLOADED' ORDER BY created_at")
                .param("t", ownerType).param("o", owner).param("c", World.COMPANY_A).query(String.class).list();
        assertThat(kept).containsExactly("frente.jpg", "lateral.png");
        Reply listed = api.get(world.adminA, listing);
        assertThat(listed.status()).as("listar las evidencias: %s", listed.raw()).isEqualTo(200);
        assertThat(listed.field("items")).extracting(item -> item.path("originalFilename").asString()).containsExactly("frente.jpg", "lateral.png");
        assertThat(listed.field("items")).allMatch(item -> item.path("ownerId").asString().equals(owner.toString()) && !item.path("url").isNull());
    }

    // ---- esc. 1: en un control ----

    @Dado("^que existe un control de calidad registrado$")
    public void aQualityControlExists() {
        UUID control = controlInProgress(seedBatch("NOT_REVIEWED", false, false), "CONFORMING");
        world.pending = () -> api.postMultipart(world.adminA, "/api/quality/evidence", photosFor("controlId", control));
    }

    @Cuando("^el administrador adjunta una o más fotografías$")
    public void theAdministratorAttachesOneOrMorePhotos() {
        world.submit();
    }

    @Entonces("^el sistema las conserva asociadas al control\\.$")
    public void theSystemKeepsThemAssociatedWithTheControl() {
        assertKeptAndListed("CONTROL", world.control, "/api/quality/controls/" + world.control + "/evidence");
    }

    // ---- esc. 2: en una falla ----

    @Dado("^que existe una Falla registrada$")
    public void aFailureExists() {
        failedBatchWithOpenFailure();
        UUID failure = world.failure;
        world.pending = () -> api.postMultipart(world.adminA, "/api/quality/evidence", photosFor("failureId", failure));
    }

    @Entonces("^el sistema las conserva asociadas a la falla\\.$")
    public void theSystemKeepsThemAssociatedWithTheFailure() {
        assertKeptAndListed("FAILURE", world.failure, "/api/quality/failures/" + world.failure + "/evidence");
        assertThat(api.get(world.adminA, "/api/quality/controls/" + world.control + "/evidence").field("items")).as("lo de la falla no se mezcla con el control").isEmpty();
    }

    // ---- esc. 3: formato no soportado ----

    /**
     * El texto del informe dice «el operario», pero adjuntar es del administrador de empresa (la historia misma lo dice): el paso actúa como administrador y
     * una prueba aparte ({@code EvidenceApiIT}) comprueba que un OPERATOR recibe 403. Probable errata del informe (quality.md).
     */
    @Dado("^que el archivo no corresponde a una imagen válida$")
    public void theFileIsNotAValidImage() {
        UUID control = controlInProgress(seedBatch("NOT_REVIEWED", false, false), "CONFORMING");
        world.pending = () -> api.postMultipart(world.adminA, "/api/quality/evidence", List.of(QualityApi.Part.field("controlId", control),
                QualityApi.Part.file("informe.jpg", "image/jpeg", "esto es texto, no una imagen".getBytes(StandardCharsets.UTF_8))));
    }

    @Cuando("^el operario intenta adjuntarlo$")
    public void theOperatorTriesToAttachIt() {
        world.submit();
    }

    @Entonces("^el sistema lo rechaza y comunica los formatos permitidos\\.$")
    public void theSystemRejectsItAndStatesTheAllowedFormats() {
        assertThat(world.last.status()).as("adjuntar un archivo que no es imagen: %s", world.last.raw()).isEqualTo(400);
        assertThat(world.last.code()).isEqualTo("UNSUPPORTED_FILE_FORMAT");
        assertThat(world.last.message()).contains("JPEG", "PNG", "WebP");
        assertThat(world.last.field("details")).extracting(JsonNode::asString).containsExactly("informe.jpg");
        assertThat(count("SELECT count(*) FROM evidence")).as("no se conservó nada").isZero();
        assertThat(CloudinaryRequests.count()).as("ni se subió a Cloudinary").isZero();
    }

    /** Lo que llegó al Cloudinary simulado: se lee por el mismo servidor de pruebas que atiende al servicio. */
    private static final class CloudinaryRequests {
        static int count() {
            return com.nodotextil.trazatex.quality.integration.CloudinaryStub.uploads().size();
        }
    }

}
