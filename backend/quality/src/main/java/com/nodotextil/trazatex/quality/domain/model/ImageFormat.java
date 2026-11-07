package com.nodotextil.trazatex.quality.domain.model;

import java.util.Optional;

/**
 * Formatos de imagen admitidos (US42): JPEG, PNG y WebP. El formato se decide por los <b>primeros bytes</b> del archivo, nunca por
 * la extensión ni por el {@code Content-Type} que declara el cliente.
 */
public enum ImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    public static final String ALLOWED_DESCRIPTION = "JPEG, PNG y WebP";

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final String contentType;
    private final String extension;

    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static ImageFormat ofContentType(String contentType) {
        for (ImageFormat format : values()) {
            if (format.contentType.equals(contentType)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Tipo de imagen no admitido: " + contentType);
    }

    public static Optional<ImageFormat> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        // WebP: contenedor RIFF ("RIFF" + tamaño de 4 bytes + "WEBP")
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
