# language: es
Característica: US04 — Aceptar invitación
  Como usuario invitado, deseo aceptar una invitación vigente para crear mi cuenta y quedar asociado a la empresa y rol asignados.

  Escenario: 1: Invitación válida
    Dado que el usuario posee una invitación Pendiente y vigente
    Y completa sus nombres, apellidos, contraseña y confirmación de contraseña
    Cuando acepta la invitación
    Entonces el sistema crea y activa su cuenta
    Y cambia la invitación a Aceptada.

  Escenario: 2: Invitación expirada
    Dado que han transcurrido 7 días desde el envío de una invitación Pendiente
    Cuando el usuario intenta aceptarla
    Entonces el sistema no crea la cuenta
    Y la invitación permanece Expirada.

  Escenario: 3: Invitación cancelada
    Dado que la invitación se encuentra Cancelada
    Cuando el usuario intenta aceptarla
    Entonces el sistema no permite utilizarla.

  Escenario: 4: Invitación ya utilizada
    Dado que la invitación se encuentra Aceptada
    Cuando se intenta utilizar nuevamente
    Entonces el sistema no permite crear otra cuenta con ella.

  Escenario: 5: Contraseña comprometida
    Dado que la contraseña elegida se encuentra en bases de datos de credenciales filtradas
    Cuando el usuario intenta aceptar la invitación
    Entonces el sistema rechaza la contraseña
    Y solicita una diferente.
