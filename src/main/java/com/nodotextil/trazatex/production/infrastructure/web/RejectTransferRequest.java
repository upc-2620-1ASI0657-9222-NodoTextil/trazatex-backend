package com.nodotextil.trazatex.production.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record RejectTransferRequest(@NotBlank String rejectionReason) {
}
