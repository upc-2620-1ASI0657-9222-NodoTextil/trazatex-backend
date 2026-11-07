package com.nodotextil.trazatex.traceability.domain.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Privacidad B2B de una empresa: qué campos configurables comparte. <b>Todo lo que no diga {@code SHARED} es
 * {@code PRIVATE}</b> (los configurables son privados por defecto): un campo ausente, un valor desconocido o una empresa
 * sin configuración nunca se comparten.
 */
public final class CompanyPrivacy {

    /** La privacidad leída de un mensaje: la tolerante deja pasar lo desconocido pero lo avisa. */
    public record Parsed(CompanyPrivacy privacy, List<String> warnings) {
    }

    public static final CompanyPrivacy ALL_PRIVATE = new CompanyPrivacy(new EnumMap<>(ConfigurableField.class));

    private final Map<ConfigurableField, Visibility> fields;

    private CompanyPrivacy(Map<ConfigurableField, Visibility> fields) {
        this.fields = fields;
    }

    public static CompanyPrivacy of(Map<ConfigurableField, Visibility> fields) {
        return new CompanyPrivacy(fields.isEmpty() ? new EnumMap<>(ConfigurableField.class) : new EnumMap<>(fields));
    }

    /**
     * Lee el mapa de {@code CompanyPrivacyUpdated}. Un campo que no es {@link ConfigurableField} se ignora; un valor que no es
     * {@code SHARED} ni {@code PRIVATE} se toma como {@code PRIVATE}. Ambos casos quedan en {@code warnings}.
     */
    public static Parsed fromRaw(Map<String, String> raw) {
        Map<ConfigurableField, Visibility> fields = new EnumMap<>(ConfigurableField.class);
        List<String> warnings = new ArrayList<>();
        raw.forEach((name, value) -> {
            ConfigurableField field = parseField(name);
            if (field == null) {
                warnings.add("campo desconocido '" + name + "': se ignora");
                return;
            }
            Visibility visibility = parseVisibility(value);
            if (visibility == null) {
                warnings.add("valor desconocido para " + field + ": se trata como PRIVATE");
                visibility = Visibility.PRIVATE;
            }
            fields.put(field, visibility);
        });
        return new Parsed(new CompanyPrivacy(fields), warnings);
    }

    public Visibility visibilityOf(ConfigurableField field) {
        return fields.getOrDefault(field, Visibility.PRIVATE);
    }

    public boolean isShared(ConfigurableField field) {
        return visibilityOf(field) == Visibility.SHARED;
    }

    /** Mapa completo (los 11 campos), para guardarlo: lo que no se configuró figura como {@code PRIVATE}. */
    public Map<ConfigurableField, Visibility> asMap() {
        Map<ConfigurableField, Visibility> all = new EnumMap<>(ConfigurableField.class);
        for (ConfigurableField field : ConfigurableField.values()) {
            all.put(field, visibilityOf(field));
        }
        return all;
    }

    private static ConfigurableField parseField(String name) {
        try {
            return name == null ? null : ConfigurableField.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Visibility parseVisibility(String value) {
        try {
            return value == null ? null : Visibility.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
