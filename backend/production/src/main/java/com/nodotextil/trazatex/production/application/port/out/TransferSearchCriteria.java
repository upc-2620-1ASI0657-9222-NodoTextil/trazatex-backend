package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.TransferStatus;

import java.util.UUID;

public record TransferSearchCriteria(
        UUID companyId,
        Direction direction,
        TransferStatus status,
        int page,
        int size) {

    public TransferSearchCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser >= 0: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size debe ser >= 1: " + size);
        }
        size = Math.min(size, Page.MAX_SIZE);
    }

    public enum Direction {
        OUTGOING,
        INCOMING,
        ALL
    }
}
