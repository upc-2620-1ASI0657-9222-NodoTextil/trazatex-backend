# language: es
Característica: US18 — Finalizar transformación textil
  Como operario, deseo finalizar una transformación para registrar sus lotes resultantes y el balance de cantidades.

  Escenario: 1: Balance válido
    Dado que existe una transformación iniciada
    Y el operario registra uno o más lotes de salida y, cuando corresponda, la cantidad y motivo del desperdicio
    Cuando solicita finalizarla
    Entonces el sistema calcula la merma como Entrada total − Salida total − Desperdicio
    Y finaliza cuando la merma es mayor o igual a cero
    Y establece las entradas como Procesado y las salidas como Disponible.

  Escenario: 2: Balance inválido
    Dado que la salida total más el desperdicio supera la entrada total
    Cuando intenta finalizar
    Entonces el sistema no permite finalizarla
    Y comunica que las cantidades no son válidas.
