package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuración de privacidad B2B de una empresa: un {@link PrivacySetting} por cada {@link ConfigurableField}, siempre
 * completa y {@code PRIVATE} por defecto (US11).
 *
 * <p>Lo que no es un campo configurable no se puede cambiar: ni lo siempre compartido (Traceability ID, empresa responsable,
 * relaciones de genealogía, tipo de material, fase operativa, estado de calidad, fecha de registro, indicador de producto
 * final) ni lo siempre privado (identidad del operario, observaciones internas, datos comerciales del producto final).
 * Cualquier nombre que no sea un {@code ConfigurableField} se rechaza con 400 y el cambio entero no se aplica.
 */
public final class CompanyPrivacy {

    private final UUID companyId;
    private final Map<ConfigurableField, PrivacySetting> settings = new EnumMap<>(ConfigurableField.class);

    private CompanyPrivacy(UUID companyId, Collection<PrivacySetting> stored) {
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        Arrays.stream(ConfigurableField.values())
                .forEach(field -> settings.put(field, PrivacySetting.privateByDefault(companyId, field)));
        stored.forEach(setting -> settings.put(setting.field(), setting));
    }

    public static CompanyPrivacy defaults(UUID companyId) {
        return new CompanyPrivacy(companyId, List.of());
    }

    /** Reconstruye desde lo guardado; un campo que falte se toma {@code PRIVATE}. */
    public static CompanyPrivacy restore(UUID companyId, Collection<PrivacySetting> stored) {
        return new CompanyPrivacy(companyId, stored);
    }

    /** Aplica los cambios (parciales) o ninguno. */
    public void update(Map<String, Visibility> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "No se indicó ningún campo a configurar.",
                    List.of("fields"));
        }
        List<String> rejected = new ArrayList<>();
        Map<ConfigurableField, Visibility> valid = new EnumMap<>(ConfigurableField.class);
        changes.forEach((name, visibility) -> {
            var field = ConfigurableField.fromName(name);
            if (field.isEmpty() || visibility == null) {
                rejected.add(name);
            } else {
                valid.put(field.get(), visibility);
            }
        });
        if (!rejected.isEmpty()) {
            throw new ValidationException(ErrorCodes.FIELD_NOT_CONFIGURABLE,
                    "Estos campos no son configurables o su visibilidad no es válida: " + String.join(", ", rejected)
                            + ". Solo se configuran los campos configurables; la información siempre compartida o siempre privada no se modifica.",
                    rejected);
        }
        valid.forEach((field, visibility) -> settings.put(field, settings.get(field).withVisibility(visibility)));
    }

    public Visibility visibilityOf(ConfigurableField field) {
        return settings.get(field).visibility();
    }

    /** Mapa completo campo → visibilidad (el payload de {@code CompanyPrivacyUpdated}). */
    public Map<ConfigurableField, Visibility> asMap() {
        Map<ConfigurableField, Visibility> map = new EnumMap<>(ConfigurableField.class);
        settings.forEach((field, setting) -> map.put(field, setting.visibility()));
        return map;
    }

    public List<PrivacySetting> settings() {
        return List.copyOf(settings.values());
    }

    public UUID companyId() {
        return companyId;
    }
}
