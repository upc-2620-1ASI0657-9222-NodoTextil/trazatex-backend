# language: es
Característica: US33 — Detectar posible falla derivada
  Como administrador de empresa, deseo que el sistema identifique lotes relacionados con una falla confirmada para conocer cuáles requieren revisión preventiva.

  Escenario: 1: Análisis de familia relacionada
    Dado que una Falla ha sido Confirmada
    Cuando el sistema analiza la genealogía hacia atrás
    Entonces utiliza los lotes históricos para localizar bifurcaciones y otros lotes relacionados de la misma familia.

  Escenario: 2: Descendientes relacionados
    Dado que existen divisiones dentro de las relaciones encontradas
    Cuando analiza sus descendientes
    Entonces revisa los lotes relacionados que continúan Disponibles.

  Escenario: 3: Posible falla derivada
    Dado que un lote relacionado continúa Disponible
    Cuando el sistema determina su posible relación con la falla
    Entonces cambia su calidad a Posible falla derivada
    Y lo bloquea preventivamente.

  Escenario: 4: Lote histórico
    Dado que un lote relacionado se encuentra Procesado, Dividido o Descartado
    Cuando analiza la genealogía
    Entonces lo utiliza únicamente para reconstruir las relaciones
    Y no lo devuelve a operación.
