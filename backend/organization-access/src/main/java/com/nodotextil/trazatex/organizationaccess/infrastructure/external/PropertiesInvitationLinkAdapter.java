package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationLinkPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** {@code TRAZATEX_FRONTEND_URL} + {@code /invitations/<token>}. La ruta del frontend es una convención de este servicio. */
@Component
class PropertiesInvitationLinkAdapter implements InvitationLinkPort {

    private final String frontendUrl;

    PropertiesInvitationLinkAdapter(@Value("${trazatex.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @Override
    public String acceptUrl(String token) {
        return frontendUrl + "/invitations/" + token;
    }
}
