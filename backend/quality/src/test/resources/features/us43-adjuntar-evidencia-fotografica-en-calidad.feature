# language: es
Característica: US43 — Adjuntar evidencia fotográfica en calidad
  Como administrador de empresa, deseo adjuntar fotografías a un control de calidad o a una falla registrada para dejar evidencia visual de los resultados o del problema detectado.

  Escenario: 1: Evidencia en control de calidad
    Dado que existe un control de calidad registrado
    Cuando el administrador adjunta una o más fotografías
    Entonces el sistema las conserva asociadas al control.

  Escenario: 2: Evidencia en falla
    Dado que existe una Falla registrada
    Cuando el administrador adjunta una o más fotografías
    Entonces el sistema las conserva asociadas a la falla.

  Escenario: 3: Formato no soportado
    Dado que el archivo no corresponde a una imagen válida
    Cuando el operario intenta adjuntarlo
    Entonces el sistema lo rechaza y comunica los formatos permitidos.
