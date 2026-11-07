# language: es
Característica: US23 — Iniciar control de calidad
  Como administrador de empresa, deseo iniciar un control de calidad sobre un lote para evaluar o reevaluar sus características según su situación.

  Escenario: 1: Evaluación inicial
    Dado que el lote tiene calidad No revisado
    Cuando el administrador inicia un control
    Entonces el sistema crea una Evaluación
    Y registra fecha y hora de inicio
    Y cambia la fase a En control de calidad.

  Escenario: 2: Reevaluación
    Dado que el lote tiene calidad Por reevaluar
    Cuando el administrador inicia un control
    Entonces el sistema crea una Reevaluación.

  Escenario: 3: Control activo existente
    Dado que el lote posee un control activo
    Cuando intenta iniciar otro
    Entonces el sistema no permite iniciarlo.
