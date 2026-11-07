# language: es
Característica: US41 — Cancelar invitación
  Como usuario que emitió una invitación, deseo cancelarla mientras se encuentre Pendiente para impedir que pueda utilizarse cuando la incorporación ya no corresponda.

  Escenario: 1: Cancelación exitosa
    Dado que existe una invitación Pendiente emitida por el usuario
    Cuando decide cancelarla
    Entonces el sistema cambia su estado a Cancelada
    Y la invitación ya no puede utilizarse para crear una cuenta.

  Escenario: 2: Invitación no cancelable
    Dado que una invitación se encuentra Aceptada, Expirada o Cancelada
    Cuando el usuario intenta cancelarla
    Entonces el sistema no modifica su estado.
