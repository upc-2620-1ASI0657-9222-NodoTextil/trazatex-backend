# Decisiones de arquitectura (resumen)

Versión completa: informe, sección 4.3.4 (Iteración 4) y anteriores. Si una decisión aquí choca con lo que pide una tarea, **gana el ADR**; avisa antes de desviarte.

| ADR | Decisión | Consecuencia para el código |
|---|---|---|
| ADR-001 | Seis microservicios por Bounded Context + Gateway; comunicación por eventos; hexagonal en cada uno | Un módulo Maven por servicio; nada de paquetes compartidos con lógica de negocio |
| ADR-002 | Neo4j para la genealogía | Traceability usa Spring Data Neo4j; consultas Cypher acotadas a 10 niveles |
| ADR-003 | Strategy + Factory Method para las transformaciones | `TransformationStrategy` por tipo (`SpinningStrategy`, `WeavingStrategy`, `DyeingStrategy`) y `TransformationFactory`; la invariante de balance vive en `Transformation.finalize()` y ninguna estrategia la relaja |
| ADR-004 | Kafka con Transactional Outbox, consumidores idempotentes y DLT | Ver `communication.md`. Nunca `kafkaTemplate.send` dentro del caso de uso |
| ADR-005 | JWT RS256 emitido por Organization & Access, JWKS, validación en el Gateway; token de 8 h sin refresh token; revocación de usuarios inactivos por eventos | El Gateway valida e inyecta `X-User-Id`, `X-Company-Id`, `X-User-Role`; los servicios confían en esas cabeceras solo si vienen del Gateway (red interna). El Gateway consume `UserAccountUpdated` del tópico compactado `orgaccess.user`, mantiene en memoria los usuarios INACTIVE y rechaza sus tokens con 401; al arrancar relee el tópico antes de declararse listo |
| ADR-006 | Privacidad B2B (ABAC) aplicada en Traceability | `LineageAuthorizationPolicy` filtra nodos y atributos según `CompanyPrivacyUpdated` y la empresa del solicitante |
| ADR-007 | Resilience4j en adaptadores externos con fallback definido | HIBP: aceptar y auditar. Cloudinary: cola diferida. SendGrid: tabla diferida + job. Chequea.pe: caché |
| ADR-008 | Regla de comunicación síncrona vs asíncrona | Solo síncrono si el usuario espera, la regla es del otro agregado y el dato viejo rompe el estado |
| ADR-009 | Comando síncrono Quality → Production para cambios de fase | `POST /internal/batches/{batchId}/phase-transitions`, `RestClient`, timeout 2 s, CB, retry, idempotente por `requestId`, sin fallback de éxito |
| ADR-010 | `Idempotency-Key` en escrituras + borradores offline en el frontend (RNF-05) | `IdempotencyFilter` en cada servicio con escrituras; tabla `idempotency_keys` 24 h; 422 si cambia el cuerpo. **Enmienda 2026-11-04:** plazos del cliente por tipo de operación (ver «Enmiendas») |
| ADR-011 | PDF generado localmente en Quality (Thymeleaf + OpenHTMLtoPDF) | `PdfGeneratorPort` → `OpenHtmlToPdfAdapter`. Sin servicios externos de PDF |
| ADR-012 | Validación de RUC con Chequea.pe | Dígito verificador local → `validated_ruc_cache` → API (`CHEQUEA_API_TOKEN`), timeout 3 s + CB. Solo ACTIVO y HABIDO. Si la API cae y no hay caché: rechazar con mensaje de reintento |
| ADR-013 | Bootstrap del Dueño de Licencia | Al iniciar, si no hay licencia: se crea con los límites de variables de entorno y se emite una invitación al dueño (`InvitationCreated`). Sin contraseña por defecto |

## Otras decisiones fijadas

- Rate limiting: Bucket4j en memoria en el Gateway (sin Redis).
- Cliente HTTP síncrono: Spring `RestClient` (no `RestTemplate`, no Feign, no gRPC).
- Despliegue objetivo: Azure Container Apps + Azure Container Registry + GitHub Actions; PostgreSQL Flexible Server (una base por servicio); Event Hubs Standard; Neo4j AuraDB Free.

## Enmiendas

### ADR-007 · borrado de evidencias (2026-11-04)

**Contexto.** V2 agregó el borrado de evidencias (`DELETE …/evidence/{evidenceId}` en Production y en Quality) sin historia de usuario ni ADR, y lo implementó de modo que (a) la llamada de borrado a Cloudinary **no** pasaba por el circuit breaker de ADR-007 y (b) el job de limpieza llamaba a Cloudinary **dentro de una transacción** con una fila bloqueada: una Cloudinary lenta retenía una conexión de la base hasta el timeout.

