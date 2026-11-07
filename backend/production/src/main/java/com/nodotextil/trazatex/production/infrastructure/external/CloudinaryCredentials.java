package com.nodotextil.trazatex.production.infrastructure.external;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lo que trae {@code CLOUDINARY_URL}. {@link #toString()} no incluye el secreto. */
public record CloudinaryCredentials(String cloudName, String apiKey, String apiSecret) {

    private static final Pattern FORMAT = Pattern.compile("^cloudinary://([^:@/\\s]+):([^@/\\s]+)@([^/?#\\s]+)$");

    /** @return las credenciales, o {@code null} si la variable está vacía (Cloudinary sin configurar) */
    public static CloudinaryCredentials parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = FORMAT.matcher(url.trim());
        if (!matcher.matches()) {
            // Sin repetir el valor: contiene el secreto
            throw new IllegalStateException("CLOUDINARY_URL no tiene el formato cloudinary://API_KEY:API_SECRET@CLOUD_NAME");
        }
        return new CloudinaryCredentials(matcher.group(3), matcher.group(1), matcher.group(2));
    }

    @Override
    public String toString() {
        return "CloudinaryCredentials[cloudName=" + cloudName + ", apiKey=" + apiKey + ", apiSecret=****]";
    }
}
