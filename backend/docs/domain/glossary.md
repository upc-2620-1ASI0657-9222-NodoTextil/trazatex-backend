# Lenguaje ubicuo y nombres en código

Fuente: informe, 4.1.2 (Ubiquitous Language), 3.2 (RF) y 4.1.13 (patrón State).
El código va en inglés (convención 5.2.3 del informe); la interfaz y los mensajes al usuario, en español. **Estos nombres son los oficiales: no inventes otros.**

## Roles

| Informe | Código |
|---|---|
| Dueño de Licencia | `LICENSE_OWNER` |
| Administrador de Empresa | `COMPANY_ADMIN` |
| Operario | `OPERATOR` |

## Fase operativa del lote (`BatchPhase`, la gobierna Production)

| Informe | Código | ¿Histórico? |
|---|---|---|
| Disponible | `AVAILABLE` | No |
| En transformación | `IN_TRANSFORMATION` | No |
| En transferencia | `IN_TRANSFER` | No |
| En control de calidad | `IN_QUALITY_CONTROL` | No |
| Procesado | `PROCESSED` | Sí |
| Dividido | `SPLIT` | Sí |
| Descartado | `DISCARDED` | Sí |

Nota: en el diagrama dinámico 2 del informe aparece la etiqueta `EN_EVALUACION`; corresponde a `IN_QUALITY_CONTROL`.

## Estado de calidad (`QualityStatus`, lo gobierna Quality)

| Informe | Código | ¿Retiene el lote? (`qualityHold`) |
|---|---|---|
| No revisado | `NOT_REVIEWED` | No |
| Conforme | `CONFORMING` | No |
| Con falla | `FAILED` | Sí |
| Por reevaluar | `PENDING_REEVALUATION` | Sí |
| Posible falla derivada | `POTENTIAL_DERIVED_FAILURE` | Sí |

Production no conoce las reglas de calidad: solo guarda `qualityHold` (lo fija Quality con el comando interno) y una copia de `qualityStatus` para mostrarla.

## Lote habilitado (RF-024)

Un lote está **habilitado** para dividir, transformar o transferir si: `phase = AVAILABLE` **y** `qualityHold = false` **y** `finalProduct = false` **y** la empresa responsable está `ACTIVE` (RF-003).
"Tener un control activo" equivale a `phase = IN_QUALITY_CONTROL`.

## Nombre del lote (`name`)

Texto libre y opcional que el operario da a un lote al registrarlo, en los hijos de una división y en las salidas de una transformación. **Solo lo ve la empresa dueña del lote** (`access = OWN`). No es un campo siempre compartido ni un `ConfigurableField`, así que ninguna configuración de privacidad lo muestra a otra empresa: nunca aparece en una vista `SHARED` de la genealogía. Decisión del usuario del 2026-11-03.

**Única excepción:** el detalle de una transferencia (`GET /api/transfers/{id}` de Production) muestra `name` a la empresa origen y a la destino, como dice `api.md`: lo ven las dos partes de la transferencia, aunque la destino aún no sea dueña o la origen ya no lo sea. Ninguna otra empresa ve ese detalle (404). Decisión del usuario del 2026-11-03.

## Otros enums

| Concepto | Código |
|---|---|
| Estado de empresa | `CompanyStatus`: `ACTIVE`, `INACTIVE` |
| Actividad textil | `TextileActivity`: `SPINNING` (hilandería), `WEAVING` (tejeduría), `DYEING` (tintorería), `FINISHING` (acabados), `CUTTING` (corte), `SEWING` (confección), `BRAND` (marca) |
| Estado de usuario | `UserStatus`: `ACTIVE`, `INACTIVE` |
| Estado de invitación | `InvitationStatus`: `PENDING`, `ACCEPTED`, `EXPIRED`, `CANCELLED` |
| Solicitud de administrador | `AdminRequestStatus`: `PENDING`, `APPROVED`, `REJECTED` |
| Visibilidad B2B | `Visibility`: `SHARED`, `PRIVATE` |
| Estado de maquinaria | `MachineStatus`: `ACTIVE`, `OUT_OF_SERVICE`, `INACTIVE` |
| Tipo de transformación | `TransformationType`: `SPINNING`, `WEAVING`, `DYEING` (estrategias nombradas en ADR-003; agregar otro tipo es el escenario de RNF-09) |
| Estado de transformación | `TransformationStatus`: `IN_PROGRESS`, `COMPLETED` |
| Estado de transferencia | `TransferStatus`: `PENDING`, `ACCEPTED`, `REJECTED` |
| Tipo de control | `ControlType`: `EVALUATION` (Evaluación), `REEVALUATION` (Reevaluación) |
| Estado del control | `ControlStatus`: `STARTING` (INICIANDO), `IN_PROGRESS` (EN_CURSO), `FINISHED`, `CANCELLED` |
| Resultado de prueba / control | `TestResult`: `CONFORMING`, `FAILED` |
| Estado de falla | `FailureStatus`: `OPEN` (generada, sin decisión final), `CONFIRMED`, `RESOLVED` |
| Retención pedida por un aviso de falla derivada (`ImpactedBatch.lockStatus`) | `LockStatus`: `PENDING` (falta aplicar el `HOLD`), `APPLIED` (el `HOLD` se aplicó y se confirmó), `NO_ACTION` (sin acción: lote histórico o ya retenido). Confirmados por el usuario el 2026-10-23 |
| Tipo de impacto (`ImpactedBatch.impactType`) | `ImpactType`: `ANCESTOR` (el lote afectado es el antecesor común), `DESCENDANT` (el antecesor común es el lote fallado), `RELATED_BRANCH` (cualquier otro). Se deduce de `commonAncestorBatchId`, no viaja en el evento |

## Términos

- **Traceability ID**: identificador interno único del lote; nunca cambia (ni al transferir).
- **Código QR**: código único y no reutilizable por lote; nunca cambia.
- **Merma** (`shrinkageKg`): pérdida natural; **se calcula**, nunca se ingresa: `entrada − salida − desperdicio`.
- **Desperdicio** (`wasteKg` + `wasteReason`): pérdida evitable; la ingresa el operario.
- **Mezcla**: transformación con más de un lote de entrada (no es una operación aparte).
- **Producto final**: lote de salida que terminó su recorrido; queda en la genealogía y ya no opera.
- **Empresa responsable**: la empresa que tiene el lote en ese momento (`companyId`).
- **Lote histórico**: `PROCESSED`, `SPLIT` o `DISCARDED`; solo sirve para reconstruir la genealogía.
