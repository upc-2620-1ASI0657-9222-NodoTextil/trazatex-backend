# language: es
Característica: US10 — Gestionar maquinaria
  Como administrador de empresa, deseo registrar y gestionar las maquinarias de mi empresa para asociarlas con las transformaciones textiles.

  Escenario: 1: Registro de maquinaria
    Dado que el administrador completa código, nombre y tipo
    Cuando confirma el registro
    Entonces el sistema registra la maquinaria como Activa.

  Escenario: 2: Cambio de estado
    Dado que existe una maquinaria registrada
    Cuando cambia su estado
    Entonces el sistema permite establecerla como Activa, Fuera de servicio o Inactiva
    Y conserva su historial.

  Escenario: 3: Maquinaria no activa
    Dado que una maquinaria está Fuera de servicio o Inactiva
    Cuando un operario intenta utilizarla en una transformación
    Entonces el sistema no permite seleccionarla.
