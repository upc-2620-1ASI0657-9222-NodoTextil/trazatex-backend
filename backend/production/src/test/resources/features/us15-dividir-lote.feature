# language: es
Característica: US15 — Dividir lote
  Como operario, deseo dividir un lote para generar lotes hijos que puedan continuar recorridos productivos independientes.

  Escenario: 1: División exitosa
    Dado que existe un lote habilitado
    Y el operario registra dos o más lotes hijos cuya cantidad total equivale a la cantidad del lote padre
    Cuando confirma la división
    Entonces el lote padre cambia a Dividido
    Y los hijos nacen Disponibles con nuevos Traceability ID y QR.

  Escenario: 2: Cantidades inválidas
    Dado que la suma de los hijos no equivale a la cantidad del padre
    Cuando intenta confirmar la división
    Entonces el sistema no realiza la operación
    Y comunica que las cantidades no coinciden.
