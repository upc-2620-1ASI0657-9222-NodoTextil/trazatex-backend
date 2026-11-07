# API pública y rutas del Gateway

Las rutas son las documentadas en el capítulo 5 del informe (Sprint 1). Se mantienen para que el frontend y el informe sigan calzando; ahora cada una la atiende el microservicio dueño.

## Enrutamiento del Gateway

| Prefijo | Servicio |
|---|---|
| `/api/auth/**`, `/api/license/**`, `/api/companies/**`, `/api/users/**`, `/api/invitations/**`, `/api/admin-requests/**`, `/api/privacy-settings/**` | organization-access |
| `/api/batches/**`, `/api/machines/**`, `/api/transformations/**`, `/api/transfers/**` | production |
| `/api/quality/**` | quality |
| `/api/traceability/**` | traceability |
| `/api/notifications/**` | notification |
| `/api/analytics/**` | analytics |
| `/.well-known/jwks.json` | **No se expone por el Gateway.** Lo sirve organization-access y el Gateway lo lee internamente vía `JWKS_URI`; quien necesite las claves públicas llama a organization-access directamente |
| `/internal/**` | **bloqueado**: el Gateway responde `404` (como una ruta inexistente, con o sin token, con cualquier método); solo red interna |

Errores de autenticación que genera el Gateway (formato `{code, message, details, timestamp}`; los 401 llevan `WWW-Authenticate: Bearer`): `401 MISSING_TOKEN` · `401 INVALID_TOKEN` · `401 TOKEN_EXPIRED` · `401 USER_INACTIVE` (el usuario fue inactivado: RF-012, el token aún vigente deja de servir en pocos segundos y vuelve a servir si se reactiva) · `503 AUTH_UNAVAILABLE` (no se puede validar la sesión: JWKS inaccesible sin caché, o el Gateway aún está cargando la lista de usuarios inactivos). Las rutas públicas no dependen de nada de esto. El Gateway **no está listo** (`/actuator/health/readiness` responde 503) hasta cargarla.

Límite de peticiones (Bucket4j en memoria, sin Redis; todo configurable en `trazatex.rate-limit.*`). Al agotarse un cubo: `429 TOO_MANY_REQUESTS` con cabecera `Retry-After` (segundos enteros, mínimo 1) y cuerpo `ApiError`; la petición no llega al servicio.

| Ruta | Cubo | Límite por defecto |
|---|---|---|
| `POST /api/auth/login` | por IP | 60 / min (en una planta muchos operarios comparten IP; RNF-06 ya bloquea por cuenta) |
| `GET /api/invitations/{token}` y `POST .../accept` | por IP (un solo cubo para las dos) | 30 / min |
| Rutas autenticadas | por `userId` **y** por empresa, **a la vez** | 120 / min por usuario y 600 / min por empresa (el Dueño de Licencia, sin empresa, solo el de usuario) |
| Rutas protegidas **sin credencial válida** (sin token, o token que no firmó organization-access) | por IP | 300 / min; pasado el límite esas peticiones reciben `429` en lugar de `401`. No cuentan el token vencido ni el usuario inactivo (sesiones reales), ni frena a quien trae un token válido desde la misma IP |

La IP es la **dirección remota de la conexión**; un `X-Forwarded-For` enviado por el cliente nunca se usa. Detrás de un proxy de confianza se activa `trazatex.rate-limit.trust-forwarded-for=true` (desactivado por defecto): en Azure es el ingress; en el `compose.yaml` es el Nginx de `web` (único camino al gateway, que no se publica en el host), activado por defecto y con `X-Forwarded-For $remote_addr` para que ningún cliente pueda ponerse otra IP. Se toma el valor que **agrega el proxy** (el de más a la derecha, o el `trusted-hops`-ésimo desde la derecha), no lo que el cliente haya puesto a la izquierda. Una IPv6 se limita por su prefijo /64. Una petición sin identidad válida (401/503) no gasta cubos autenticados; los preflight CORS no cuentan.

Identidad: el Gateway **borra** toda cabecera `X-User-*` y `X-Company-*` que traiga la petición (en todas las rutas, también las públicas) e inyecta `X-User-Id`, `X-User-Role` y `X-Company-Id` (esta última ausente para el Dueño de Licencia) desde el JWT. No toca otras cabeceras `X-*` (como `X-Forwarded-*`). Rutas con segmentos `.`/`..`, `%2F`, `\`, `;`, `//` o bytes nulos se rechazan con `400` antes de enrutar (el servicio de destino las normalizaría y podrían llegar a `/internal`).

Rutas públicas (sin JWT): `POST /api/auth/login`, `GET /api/invitations/{token}`, `POST /api/invitations/{token}/accept`. Todo lo demás exige JWT.

`Idempotency-Key` se ignora en las rutas públicas (no hay `X-User-Id` a quien atribuir la clave): el servicio no responde 400 si el frontend la envía ahí, pero tampoco la guarda ni repite respuestas. Aceptar una invitación ya aceptada responde `409 INVITATION_ALREADY_ACCEPTED`.

