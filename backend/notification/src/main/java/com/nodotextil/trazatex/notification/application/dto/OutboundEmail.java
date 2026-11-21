package com.nodotextil.trazatex.notification.application.dto;

public record OutboundEmail(String to,String subject,String body) {
    @Override public String toString() { return "OutboundEmail[to=" + to + ", subject=" + subject + ", body=***]"; }
}
