# language: es
Característica: US11 — Configurar privacidad B2B
  Como administrador de empresa, deseo configurar la privacidad de los datos de mi empresa para controlar su visibilidad ante otras empresas.

  Escenario: 1: Configuración inicial
    Dado que existe un campo Configurable
    Cuando se habilita para la empresa
    Entonces el sistema lo establece como Privado por defecto.

  Escenario: 2: Compartir campo configurable
    Dado que existe un campo Configurable Privado
    Cuando el administrador lo establece como Compartido
    Entonces el sistema guarda la configuración
    Y permite visualizarlo desde otras empresas cuando corresponda.

  Escenario: 3: Volver privado un campo configurable
    Dado que existe un campo Configurable Compartido
    Cuando el administrador lo establece como Privado
    Entonces el sistema deja de mostrarlo a otras empresas.

  Escenario: 4: Información siempre compartida
    Dado que el administrador consulta Traceability ID, empresa responsable, relaciones de genealogía, tipo de material, fase operativa, estado de calidad, fecha de registro o indicador de producto final
    Cuando intenta establecerlo como Privado
    Entonces el sistema no permite modificar su visibilidad.

  Escenario: 5: Información siempre privada
    Dado que se consulta la identidad del operario, observaciones internas o datos comerciales de producto final
    Cuando otra empresa accede a la trazabilidad
    Entonces el sistema mantiene dicha información oculta.
