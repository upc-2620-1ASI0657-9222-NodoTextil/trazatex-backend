# language: es
Característica: US03 — Gestionar estado de empresa
  Como dueño de licencia, deseo activar o inactivar una empresa para gestionar su participación en la cadena sin eliminar su historial.

  Escenario: 1: Inactivar empresa
    Dado que existe una empresa Activa
    Cuando el dueño de licencia decide inactivarla
    Entonces el sistema cambia su estado a Inactiva
    Y conserva toda su información e historial
    Y sus usuarios no pueden iniciar nuevas operaciones productivas.

  Escenario: 2: Activar empresa
    Dado que existe una empresa Inactiva
    Cuando el dueño de licencia decide activarla
    Entonces el sistema cambia su estado a Activa
    Y permite nuevamente las operaciones correspondientes a sus usuarios.
