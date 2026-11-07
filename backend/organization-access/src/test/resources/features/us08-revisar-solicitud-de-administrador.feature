# language: es
Característica: US08 — Revisar solicitud de administrador
  Como dueño de licencia, deseo revisar las solicitudes de nuevos administradores para aprobar o rechazar su incorporación.

  Escenario: 1: Solicitud aprobada con capacidad
    Dado que existe una solicitud Pendiente
    Y la empresa posee capacidad de usuarios disponible
    Cuando el dueño de licencia la aprueba
    Entonces el sistema genera una invitación Pendiente para el nuevo administrador.

  Escenario: 2: Sin capacidad disponible
    Dado que existe una solicitud Pendiente
    Y la empresa alcanzó su límite de usuarios
    Cuando el dueño intenta aprobarla
    Entonces el sistema no genera la invitación
    Y comunica que no existe capacidad disponible.

  Escenario: 3: Solicitud rechazada
    Dado que existe una solicitud Pendiente
    Cuando el dueño de licencia la rechaza
    Entonces el sistema registra el rechazo
    Y no genera una invitación.
