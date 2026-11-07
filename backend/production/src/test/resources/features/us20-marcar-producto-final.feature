# language: es
Característica: US20 — Marcar producto final
  Como operario, deseo marcar un lote de salida como producto final para indicar que terminó su recorrido productivo y registrar sus datos comerciales básicos.

  Escenario: 1: Marcar producto final
    Dado que se genera un lote de salida
    Cuando el operario lo marca como producto final
    Entonces el sistema registra dicha condición
    Y mantiene el lote dentro de su genealogía.

  Escenario: 2: Registrar datos comerciales
    Dado que un lote se marca como producto final
    Cuando el operario registra distribuidor o comprador, precio, moneda, fecha de salida comercial o referencia comercial
    Entonces el sistema conserva los datos ingresados como información Privada.

  Escenario: 3: Precio con moneda
    Dado que se registra un precio de venta
    Cuando intenta guardar sin indicar moneda
    Entonces el sistema solicita completar la moneda.

  Escenario: 4: Operar producto final
    Dado que un lote está marcado como producto final
    Cuando se intenta dividirlo, transformarlo, transferirlo o incorporarlo nuevamente al recorrido productivo
    Entonces el sistema no permite la operación.
