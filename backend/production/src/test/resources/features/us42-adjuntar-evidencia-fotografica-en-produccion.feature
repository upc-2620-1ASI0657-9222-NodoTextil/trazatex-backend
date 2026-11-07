# language: es
Característica: US42 — Adjuntar evidencia fotográfica en producción
  Como operario, deseo adjuntar fotografías a un lote o a una transformación registrada para dejar evidencia visual de su estado o del proceso realizado.

  Escenario: 1: Evidencia en lote
    Dado que existe un lote registrado
    Cuando el operario adjunta una o más fotografías
    Entonces el sistema las conserva asociadas al lote.

  Escenario: 2: Evidencia en transformación
    Dado que existe una transformación registrada
    Cuando el operario adjunta una o más fotografías
    Entonces el sistema las conserva asociadas a la transformación.

  Escenario: 3: Formato no soportado
    Dado que el archivo no corresponde a una imagen válida
    Cuando el operario intenta adjuntarlo
    Entonces el sistema lo rechaza y comunica los formatos permitidos.
