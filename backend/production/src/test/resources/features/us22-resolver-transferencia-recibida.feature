# language: es
Característica: US22 — Resolver transferencia recibida
  Como administrador de empresa, deseo aceptar o rechazar una transferencia recibida para controlar la incorporación de lotes a mi empresa.

  Escenario: 1: Transferencia aceptada
    Dado que existe una transferencia Pendiente
    Cuando el administrador la acepta
    Entonces el sistema registra fecha y hora de recepción
    Y cambia la responsabilidad a la empresa receptora
    Y establece los lotes como Disponible
    Y conserva sus Traceability ID y QR.

  Escenario: 2: Transferencia rechazada
    Dado que existe una transferencia Pendiente
    Y registra el motivo del rechazo
    Cuando confirma el rechazo
    Entonces el sistema registra fecha y hora
    Y devuelve los lotes a la empresa de origen como Disponible
    Y alerta a sus administradores.
