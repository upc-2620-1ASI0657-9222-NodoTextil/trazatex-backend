package com.nodotextil.trazatex.sharedkernel.exception;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrazatexExceptionTest {

    @Test
    void cadaExcepcionBaseTieneSuCodigoPorDefecto() {
        assertThat(new NotFoundException("m").code()).isEqualTo("NOT_FOUND");
        assertThat(new ConflictException("m").code()).isEqualTo("CONFLICT");
        assertThat(new ValidationException("m").code()).isEqualTo("VALIDATION_ERROR");
        assertThat(new ForbiddenException("m").code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void permitenUnCodigoPropio() {
        TrazatexException e = new ConflictException("BATCH_IN_TRANSFORMATION", "El lote está en transformación");

        assertThat(e.code()).isEqualTo("BATCH_IN_TRANSFORMATION");
        assertThat(e.getMessage()).isEqualTo("El lote está en transformación");
    }

    @Test
    void sonExcepcionesNoComprobadasDeLaMismaBase() {
        assertThat(List.<TrazatexException>of(new NotFoundException("m"), new ConflictException("m"),
                new ValidationException("m"), new ForbiddenException("m")))
                .allSatisfy(e -> assertThat(e).isInstanceOf(RuntimeException.class));
    }

    @Test
    void validationLlevaDetallesInmutables() {
        List<String> source = new ArrayList<>(List.of("name: obligatorio"));
        ValidationException e = new ValidationException("Datos inválidos", source);
        source.add("otro");

        assertThat(e.details()).containsExactly("name: obligatorio");
        assertThatThrownBy(() -> e.details().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new NotFoundException("m").details()).isEmpty();
    }
}
