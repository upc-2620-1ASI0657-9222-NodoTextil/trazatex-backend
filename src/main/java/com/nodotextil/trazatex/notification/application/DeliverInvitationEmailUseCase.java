package com.nodotextil.trazatex.notification.application;
import com.nodotextil.trazatex.notification.application.port.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeliverInvitationEmailUseCase {
    private final EmailSenderPort emailSender;
    @Value("${app.public-base-url:http://localhost:8080}") private String publicBaseUrl;
    public DeliverInvitationEmailUseCase(EmailSenderPort emailSender) { this.emailSender = emailSender; }
    public void deliver(String email, String token, String role) {
        emailSender.send(email, "TrazaTex invitation", "Invitation role=" + role + "\n" + invitationLink(token));
    }
    private String invitationLink(String token) {
        String base = publicBaseUrl == null ? "http://localhost:8080" : publicBaseUrl;
        return base + "/api/invitations/" + token + "/accept";
    }
}