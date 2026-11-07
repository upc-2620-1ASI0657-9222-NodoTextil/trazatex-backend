# language: es
Característica: US34 — Revisar posible falla derivada
  Como administrador de empresa, deseo revisar un lote con posible falla derivada para confirmar o descartar que se encuentre afectado.

  Escenario: 1: Sin control previo
    Dado que el lote tiene calidad Posible falla derivada y nunca fue controlado
    Cuando inicia su revisión
    Entonces el sistema permite una Evaluación
    Y mantiene el lote bloqueado.

  Escenario: 2: Con control previo
    Dado que el lote posee controles anteriores
    Cuando inicia la revisión
    Entonces el sistema permite una Reevaluación
    Y mantiene el lote bloqueado.

  Escenario: 3: Resultado conforme
    Dado que todas las pruebas resultan Conformes
    Cuando finaliza el control
    Entonces el lote cambia a Conforme
    Y queda desbloqueado.

  Escenario: 4: Resultado con falla
    Dado que una o más pruebas presentan falla
    Cuando finaliza el control
    Entonces el lote cambia a Con falla
    Y genera su propia Falla.
