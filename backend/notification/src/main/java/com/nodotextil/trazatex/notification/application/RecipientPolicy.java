package com.nodotextil.trazatex.notification.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** A qué direcciones nunca se envía correo real: dominios reservados (RFC 2606) y los que la operación bloquee ({@code TRAZATEX_MAIL_BLOCKED_DOMAINS}). */
public class RecipientPolicy {
    private static final Pattern RESERVED = Pattern.compile("((@|\\.)example\\.(com|org|net)$|\\.(test|example|invalid|localhost)$)");
    private final Set<String> blockedDomains;

    public RecipientPolicy(String blockedDomainsCsv) {
        this.blockedDomains = Arrays.stream((blockedDomainsCsv == null ? "" : blockedDomainsCsv).split(","))
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT)).filter(domain -> !domain.isBlank()).collect(Collectors.toSet());
    }

    public boolean isBlocked(String email) {
        String address = email.toLowerCase(Locale.ROOT);
        int at = address.indexOf('@');
        return RESERVED.matcher(address).find() || (at >= 0 && blockedDomains.contains(address.substring(at + 1)));
    }
}