CORS se configura solo en el Gateway. Cada servicio expone su OpenAPI (springdoc); el Gateway puede agruparlos.

## Rutas del frontend

No las sirve ningún microservicio; son las que construyen los servicios al armar enlaces de correo.

| Ruta | Quién la usa |
|---|---|
| `/invitations/{token}` | Pantalla de aceptación de invitación. `organization-access` arma `acceptUrl = TRAZATEX_FRONTEND_URL + /invitations/{token}` (evento `InvitationCreated`). La pantalla consulta `GET /api/invitations/{token}` y envía `POST /api/invitations/{token}/accept`. |

## Endpoints por servicio

### organization-access
| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/auth/login` | Login; devuelve JWT (RS256, 8 h). Bloqueo 15 min tras 3 fallos **por correo**, exista o no la cuenta: 423 `ACCOUNT_LOCKED` (RNF-06). Credenciales inválidas: siempre 401 `INVALID_CREDENTIALS` |
| GET | `/api/license` | Resumen de la licencia (US01) |
| POST / GET | `/api/companies` | Crear (valida cupo y RUC con Chequea.pe) / listar (US02) |
| PATCH | `/api/companies/{id}/status` | Activar o inactivar (US03) |
| POST | `/api/invitations/operators` | Invitar operario (US06) |
| POST | `/api/invitations/{id}/cancel` | Cancelar invitación (US41) |
| GET | `/api/invitations` | COMPANY_ADMIN y LICENSE_OWNER: las invitaciones que **emitió quien consulta**, **paginadas** (`page`, `size`; por defecto 20, máximo 100; respuesta `{items,page,size,totalElements,totalPages}`; `400 INVALID_PAGINATION`) de la más reciente a la más antigua, con el estado efectivo (una `PENDING` vencida sale `EXPIRED`); nunca el token. Agregado en V2; documentado el 2026-11-03 |
| GET | `/api/companies/current/capacity` | COMPANY_ADMIN: cupo de usuarios de la propia empresa `{limit, activeUsers, pendingInvitations}` (usuarios `ACTIVE` e invitaciones `PENDING` vigentes, los mismos conteos que al invitar). Sin licencia inicializada → **404 `LICENSE_NOT_FOUND`** (cambio del 2026-11-03, al pasar el endpoint por un caso de uso; antes respondía un error 500 genérico). Agregado en V2 |
| GET | `/api/invitations/{token}` | Detalle de invitación |
| POST | `/api/invitations/{token}/accept` | Aceptar invitación y crear cuenta, con HIBP (US04) |
| GET | `/api/users` | Usuarios de la empresa |
| PATCH | `/api/users/{id}/status` | Activar o inactivar (US09) |
| POST / GET | `/api/admin-requests` | Solicitar administrador (US07) / listar |
| PATCH | `/api/admin-requests/{id}/decision` | Aprobar o rechazar (US08) |
| GET / PUT | `/api/privacy-settings` | Privacidad B2B (US11) |
| GET | `/.well-known/jwks.json` | Claves públicas |

### production
| Método | Ruta | Uso |
|---|---|---|
| POST / GET | `/api/batches` | Registrar (US12) / buscar y filtrar (US13) |
| GET | `/api/batches/{id}` · `/api/batches/qr/{qrCode}` | Detalle · por QR (US14) |
| POST | `/api/batches/{batchId}/split` | Dividir (US15) |
| PUT | `/api/batches/{id}/commercial-data` | Reemplazar los datos comerciales de un producto final (US20); si hay precio, la moneda es obligatoria |
| POST / GET | `/api/batches/{id}/evidence` | Adjuntar fotos (`multipart/form-data`, campo `files`; JPEG, PNG o WebP, máx. 10 MB c/u) / listar (US42) |
| POST / GET / PATCH | `/api/machines`, `/api/machines/{id}`, `/api/machines/{id}/status` | Maquinaria (US10) |
| POST / GET | `/api/transformations`, `/api/transformations/{id}` | Iniciar (US16) / detalle |
| POST | `/api/transformations/{id}/complete` | Finalizar con balance (US18, US19) |
| POST / GET | `/api/transformations/{id}/evidence` | Adjuntar fotos (igual que en lotes) / listar (US42) |
| POST / GET | `/api/transfers`, `/api/transfers/{id}` | Iniciar (US21) / detalle |
| POST | `/api/transfers/{id}/accept` · `/reject` | Resolver (US22) |
| POST | `/internal/batches/{batchId}/phase-transitions` | **Comando interno** de Quality (ver `communication.md`) |

#### Contratos de production (fase 04b)

Todas las rutas son de `/api/**`: llevan las cabeceras de identidad que inyecta el Gateway, y ninguna es accesible para el `LICENSE_OWNER` (sin empresa → 403). Las escrituras aceptan `Idempotency-Key` (la repetición devuelve el mismo JSON, pero como se guarda en una columna `JSONB` el orden de las claves puede diferir del original). Cantidades en kg con **3 decimales** y porcentajes con **2** (más decimales → 400, no se redondea). Errores: `ApiError { code, message, details, timestamp }`.

| Ruta | Roles | Cuerpo / parámetros → respuesta |
|---|---|---|
| `POST /api/batches` | OPERATOR | `{name?, materialType, composition:[{name, percentage}], quantityKg, origin, supplierName?, receptionCharacteristics}` → 201 lote completo (`BatchResult`). **`name`: hasta 20 caracteres** (se cuentan caracteres, no bytes; sin nombre se usa el Traceability ID); más → 400 `INVALID_BATCH_NAME` (`details: ["name"]`). La misma regla vale para el `name` de cada hijo de una división y de cada salida de una transformación |
| `GET /api/batches` | OPERATOR, COMPANY_ADMIN | filtros `traceabilityId` (prefijo; se normaliza lo escrito a mano: minúsculas, O→0, I/L→1), `materialType`, `phase`, `qualityStatus`, `createdFrom`, `createdTo` (`yyyy-MM-dd`, días de Lima, ambos inclusivos), `page`, `size` → página de resúmenes |
| `GET /api/batches/{id}` | ambos | lote completo de la propia empresa; de otra empresa → 404 |
| `GET /api/batches/qr/{qrCode}` | ambos | `{access: "OWN"\|"SHARED", batch}`: propio → todos sus datos; ajeno → solo los campos siempre compartidos (`batchId, traceabilityId, qrCode, companyId, materialType, phase, qualityStatus, createdAt, finalProduct`); desconocido o mal formado → 404 `QR_NOT_FOUND` |
| `POST /api/batches/{batchId}/split` | OPERATOR | `{children:[{name?, quantityKg}]}` → 201 `{parent, children}` |
| `PUT /api/batches/{id}/commercial-data` | OPERATOR | `{buyerOrDistributor?, salePrice?, currency?, commercialExitDate?, commercialReference?}` **reemplaza** los datos comerciales del producto final (lo omitido queda vacío); precio sin moneda → 400; `PATCH` ya no existe en esta ruta (405) |
| `POST /api/machines` · `PATCH /api/machines/{id}/status` | COMPANY_ADMIN | `{internalCode, name, type}` → 201 · `{status}` → 200 |
| `GET /api/machines[?status=]` · `GET /api/machines/{id}` | ambos | lista por código · detalle con el historial de estados `{machine, history:[{from, to, changedBy, changedAt}]}` (el registro tiene `from` nulo) |
| `POST /api/transformations` | OPERATOR | `{type, machineId, batchIds[]}` → 201 |
| `POST /api/transformations/{id}/complete` | OPERATOR | `{outputs:[{name?, materialType, quantityKg, finalProduct?, commercialData?}], wasteKg?, wasteReason?}` → 200 `{transformation, outputs}`; la merma nunca se envía, se calcula |
| `GET /api/transformations[?status=&type=&page=&size=]` · `GET /api/transformations/{id}` | ambos | página · detalle |
| `POST /api/transfers` | OPERATOR | `{targetCompanyId, batchIds[], quantitiesKg?}` → 201. Una transferencia es de **lotes completos**: `quantitiesKg` (opcional, `batchId → kg`) existe para que pedir solo una parte de un lote dé **400 `PARTIAL_TRANSFER_NOT_ALLOWED`** ("Solo se transfieren lotes completos: divida el lote antes", con el Traceability ID del lote en `details`) y no cambie nada; un valor igual a la cantidad del lote pasa. Un `batchId` de `quantitiesKg` que no está en `batchIds` → 400 `INVALID_QUANTITIES`; un valor nulo, cero, negativo o con más de 3 decimales → 400 `INVALID_QUANTITY`. Un 400 no deja eventos ni claves de idempotencia |
| `POST /api/transfers/{id}/accept` · `/reject` | COMPANY_ADMIN (destino) | — · `{reason}` → 200 |
| `GET /api/transfers[?direction=OUTGOING\|INCOMING\|ALL&status=&page=&size=]` · `GET /api/transfers/{id}` | ambos | página · `{transfer, batches:[{batchId, traceabilityId, name, materialType, quantityKg}]}` (solo la empresa origen y la destino la ven; otra → 404). **Excepción a la regla de `name`** (decisión del usuario, 2026-11-03): aquí el nombre libre del lote lo ven la empresa origen y la destino, aunque una de ellas no sea (o ya no sea) la dueña; en Traceability `name` sale solo en lotes `OWN` (`glossary.md`). El resumen de cada lote es **solo esos cinco campos**: nunca `createdBy`, composición, proveedor ni datos comerciales, ni siquiera para la empresa origen (un producto final, el único con datos comerciales, no se puede transferir: 409 `BATCH_IS_FINAL_PRODUCT`) |
| `GET /api/transfers/recipients[?page=&size=]` | OPERATOR, COMPANY_ADMIN | Las empresas **activas** a las que se puede transferir ("misma cadena", US21), por razón social y, a igual nombre, por id: **página** `{items:[{id, businessName}], page, size, totalElements, totalPages}` (I12). Sale de la proyección local de empresas (`company_projection`). `LICENSE_OWNER` → 403 |
| `POST /api/batches/{id}/evidence` · `POST /api/transformations/{id}/evidence` | OPERATOR (de la empresa dueña) | `multipart/form-data`, campo `files` (1 a **5** archivos, **JPEG/PNG/WebP por sus primeros bytes**, **10 MB** c/u; todo o nada) → **201** `{items:[{id, ownerType, ownerId, status, url, contentType, sizeBytes, originalFilename, uploadedBy, createdAt, uploadedAt}]}`. Siempre 201 aunque Cloudinary falle: esa evidencia sale `status: "PENDING"` con `url: null` y se sube después. 400 `UNSUPPORTED_FILE_FORMAT` (con los formatos permitidos y el nombre del archivo en `details`), `NO_FILES`, `TOO_MANY_FILES`; 413 `FILE_TOO_LARGE`; 415 si no es multipart. Acepta `Idempotency-Key`: para multipart el hash es de las **partes** (campo, nombre de archivo, tipo y bytes), no del cuerpo crudo, así que un reintento con otro separador devuelve la respuesta guardada |
| `DELETE /api/batches/{id}/evidence/{evidenceId}` · `DELETE /api/transformations/{id}/evidence/{evidenceId}` | OPERATOR (de la empresa dueña) | **204**. Borrado en dos tiempos (M5, ver *ADR-007 · borrado de evidencias* en `adr.md`): la evidencia se **marca** (`deleted_at`) en una transacción corta, con auditoría `EVIDENCE_DELETED`, y desaparece de inmediato de los listados; un job (cada 5 s) borra la imagen en Cloudinary —por el circuit breaker y fuera de toda transacción— y después las referencias; si Cloudinary falla para una evidencia, esa se aplaza con espera creciente (10 s, 20 s ... 5 min) y el job sigue con las demás. `404 EVIDENCE_NOT_FOUND` si no existe o no es de la propia empresa. No hay historial de lo borrado más allá de la auditoría |
| `GET /api/batches/{id}/evidence` · `GET /api/transformations/{id}/evidence` | ambos | `{items:[...]}` de las evidencias que subió **la propia empresa**, de la más antigua a la más nueva; `status` `PENDING` (sin `url`), `UPLOADED` o `FAILED`. Lote o transformación de otra empresa → 404 |
| `POST /internal/batches/{batchId}/phase-transitions` | ninguno (red interna; el Gateway la responde 404) | `{operation, requestId, qualityHold?, reason?, requestedBy?}` → 200 `{batchId, phase, qualityHold}`. `qualityHold` obligatorio en `RELEASE`; `requestedBy` (UUID) obligatorio salvo en `HOLD`. 409 solo por rechazo de negocio (`INVALID_PHASE_TRANSITION`, `BATCH_IS_FINAL_PRODUCT`; en `START_EVALUATION` también `COMPANY_INACTIVE`/`COMPANY_UNKNOWN`), 404 `BATCH_NOT_FOUND`, 400 (`MISSING_FIELDS`, `INVALID_REQUEST_ID`, `MALFORMED_REQUEST`), 503 `TEMPORARILY_UNAVAILABLE` con `Retry-After: 1` si una carrera no se resuelve. Idempotente por `(batchId, requestId, operation)`. Ver `communication.md` |

**Paginación (RNF-03).** `page` desde 0 (por defecto 0); `size` por defecto 20 y **máximo 100: si piden más, se usa 100** (la respuesta trae el `size` efectivo); `page < 0` o `size < 1` → 400 `INVALID_PAGINATION`. Respuesta `{items, page, size, totalElements, totalPages}`. Orden estable: `created_at` descendente y, a igual fecha, `id` ascendente (`start_time`/`departure_time` en transformaciones y transferencias), así dos páginas nunca repiten ni saltan filas. La búsqueda por prefijo del Traceability ID usa el índice `ix_batches_company_traceability_prefix` (`text_pattern_ops`, migración V2) y el listado el índice `ix_batches_company_created`; ambos se comprueban con `EXPLAIN` en las pruebas.

**Errores propios de production** (además de los comunes): 400 `MISSING_FIELDS`, `INVALID_QUANTITY`, `INVALID_QUANTITIES` (la salida más el desperdicio supera la entrada), `INVALID_COMPOSITION`, `SPLIT_QUANTITIES_MISMATCH`, `SPLIT_REQUIRES_TWO_CHILDREN`, `WASTE_REASON_REQUIRED`, `YIELD_BELOW_MINIMUM`, `PRICE_REQUIRES_CURRENCY`, `INVALID_COMMERCIAL_DATA`, `NO_BATCHES_SELECTED`, `DUPLICATE_BATCH`, `REJECTION_REASON_REQUIRED`, `SAME_COMPANY_TRANSFER`, `INVALID_PAGINATION`, `INVALID_DATE_RANGE`; 404 `BATCH_NOT_FOUND`, `QR_NOT_FOUND`, `MACHINE_NOT_FOUND`, `TRANSFORMATION_NOT_FOUND`, `TRANSFER_NOT_FOUND`, `TARGET_COMPANY_NOT_FOUND`; 409 `INVALID_PHASE_TRANSITION`, `BATCH_ON_QUALITY_HOLD`, `BATCH_IS_FINAL_PRODUCT`, `NOT_A_FINAL_PRODUCT`, `MACHINE_NOT_ACTIVE`, `MACHINE_CODE_ALREADY_EXISTS`, `INVALID_STATUS_TRANSITION`, `TRANSFORMATION_NOT_IN_PROGRESS`, `TRANSFER_NOT_PENDING`, `COMPANY_INACTIVE`, `COMPANY_UNKNOWN`, `TARGET_COMPANY_INACTIVE`, `CONCURRENT_MODIFICATION`; 403 `NOT_TRANSFER_RECIPIENT`.

### traceability

Solo lectura (los cambios del grafo llegan por eventos). Roles: **OPERATOR y COMPANY_ADMIN**; `LICENSE_OWNER` (sin empresa) → 403 `FORBIDDEN`; sin cabeceras de identidad → 401 `AUTHENTICATION_REQUIRED`. Un usuario de una empresa `INACTIVE` **sí consulta** (es lectura; Traceability no guarda el estado de las empresas). Cualquier usuario autenticado puede abrir cualquier genealogía, también la que cruza otras empresas: de las demás ve solo lo siempre compartido y los campos configurables que **esa empresa** tiene marcados `SHARED` **ahora** (su último `CompanyPrivacyUpdated`; vale para todos sus nodos, también los antiguos, y cambia en cuanto ella cambia la configuración; sin configuración, todo `PRIVATE`) (ADR-006); `qualityHold` (nunca sale a otra empresa) y los campos configurables privados no salen **ni como `null`** (están ausentes del JSON). Una respuesta de otra empresa nunca revela un valor oculto.

**Toda respuesta de `/api/traceability/**` lleva `Cache-Control: no-store`** (lo que se ve depende de la empresa que consulta; un caché compartido podría entregar a una empresa lo calculado para otra): la pone el servicio (filtro, también en 401/403/404/400/405/500 y rutas inexistentes) y el Gateway la impone él mismo antes de enviar la respuesta, de modo que vale también para sus propios errores (401, 429, 502/503/504 y los rechazos del guardián de rutas).

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/traceability/lineages?qrCode=&traceabilityId=&page=&size=` | Buscar (US30). `qrCode`: coincidencia exacta, de cualquier empresa. `traceabilityId`: por **prefijo solo entre los lotes de la propia empresa**; en los de otra, solo coincidencia exacta (no se pueden listar los lotes de otra empresa). Se normaliza lo escrito a mano (mayúsculas, O→0, I/L→1). Al menos uno (400 `MISSING_SEARCH_CRITERIA`); con ambos deben cumplirse los dos. Paginación como en production (`size` por defecto 20, máximo 100: si piden más, se usa 100; `page < 0` o `size < 1` → 400 `INVALID_PAGINATION`); orden `createdAt` descendente, luego `batchId`; con un Traceability ID exacto de otra empresa, ese lote ocupa el primer lugar. Respuesta `{items:[{batchId, traceabilityId, qrCode, companyId, businessName, materialType, phase, qualityStatus, createdAt, finalProduct, access}], page, size, totalElements, totalPages}`; nunca devuelve lotes incompletos |
| GET | `/api/traceability/batches/{batchId}/ancestors` | Hacia atrás (US31) |
| GET | `/api/traceability/batches/{batchId}/descendants` | Hacia adelante (US32) |
| GET | `/api/traceability/batches/{batchId}/lineage` | Completa, con filtro B2B (US36): ambos sentidos y el historial de transferencias de cada lote (`transferHistory`, lista vacía si no tiene; solo en esta vista) |

Respuesta de genealogía: `{batchId, direction (ANCESTORS|DESCENDANTS|BOTH), maxLevels: 10, complete, truncated, nodes:[…], edges:[{type (SPLIT_INTO|INPUT_OF|PRODUCED|…), from, to}]}`. Cada nodo trae `id`, `kind` (BATCH|TRANSFORMATION), `level` (negativo hacia atrás, positivo hacia adelante, 0 el lote pedido; un nivel = un salto lote→lote), `access` (`OWN`|`SHARED`), `companyId`, `businessName` y los campos de su tipo: de un lote `traceabilityId, qrCode, materialType, phase, qualityStatus, createdAt, finalProduct` (siempre) y `supplierName, origin, quantityKg, composition, receptionCharacteristics` (propio o compartido), `qualityHold` (solo la empresa dueña); de una transformación `type, startedAt, completedAt` (siempre) y `machineName, machineType, shrinkageKg, wasteKg, wasteReason` (propia o compartido). Las relaciones se muestran siempre.
- `truncated: true` → había más genealogía pasados los 10 niveles. `complete: false` → el recorrido se detuvo en un nodo cuyo registro aún no llegó (el frontend muestra "en actualización"); ese nodo no aparece y la genealogía se completa sola cuando llega.
- Errores: **404 `BATCH_NOT_FOUND` ("El lote no existe.") con exactamente el mismo cuerpo** (salvo `timestamp`) para un lote que no existe, uno provisional (solo conocido por una referencia) y uno incompleto o ilegible: nunca se dice cuál es el caso. 400 `INVALID_PARAMETER` (con el parámetro en `details`) si `batchId` no es un UUID o `page`/`size` no son números; 400 `MISSING_SEARCH_CRITERIA`, `INVALID_PAGINATION`; 401/403 como arriba.

### quality *(06c: control, pruebas, finalizar, falla y descarte; evidencias y reporte, 06d)*

| Endpoint | Roles | Descripción |
|---|---|---|
| `POST /api/quality/batches/{batchId}/controls` | COMPANY_ADMIN | Inicia un control (US23, US34). Cuerpo opcional `{internalObservations}`. Acepta `Idempotency-Key`. **`201`** + `Location`: Production confirmó y el control está `IN_PROGRESS`. **`202`** + `Location` + `Retry-After: 5`: Production no respondió (503, otro 5xx, timeout, sin conexión o circuito abierto); el control está `STARTING` y el `PendingControlRecoveryJob` lo resuelve solo con el mismo `requestId` (consultar `Location`). Cuerpo (ambos): el control `{id, batchId, companyId, controlType, status, overallResult, inspectorId, startDate, endDate, internalObservations, cancelReason, tests[]}`. |
| `GET /api/quality/controls/{controlId}` | COMPANY_ADMIN | El control de la propia empresa; `status` pasa de `STARTING` a `IN_PROGRESS` o `CANCELLED` (con `cancelReason` = el código de error de Production). |
| `GET /api/quality/controls[?page=&size=]` | COMPANY_ADMIN | **Página** de los controles de la propia empresa, del más reciente al más antiguo (`start_date` descendente, luego `id`), cada uno con sus `tests` (I12: las pruebas de la página se leen con una sola consulta). `{items:[<control>], page, size, totalElements, totalPages}` |
| `GET /api/quality/failures[?page=&size=]` | COMPANY_ADMIN | **Página** de las fallas de la propia empresa, de la más reciente a la más antigua (`created_at` descendente, luego `id`). Mismo formato de página |
| `GET /api/quality/batches[?page=&size=]` | COMPANY_ADMIN | **Página** de `{batchId, qualityStatus, qualityHold}` de los lotes de la propia empresa que ya tienen estado de calidad (los usa el frontend para saber que un lote ya está sincronizado con Calidad antes de iniciar un control), por `batchId` |

Paginación de estos tres listados, de los destinatarios y de las invitaciones: la misma convención de arriba (`page` desde 0; `size` por defecto 20, **máximo 100**: si piden más se usa 100 y la respuesta trae el efectivo; `page < 0` o `size < 1` → `400 INVALID_PAGINATION`). El frontend los recorre con `api.all` (páginas de 100) mientras no tenga paginación en pantalla.

- **Errores del inicio**: `409` con las reglas de dominio —`CONTROL_ALREADY_ACTIVE`, `BATCH_NOT_AVAILABLE_FOR_CONTROL`, `BATCH_ALREADY_CONFORMING`, `BATCH_FAILED_REQUIRES_REEVALUATION` (primero hay que reevaluar, US26), `BATCH_QUALITY_UNKNOWN`— y con el rechazo de Production: **`409` con el mismo código de Production** (`INVALID_PHASE_TRANSITION`, `BATCH_IS_FINAL_PRODUCT`, `COMPANY_INACTIVE`, `COMPANY_UNKNOWN`) y el control queda `CANCELLED` con ese código como motivo, aunque la respuesta sea 409; `404 BATCH_NOT_FOUND` si Production responde 404. Un `400` de Production es un defecto de Quality: `500 INTERNAL_ERROR` (el detalle va al log como ERROR) y el control queda `CANCELLED` con el código del 400.
- **Otra empresa** (convención de Production): un lote o control de otra empresa es **`404`** (`BATCH_NOT_FOUND` / `CONTROL_NOT_FOUND`) con exactamente el mismo cuerpo, salvo `timestamp`, que uno inexistente. Otro rol, incluido `LICENSE_OWNER` (sin empresa) → `403 FORBIDDEN`; sin cabeceras de identidad → `401 AUTHENTICATION_REQUIRED`; `batchId` que no es UUID → `400 INVALID_PARAMETER`.
- Los accesos son solo de `COMPANY_ADMIN`: las historias de EP03 (US23…US28, US34) son del administrador de empresa.

**06c** (todos solo `COMPANY_ADMIN`; los de escritura aceptan `Idempotency-Key`; lo de otra empresa es `404` con el mismo cuerpo que lo inexistente):

| Endpoint | Descripción |
|---|---|
| `POST /api/quality/controls/{controlId}/tests` | US24. Cuerpo `{criterionName, expectedValue, obtainedValue, unit, testResult (CONFORMING\|FAILED), remarks?}` → `201` con la prueba. `400 MISSING_FIELDS`; `409 CONTROL_NOT_IN_PROGRESS` (el control no está en curso) o `409 CONTROL_FINISH_PENDING` (se pidió finalizarlo y Production no ha confirmado: las pruebas están congeladas). |
| `POST /api/quality/controls/{controlId}/finish` | US25, US28, US34. **`200`** `{control, failure}`: Production confirmó `RELEASE`; el control está `FINISHED` y `failure` es la que generó, reabrió o resolvió (`null` si salió conforme sin falla que resolver). **`202`** + `Location` + `Retry-After: 5`: Production no respondió; el control sigue `IN_PROGRESS` con `finishPending: true` y el job lo resuelve solo con el mismo `requestId`. **El control, la calidad del lote, la falla y los eventos solo cambian con la confirmación.** Si Production lo rechaza: `409` con su código (o `404 BATCH_NOT_FOUND`) y el control sigue `IN_PROGRESS` con las pruebas abiertas (un `400` es `500`). `409 CONTROL_HAS_NO_TESTS` sin pruebas. |
| `GET /api/quality/failures/{failureId}` | La falla `{id, controlId, batchId, companyId, rootCause, affectedQtyKg, observations, status, discardPending}`. `controlId` es el último control que la originó o reabrió. |
| `PUT /api/quality/failures/{failureId}` | US26 esc. 1. Cuerpo `{rootCause, affectedQtyKg, observations?}` → `200`. `400 MISSING_FIELDS`/`INVALID_QUANTITY` (la cantidad debe ser > 0); `409 FAILURE_DISCARD_PENDING`. |
| `POST /api/quality/failures/{failureId}/reevaluation` | US26 esc. 2. `200`: el lote pasa a `PENDING_REEVALUATION` y sigue retenido; no llama a Production ni exige información. `409 INVALID_QUALITY_TRANSITION` si el lote no está `FAILED`. |
| `POST /api/quality/failures/{failureId}/discard` | US27. **`200`** `{failure, completed: true}`: Production confirmó `DISCARD` y la falla está `CONFIRMED` (se emite `FailureConfirmed`). **`202`**: sin respuesta; la falla sigue `OPEN` con `discardPending` y se resuelve sola. Exige causa y cantidad: `409 FAILURE_INFO_INCOMPLETE`. Solo un lote `FAILED`. Si Production lo rechaza: `409` con su código y la falla sigue `OPEN`. |

Errores nuevos de 06c: `CONTROL_FINISH_PENDING`, `CONTROL_HAS_NO_TESTS`, `FAILURE_INFO_INCOMPLETE`, `FAILURE_DISCARD_PENDING`, `INVALID_FAILURE_TRANSITION`, `INVALID_QUALITY_TRANSITION`, `FAILURE_NOT_FOUND`.

**06d** (solo `COMPANY_ADMIN`, de la empresa dueña; lo de otra empresa es `404` con el mismo cuerpo que lo inexistente):

| Endpoint | Descripción |
|---|---|
| `GET /api/quality/controls/{controlId}/report` | US44. El reporte PDF de un control **finalizado**, generado en memoria (Thymeleaf + OpenHTMLtoPDF, ADR-011) y **sin almacenarse**. **`200`** `application/pdf` con `Content-Disposition: attachment; filename="reporte-control-<TraceabilityID>-<8 del id>.pdf"`. `409 CONTROL_NOT_FINISHED` si el control no está `FINISHED`; `404 CONTROL_NOT_FOUND`; **`503 REPORT_UNAVAILABLE`** + `Retry-After: 5` si el generador falla, tarda más de 10 s o hay 4 generaciones en curso (se puede reintentar). **Todas** las respuestas de la ruta, errores incluidos, llevan `Cache-Control: no-store`. Contenido y exclusiones: `docs/domain/quality.md`. Audita `REPORT_GENERATED` (sin el contenido). |
| `POST /api/quality/evidence` | US43. `multipart/form-data`: **exactamente uno** de `controlId` / `failureId` (si no, `400 EVIDENCE_OWNER_REQUIRED`) y `files` (1 a **5** archivos, **JPEG/PNG/WebP por sus primeros bytes**, **10 MB** c/u; todo o nada) → **`201`** `{items:[{id, ownerType (CONTROL\|FAILURE), ownerId, status, url, contentType, sizeBytes, originalFilename, uploadedBy, createdAt, uploadedAt}]}`. Siempre 201 aunque Cloudinary falle: esa evidencia sale `PENDING` con `url: null` y el job la sube después (cada 30 s). `400 UNSUPPORTED_FILE_FORMAT` (con los formatos permitidos y el archivo en `details`), `NO_FILES`, `TOO_MANY_FILES`; `413 FILE_TOO_LARGE`; `415` si no es multipart; `404 CONTROL_NOT_FOUND` / `FAILURE_NOT_FOUND`. El control puede estar en **cualquier estado**. Acepta `Idempotency-Key` (el hash es de las partes, como en Production). |
| `GET /api/quality/controls/{id}/evidence` · `GET /api/quality/failures/{id}/evidence` | `{items:[…]}` de las evidencias que subió **la propia empresa**, de la más antigua a la más nueva (`PENDING` sin `url`, `UPLOADED` o `FAILED`). |
| `DELETE /api/quality/controls/{id}/evidence/{evidenceId}` · `DELETE /api/quality/failures/{id}/evidence/{evidenceId}` | COMPANY_ADMIN: **204**, con el mismo borrado en dos tiempos que en Production (ADR-007 · borrado de evidencias). `404 EVIDENCE_NOT_FOUND` si no existe o no es de la propia empresa. |

Errores nuevos de 06d: `CONTROL_NOT_FINISHED`, `REPORT_UNAVAILABLE`, `EVIDENCE_OWNER_REQUIRED`, `NO_FILES`, `TOO_MANY_FILES`, `UNSUPPORTED_FILE_FORMAT`, `FILE_TOO_LARGE`. Rutas del Gateway: la misma `/api/quality/**` (el Gateway deja pasar la subida multipart y el PDF tal cual). El endpoint `/api/quality/batches/{batchId}/potential-derived-failure` del Sprint 1 se reemplaza por el consumo de `PossibleDerivedFailureDetected`.

### notification
Rutas del Gateway: `/api/notifications/**`. Identidad por las cabeceras `X-User-Id`, `X-Company-Id` y `X-User-Role`; cada usuario ve **solo las suyas** (las de otro, o de otra empresa, responden `404` con el mismo cuerpo que una inexistente).

| Ruta | Respuesta |
|---|---|
| `GET /api/notifications?unread=&type=&page=&size=` | Página `{items, page, size, totalElements, totalPages}`, más recientes primero (`created_at` descendente y, a igual fecha, `id`). `unread=true` solo no leídas, `unread=false` solo leídas, sin el parámetro todas. `type` es un código de la tabla de abajo. `size` por defecto 20 y máximo 100 (más se recorta a 100). `page < 0` o `size < 1` → 400 `INVALID_PAGINATION`; un `type` desconocido o un valor que no es booleano → 400 `INVALID_PARAMETER`. |
| `GET /api/notifications/unread-count` | `{count}`: las no leídas **del usuario**, sin importar la página. |
| `PATCH /api/notifications/{id}/read` | La notificación ya marcada. Repetirla no cambia nada. Una que no existe, o que es de otro usuario o empresa, responde `404 NOTIFICATION_NOT_FOUND` con exactamente el mismo cuerpo (no se revela que existe); lo mismo para `PUT .../read`. |
| `PUT /api/notifications/{id}/read?value=true\|false` · `PUT /api/notifications/read-all` | `200` sin cuerpo. |

Cada elemento: `{id, type, title, message, resourceType, resourceId, data, companyId, role, createdAt, read}`. **`type` es un código estable**, no texto de pantalla: la interfaz decide cómo mostrarlo. `title` y `message` son texto genérico en español. `data` lleva los ids que el front necesita; `resourceType` y `resourceId` apuntan al recurso principal.

| `type` | Evento | Destinatario | `data` |
|---|---|---|---|
| `ADMIN_REQUEST_SUBMITTED` | `AdminRequestSubmitted` | `LICENSE_OWNER` | `requestId`, `companyId` |
| `ADMIN_REQUEST_RESOLVED` | `AdminRequestResolved` | quien hizo la solicitud | `requestId`, `decision` |
| `TRANSFER_RECEIVED` | `TransferInitiated` | administradores de la empresa destino | `transferId`, `batchIds`, `fromCompanyId`, `toCompanyId` |
| `TRANSFER_ACCEPTED` · `TRANSFER_REJECTED` | `TransferAccepted` · `TransferRejected` | administradores de la empresa origen | ídem |
| `POTENTIAL_DERIVED_FAILURE` | `PossibleDerivedFailureDetected` | administradores de **cada empresa afectada**, **una alerta por falla y empresa** | `failureId`, `sourceBatchId`, `batchIds` (todos los lotes de esa empresa; `resourceId` es el primero) |

### analytics
Solo `COMPANY_ADMIN`; la empresa sale siempre de `X-Company-Id`. El endpoint de reporte de Analytics desapareció (pasó a Quality) y la ruta `GET /api/analytics` (sin `/summary`) tampoco existe: es `404`.

| Método y ruta | Descripción |
|---|---|
| `GET /api/analytics/summary?from=AAAA-MM-DD&to=AAAA-MM-DD` | Los cuatro bloques de EP05 en un documento: `quality` (US37, estado al cierre del periodo), `failureCauses` (US38, fallas registradas por causa), `durations` (US39, promedio en horas de transformaciones, controles y transferencias **aceptadas de la empresa de origen**) y `materials` (US40). Fechas inclusivas en `America/Lima`, máximo **366 días**. |

Errores (todos `ApiError`): `400 INVALID_PERIOD` (falta un parámetro, fecha mal escrita, `from > to` o más de 366 días), `401 AUTHENTICATION_REQUIRED`, `403 FORBIDDEN` (`OPERATOR` y `LICENSE_OWNER`). Periodo sin datos → listas vacías y ceros. Detalle de cada indicador, del orden por `batchVersion` y del esquema: [`domain/analytics.md`](../domain/analytics.md).
