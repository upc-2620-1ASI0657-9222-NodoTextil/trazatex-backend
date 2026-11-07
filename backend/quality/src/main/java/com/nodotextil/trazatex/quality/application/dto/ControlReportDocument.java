package com.nodotextil.trazatex.quality.application.dto;

/** El PDF en memoria (nunca se guarda) y el nombre con el que se descarga. */
public record ControlReportDocument(byte[] pdf, String filename) {
}