**Decisión.**
1. **Se acepta el borrado** como parte de US42 y US43 (adjuntar evidencia), con el alcance mínimo: el operario (Production) o el administrador (Quality) de la empresa dueña borra una evidencia propia. Queda la **auditoría** `EVIDENCE_DELETED` (quién, cuándo, de qué). No hay papelera ni «deshacer».
2. **Dos tiempos.** El `DELETE` solo **marca** la evidencia (`deleted_at`) en una transacción corta y responde 204; los listados la excluyen de inmediato. Un job (cada 5 s) la **purga**: (1) elige la siguiente (transacción corta), (2) borra la imagen en Cloudinary **sin transacción abierta**, (3) borra las referencias (otra transacción corta). Si Cloudinary falla para una evidencia, la marca sigue, la fila se **aplaza** (`purge_attempts`, `purge_retry_at`: 10 s, 20 s, 40 s ... tope 5 min) y el job **continúa con las demás**; no se detiene en el primer error.
3. **Circuit breaker.** La llamada de borrado usa el mismo `CircuitBreaker` `cloudinary` que la subida (timeout, ventana, umbral). Con el circuito abierto no se llama y se lanza `StorageNotAvailableException`; el job lo reintenta cuando cierra. **Sin fallback que simule éxito**: la referencia solo se borra después de que Cloudinary confirme (`ok` o `not found`).
4. **Idempotencia en vez de bloqueo.** Como no hay transacción durante la llamada, dos instancias podrían elegir la misma fila a la vez: ambas borran (la segunda recibe `not found`, que cuenta como hecho) y la segunda purga cero filas. Se prefiere una llamada de más a mantener una conexión retenida.

**Consecuencias.** `JdbcEvidenceDeletionAdapter.nextDeleted` ya no usa `FOR UPDATE SKIP LOCKED` y salta las filas aplazadas (V7 en Production y en Quality). Pruebas: `EvidenceDeletionServiceTest` / `QualityEvidenceDeletionTest` (la llamada corre con 0 transacciones abiertas), `EvidenceDeletionIT` (Production: la marca oculta, Cloudinary que falla deja la marca, y mientras Cloudinary responde no hay conexiones `idle in transaction`) y `EvidenceResilienceIT` (con el circuito abierto no se llama a Cloudinary para borrar, en ambos servicios). **Pendiente del equipo:** redactar la historia de usuario (hoy el alcance se apoya en US42/US43).

### ADR-010 — plazos del cliente por tipo de operación (2026-11-04)

**Motivo.** El texto original de RNF-05 daba un único plazo de 2,5 s al frontend para detectar que no hay respuesta. Con un solo plazo, operaciones que el servidor completa con normalidad se mostraban como «sin respuesta» aunque se hubieran registrado: la subida de evidencias (Cloudinary tarda hasta 8 s por archivo y se admiten 5 archivos de 10 MB), el alta de empresa (Chequea.pe 3 s más el circuito de resiliencia), el informe PDF (el generador tiene 10 s) y los comandos de Quality (Quality→Production: 2 s, 2 intentos y 300 ms ≈ 4,3 s antes del `202`).

**Decisión.** El plazo depende de la operación y sale de una única tabla en el frontend (`frontend/src/app/core/timeouts.ts`); la primera regla que coincide decide y toda ruta no clasificada usa 2,5 s.

| Operación | Plazo | Por qué |
|---|---|---|
| Lecturas y escrituras JSON normales (por defecto) | **2,5 s** | Sin dependencias lentas; se conserva el aviso rápido de RNF-05 |
| Subidas `multipart/form-data` (`POST /batches/{id}/evidence`, `POST /transformations/{id}/evidence`, `POST /quality/evidence`) | **30 s** | Hasta 5 archivos × 10 MB y Cloudinary a 8 s por archivo |
| Alta de empresa (`POST /companies`) | **30 s** | Chequea.pe (3 s) + invitación del primer administrador |
| Informe PDF (`GET /quality/controls/{id}/report`) | **15 s** | Generador de PDF con límite de 10 s |
| Comandos de Quality (`POST/PUT/PATCH/DELETE /quality/**`, salvo las subidas) | **8 s** | Llamada a Production (≈ 4,3 s) antes del `202` |

**Lo que no cambia.**
- Agotar el plazo **no** es un fallo de la operación: el cliente solo deja de esperar. La interfaz muestra el mismo mensaje («No se pudo confirmar la respuesta del servidor. Conserva los datos y vuelve a intentarlo.»), conserva los datos del formulario (borrador en IndexedDB) y **conserva la `Idempotency-Key` pendiente**.
- Todo reintento reutiliza **la misma `Idempotency-Key`**, de modo que el servidor devuelve la respuesta guardada en lugar de repetir la operación.
- El servidor guarda las claves **24 h** (`idempotency_keys`). El cliente las da por vigentes **23 h**: pasado ese tiempo descarta el registro pendiente (ya no se puede reutilizar), avisa de que la operación anterior pudo haberse registrado y de que hay que revisarlo antes de repetirla, y el siguiente intento usa una clave nueva.

**Consecuencias.**
- El aviso de «sin conexión» en menos de 3 s (RNF-05) sigue cumpliéndose para lecturas y escrituras normales y, en cualquier operación, cuando el navegador dispara el evento `offline`. En las operaciones con plazo largo, una red que está caída pero no se detecta como `offline` se notifica solo al agotarse su plazo (8, 15 o 30 s).
- Una ruta nueva del backend debe clasificarse: se registra en `frontend/src/app/core/service-routes.ts` y, si necesita un plazo propio, en `timeouts.ts`. `npm run check:routes` (compara con los controladores) y `timeouts.spec.ts` fallan si queda alguna sin clasificar.
- `communication.md` (sección de idempotencia y frontend) todavía cita el plazo único de 2,5 s y debe alinearse con esta enmienda.
