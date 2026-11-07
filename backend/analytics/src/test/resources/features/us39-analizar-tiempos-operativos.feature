# language: es
Característica: US39 — Analizar tiempos operativos
  Como administrador de empresa, deseo consultar los tiempos promedio de las operaciones para analizar la duración de los procesos de mi empresa.

  Escenario: 1: Consulta de tiempos
    Dado que existen operaciones finalizadas dentro del periodo seleccionado
    Cuando el administrador consulta sus indicadores
    Entonces el sistema muestra la duración promedio de transformaciones, transferencias y controles de calidad
    Y presenta los resultados del periodo.
