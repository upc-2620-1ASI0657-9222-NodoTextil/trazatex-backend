# language: es
Característica: US16 — Iniciar transformación textil
  Como operario, deseo iniciar una transformación textil para registrar los lotes y recursos que participan en el proceso productivo.

  Escenario: 1: Inicio exitoso
    Dado que existen uno o más lotes habilitados
    Y el operario selecciona una maquinaria Activa y un tipo de transformación
    Cuando confirma el inicio
    Entonces el sistema registra fecha y hora de inicio
    Y cambia los lotes a En transformación.

  Escenario: 2: Lote no habilitado
    Dado que uno de los lotes seleccionados no está habilitado
    Cuando intenta iniciar la transformación
    Entonces el sistema no permite iniciarla.

  Escenario: 3: Maquinaria no disponible
    Dado que la maquinaria está Fuera de servicio o Inactiva
    Cuando intenta seleccionarla
    Entonces el sistema no permite utilizarla.
