package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.ControlReportDocument;

import java.util.UUID;

/** US44: el reporte PDF de un control finalizado de la propia empresa, generado al vuelo y sin almacenarse. */
public interface GenerateControlReportUseCase {

    /**
     * @throws com.nodotextil.trazatex.sharedkernel.exception.NotFoundException {@code CONTROL_NOT_FOUND}: no existe o es de otra empresa
     * @throws com.nodotextil.trazatex.sharedkernel.exception.ConflictException {@code CONTROL_NOT_FINISHED}
     * @throws com.nodotextil.trazatex.quality.application.exception.PdfGenerationException el generador falló: se puede reintentar
     */
    ControlReportDocument generate(UUID controlId, Actor actor);
}
