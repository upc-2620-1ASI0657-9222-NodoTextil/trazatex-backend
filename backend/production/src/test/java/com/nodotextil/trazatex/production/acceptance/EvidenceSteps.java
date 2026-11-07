package com.nodotextil.trazatex.production.acceptance;

import com.nodotextil.trazatex.production.domain.SampleImages;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** US42: fotografías de un lote o de una transformación. Cloudinary es WireMock; el resto, el servicio real. */
public class EvidenceSteps extends StepSupport {

    public EvidenceSteps(World world, ProductionApi api, Outbox outbox, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, outbox, jdbc, json);
    }

    private String evidencePath() {
        return (String) world.notes.get("evidencePath");
    }

    @Dado("^que existe un lote registrado$")
    public void aBatchExists() {
        world.batch = registerLot(25);
        world.notes.put("evidencePath", "/api/batches/" + id(world.batch) + "/evidence");
    }

    @Dado("^que existe una transformación registrada$")
    public void aTransformationExists() {
        world.transformation = startTransformation("SPINNING", registerMachine(), registerLot(25));
        world.notes.put("evidencePath", "/api/transformations/" + id(world.transformation) + "/evidence");
    }

    @Cuando("^el operario adjunta una o más fotografías$")
    public void theOperatorAttachesOneOrMorePhotos() {
        world.send(() -> api.multipart(world.operatorA, evidencePath(), List.of(
                new ProductionApi.Part("files", "frente.jpg", "image/jpeg", SampleImages.jpeg()),
                new ProductionApi.Part("files", "lateral.png", "image/png", SampleImages.png()))));
    }

    private void assertKeptAssociatedTo(String ownerType, JsonNode owner) {
        assertThat(world.last.status()).as("adjuntar: %s", world.last.raw()).isEqualTo(201);
        JsonNode created = world.last.field("items");
        assertThat(created).hasSize(2);
        List<String> ids = new ArrayList<>();
        for (JsonNode item : created) {
            ids.add(item.path("id").asString());
            assertThat(item.path("ownerType").asString()).isEqualTo(ownerType);
            assertThat(item.path("ownerId").asString()).isEqualTo(owner.path("id").asString());
            assertThat(item.path("status").asString()).isEqualTo("UPLOADED");
            assertThat(item.path("url").asString()).startsWith("https://res.cloudinary.com/").endsWith(item.path("id").asString() + ".jpg");
            assertThat(item.path("uploadedBy").asString()).isEqualTo(world.operatorA.userId().toString());
        }
        // las conserva: el listado de su dueño las devuelve, y la base las tiene asociadas a él
        Reply listed = api.get(world.operatorA, evidencePath());
        assertThat(listed.status()).isEqualTo(200);
        List<String> listedIds = new ArrayList<>();
        listed.field("items").forEach(item -> listedIds.add(item.path("id").asString()));
        assertThat(listedIds).containsExactlyElementsOf(ids);
        assertThat(count("SELECT count(*) FROM evidence WHERE owner_type = ? AND owner_id = ?", ownerType, id(owner))).isEqualTo(2);
        // y llegaron a Cloudinary con su id como public_id (así reintentar no duplica)
        assertThat(CloudinaryStub.uploads()).extracting(request -> request.getParts().stream().filter(part -> part.getName().equals("public_id"))
                .findFirst().orElseThrow().getBody().asString()).containsExactlyElementsOf(ids);
        // cada empresa ve solo las suyas
        assertThat(api.get(world.operatorB, evidencePath()).status()).isEqualTo(404);
    }

    @Entonces("^el sistema las conserva asociadas al lote\\.$")
    public void theSystemKeepsThemAssociatedToTheBatch() {
        assertKeptAssociatedTo("BATCH", world.batch);
    }

    @Entonces("^el sistema las conserva asociadas a la transformación\\.$")
    public void theSystemKeepsThemAssociatedToTheTransformation() {
        assertKeptAssociatedTo("TRANSFORMATION", world.transformation);
    }

    @Dado("^que el archivo no corresponde a una imagen válida$")
    public void theFileIsNotAValidImage() {
        world.batch = registerLot(25);
        world.notes.put("evidencePath", "/api/batches/" + id(world.batch) + "/evidence");
        // se llama como una foto y se declara como una imagen, pero sus bytes son de un texto
        world.pending = () -> api.multipart(world.operatorA, evidencePath(), List.of(
                new ProductionApi.Part("files", "foto.jpg", "image/jpeg", "esto es un texto, no una fotografía".getBytes(StandardCharsets.UTF_8))));
    }

    @Entonces("^el sistema lo rechaza y comunica los formatos permitidos\\.$")
    public void theSystemRejectsItAndStatesTheAllowedFormats() {
        assertThat(world.last.status()).as("archivo no válido: %s", world.last.raw()).isEqualTo(400);
        assertThat(world.last.code()).isEqualTo("UNSUPPORTED_FILE_FORMAT");
        assertThat(world.last.message()).contains("JPEG, PNG y WebP");
        assertThat(world.last.field("details")).extracting(JsonNode::asString).containsExactly("foto.jpg");
        assertThat(count("SELECT count(*) FROM evidence")).as("no se guardó nada").isZero();
        assertThat(count("SELECT count(*) FROM evidence_upload_queue")).isZero();
        assertThat(CloudinaryStub.uploads()).as("no llegó a Cloudinary").isEmpty();
        assertThat(api.get(world.operatorA, evidencePath()).field("items")).isEmpty();
    }
}
