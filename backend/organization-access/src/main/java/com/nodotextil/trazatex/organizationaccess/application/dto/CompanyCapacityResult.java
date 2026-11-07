package com.nodotextil.trazatex.organizationaccess.application.dto;

/**
 * El cupo de usuarios de una empresa (RF-013): {@code limit} es el máximo por empresa de la licencia; {@code activeUsers}, los usuarios ACTIVE; y
 * {@code pendingInvitations}, las invitaciones PENDING vigentes. Las dos últimas suman el cupo usado, igual que al invitar.
 */
public record CompanyCapacityResult(int limit, long activeUsers, long pendingInvitations) {
}
