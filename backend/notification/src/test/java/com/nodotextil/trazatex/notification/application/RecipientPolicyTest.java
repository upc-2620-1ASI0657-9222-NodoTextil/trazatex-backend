package com.nodotextil.trazatex.notification.application;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RecipientPolicyTest {
    @Test void reservedDomainsAreAlwaysBlocked() {
        var policy = new RecipientPolicy("");
        for (String email : new String[]{"a@example.com", "a@pima.example.com", "a@example.org", "a@x.example.net", "a@empresa.invalid", "a@host.test", "a@x.example", "a@algo.localhost"})
            assertThat(policy.isBlocked(email)).as(email).isTrue();
        assertThat(policy.isBlocked("a@empresa.com")).isFalse();
        assertThat(policy.isBlocked("a@notexample.com")).isFalse();
    }

    @Test void configuredDomainsAreBlockedIgnoringCaseAndSpaces() {
        var policy = new RecipientPolicy(" UPC.PE , escuela.pe ,, ");
        assertThat(policy.isBlocked("carlos@upc.pe")).isTrue();
        assertThat(policy.isBlocked("MARTIN@UPC.PE")).isTrue();
        assertThat(policy.isBlocked("a@escuela.pe")).isTrue();
        assertThat(policy.isBlocked("a@otra.pe")).isFalse();
        assertThat(policy.isBlocked("a@sub.upc.pe")).isFalse();
    }
}
