# language: es
Característica: US09 — Gestionar estado de usuario
  Como administrador de empresa, deseo gestionar el estado de los usuarios de mi empresa para controlar su acceso sin eliminar su historial.

  Escenario: 1: Inactivar usuario
    Dado que existe un usuario Activo
    Cuando el administrador lo inactiva
    Entonces cambia a Inactivo
    Y conserva su historial
    Y no puede autenticarse ni realizar operaciones.

  Escenario: 2: Reactivar usuario
    Dado que existe un usuario Inactivo
    Y existe capacidad disponible
    Cuando el administrador lo reactiva
    Entonces cambia a Activo
    Y recupera el acceso según su rol.

  Escenario: 3: Reactivación sin capacidad
    Dado que no existe capacidad disponible
    Cuando se intenta reactivar al usuario
    Entonces el sistema no permite la reactivación.
