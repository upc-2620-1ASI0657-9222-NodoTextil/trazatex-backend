# language: es
Característica: US17 — Determinar composición de transformación
  Como operario, deseo que el sistema determine la composición resultante de una transformación para conservar correctamente la información de los materiales procesados.

  Escenario: 1: Un lote de entrada
    Dado que la transformación utiliza un solo lote
    Cuando se generan sus lotes de salida
    Entonces el sistema conserva la composición del lote de entrada.

  Escenario: 2: Varios lotes de entrada
    Dado que la transformación utiliza varios lotes
    Cuando el sistema procesa las cantidades aportadas
    Entonces calcula la composición resultante mediante promedio ponderado por kg
    Y considera la operación como una mezcla.
