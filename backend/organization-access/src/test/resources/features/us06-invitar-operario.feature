# language: es
Característica: US06 — Invitar operario
  Como administrador de empresa, deseo invitar a un operario para incorporarlo como usuario de mi empresa.

  Escenario: 1: Invitación exitosa
    Dado que existe capacidad de usuarios disponible
    Y el administrador completa los datos del operario
    Cuando confirma la invitación
    Entonces el sistema genera una invitación Pendiente
    Y la envía al correo registrado.

  Escenario: 2: Límite de usuarios alcanzado
    Dado que la suma de usuarios Activos e invitaciones Pendientes alcanza el límite contratado
    Cuando el administrador intenta invitar a otro operario
    Entonces el sistema no genera la invitación
    Y comunica que no existe capacidad disponible.
