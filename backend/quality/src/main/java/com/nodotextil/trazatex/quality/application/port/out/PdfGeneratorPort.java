package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.application.dto.ControlReport;
import com.nodotextil.trazatex.quality.application.exception.PdfGenerationException;

public interface PdfGeneratorPort {

    byte[] render(ControlReport report);
}
