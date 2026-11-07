package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.util.List;
import java.util.UUID;

public record StartTransformationCommand(TransformationType type, UUID machineId, List<UUID> batchIds) {
}
