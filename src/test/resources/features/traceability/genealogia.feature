# language: es
Característica: Genealogía de lotes
  Como usuario de TrazaTex
  quiero que el sistema genere y consulte la genealogía de los lotes
  para conocer su origen y los lotes que pueden verse afectados por una falla

  Escenario: Relación por división (US29, escenario 1)
    Dado que Production publica la división del lote "L1" en los lotes "L2,L3"
    Cuando el sistema procesa los eventos de Production
    Entonces existe una relación DIVISION de "L1" hacia "L2"
    Y existe una relación DIVISION de "L1" hacia "L3"

  Escenario: Relación por transformación (US29, escenario 2)
    Dado que Production publica la transformación de los lotes "A,B" en los lotes "X,Y"
    Cuando el sistema procesa los eventos de Production
    Entonces el sistema registra 4 relaciones TRANSFORMATION

  Escenario: Un evento repetido no duplica relaciones
    Dado que Production publica la división del lote "L1" en los lotes "L2,L3"
    Y que Production publica la división del lote "L1" en los lotes "L2,L3"
    Cuando el sistema procesa los eventos de Production
    Entonces el grafo contiene exactamente 2 relaciones

  Escenario: Trazabilidad hacia atrás completa (US31)
    Dado que Production publica la división del lote "L1" en los lotes "L2,L3"
    Y que Production publica la transformación de los lotes "L3" en los lotes "L4"
    Cuando se consulta la trazabilidad hacia atrás del lote "L4"
    Entonces los antecesores del lote son "L1,L3"

  Escenario: Lote histórico en una falla confirmada (US33, escenario 4)
    Dado que Production publica la división del lote "L1" en los lotes "L2,L3"
    Y que Production publica la transformación de los lotes "L3" en los lotes "L4"
    Y que los lotes "L2,L4" están Disponibles
    Y que los lotes "L1,L3" están Procesados
    Cuando se confirma una falla en el lote "L4"
    Entonces los lotes con posible falla derivada son "L2"
    Y los lotes "L1,L3" no se devuelven a operación
