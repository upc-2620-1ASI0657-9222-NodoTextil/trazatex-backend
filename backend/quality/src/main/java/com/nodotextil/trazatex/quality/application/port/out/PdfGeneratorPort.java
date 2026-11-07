package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.application.dto.ControlReport;
import com.nodotextil.trazatex.quality.application.exception.PdfGenerationException;

/** ADR-011: el PDF se genera localmente (Thymeleaf + OpenHTMLtoPDF), sin servicios externos de PDF. */
public interface PdfGeneratorPort {

    /** @throws PdfGenerationException si no se pudo generar */
    byte[] render(ControlReport report);
}
