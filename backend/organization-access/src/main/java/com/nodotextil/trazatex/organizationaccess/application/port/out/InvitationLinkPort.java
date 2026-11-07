package com.nodotextil.trazatex.organizationaccess.application.port.out;

/** Enlace de aceptación que viaja en {@code InvitationCreated.acceptUrl}. */
public interface InvitationLinkPort {

    String acceptUrl(String token);
}
