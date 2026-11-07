package com.nodotextil.trazatex.traceability.domain.model.view;

/**
 * Marca de todo lo que una consulta puede devolver. Solo {@code LineageAuthorizationPolicy} construye estos tipos (lo exige ArchUnit):
 * así ninguna respuesta sale sin pasar por la política de privacidad (ADR-006).
 */
public interface VisibleView {
}
