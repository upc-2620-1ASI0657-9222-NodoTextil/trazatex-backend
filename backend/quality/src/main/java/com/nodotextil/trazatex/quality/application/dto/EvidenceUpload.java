package com.nodotextil.trazatex.quality.application.dto;

/** Un archivo recibido: el nombre que declaró el cliente (solo para mostrar) y sus bytes. */
public record EvidenceUpload(String originalFilename, byte[] content) {
}
