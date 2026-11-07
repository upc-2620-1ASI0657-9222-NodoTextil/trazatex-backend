# language: es
Característica: US36 — Consultar genealogía compartida
  Como usuario autorizado, deseo visualizar una genealogía entre empresas para conocer el recorrido de los lotes respetando la privacidad B2B.

  Escenario: 1: Información autorizada
    Dado que el usuario consulta una genealogía con información de varias empresas
    Cuando visualiza sus lotes y relaciones
    Entonces el sistema muestra los campos Siempre Compartidos
    Y los campos Configurables establecidos como Compartidos por cada empresa.

  Escenario: 2: Información privada
    Dado que existen campos Configurables Privados o información Siempre Privada
    Cuando un usuario de otra empresa visualiza la genealogía
    Entonces el sistema mantiene dichos datos ocultos.
