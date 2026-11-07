> Para el despliegue completo (front, Notification, Analytics y compose), revisa [INSTRUCCIONES_DESPLIEGUE_LOCAL.md](../../../INSTRUCCIONES_DESPLIEGUE_LOCAL.md).

# Colección Bruno — recorrido completo por el Gateway (fase 03)

Colección de [Bruno](https://www.usebruno.com/) con el recorrido de la fase 03, **siempre por el Gateway** (`http://localhost:8080`):

1. El Dueño de Licencia acepta su invitación y hace login.
2. Crea una empresa con un **RUC real** (validado por Chequea.pe).
3. El primer administrador acepta su invitación y hace login.
4. El administrador invita a un operario; el operario acepta y hace login.
5. El administrador **inactiva** al operario → su token (aún vigente, 8 h) recibe `401 USER_INACTIVE` en pocos segundos (RF-012).
6. Lo **reactiva** → el mismo token vuelve a servir.

Los enlaces de invitación salen de **Kafka UI** (el envío real del correo es del servicio `notification`, siguiente sprint): cada paso "Enlace de invitación (Kafka UI)" lee del tópico `orgaccess.invitation` el último `InvitationCreated` del correo y guarda el token. A mano: Kafka UI → Topics → `orgaccess.invitation` → Messages → el último segmento del `acceptUrl`.

## Antes de empezar

```bash
docker compose up -d                       # Kafka, Kafka UI, PostgreSQL, Neo4j
./mvnw -pl api-gateway,organization-access -am verify    # construye los jars (y corre las pruebas)
set -a; . ./.env; set +a
SPRING_PROFILES_ACTIVE=local java -jar organization-access/target/organization-access-0.1.0-SNAPSHOT-exec.jar    # :8081
SPRING_PROFILES_ACTIVE=local java -jar api-gateway/target/api-gateway-0.1.0-SNAPSHOT-exec.jar                     # :8080 (en otra terminal)
```

El Gateway necesita Kafka y organization-access; no está listo (`/actuator/health/readiness`) hasta leer `orgaccess.user` completo.

## Cómo usarla

1. Bruno → **Open Collection** → carpeta `docs/http`.
2. Entorno **local** (`environments/local.bru`): completa `ownerEmail` con el valor de `TRAZATEX_OWNER_EMAIL` de tu `.env` (el de la invitación del Dueño) y las tres contraseñas **secretas** `ownerPassword`, `adminPassword`, `operatorPassword` (las eliges tú: mínimo 8 caracteres y que no figuren en filtraciones, HIBP las comprueba; Bruno no las guarda en el archivo).
3. **Run collection** (Runner), en el orden de las carpetas `00` a `05`. La carpeta `06` (límites) va aparte.

Por línea de comandos (el CLI de Bruno corre las mismas peticiones y aserciones):

```bash
cd docs/http
npx @usebruno/cli run --env local -r --tests-only \
  --env-var "ownerEmail=$TRAZATEX_OWNER_EMAIL" \
  --env-var "ownerPassword=..." --env-var "adminPassword=..." --env-var "operatorPassword=..."
```

Resultado esperado de las carpetas `00`–`05`: 31 peticiones, 53 aserciones y 4 pruebas en verde (con `@usebruno/cli` 4.2.1). La colección completa con la `06` y la `07` suma 57 peticiones, 101 aserciones y 6 pruebas. **Con la `08` (fase 05, abajo): 77 peticiones, 126 aserciones y 33 pruebas en verde** (CLI 4.2.1; corrida de cierre de la fase 05, sin pasos manuales).

## Qué comprueba cada carpeta

| Carpeta | Comprueba |
|---|---|
| `00-gateway` | Readiness; `/internal/**` → 404; ruta protegida sin token → 401; `X-User-*` falsificadas sin token → 401 |
| `01-dueno` | Enlace en Kafka UI, aceptar (pública), login, `GET /api/license` |
| `02-empresa` | Crear empresa con `Idempotency-Key`; **repetir con la misma clave** (misma respuesta, sin segunda empresa ni segunda consulta); listar; RUC con dígito verificador inválido → 400 |
| `03-administrador` | Enlace, aceptar, login |
| `04-operario` | Invitar (administrador), enlace, aceptar, login; el token llega al servicio (403: no es administrador, prueba que el Gateway inyectó su identidad); una cabecera `X-User-Role` falsificada se pisa |
| `05-revocacion` | Inactivar → `401 USER_INACTIVE`; el login se rechaza; reactivar → el mismo token vuelve a servir; login de nuevo |
| `06-limites` | Opcional: login con credenciales malas, repetido, hasta el `429` con `Retry-After` |
| `07-production` | Fase 04, ver abajo: empresa B, flujo de lotes por el Gateway, evidencias, comando interno y empresa inactiva |
| `08-traceability` | Fase 05, ver abajo: genealogía por el Gateway vista por cada empresa, privacidad B2B que cambia en vivo, búsquedas, errores y `Cache-Control: no-store` |
| `09-quality` | Fase 06, ver abajo: control, pruebas, finalizar, falla, reevaluación, descarte, evidencias y reporte PDF por el Gateway |
| `10-notification` | Fase 07, ver abajo: la bandeja de la empresa que recibe y de la que origina una transferencia, lectura idempotente, aislamiento por empresa y rol, paginación y errores |
| `11-analytics` | Fase 08, ver abajo: indicadores de EP05 por empresa, periodo y errores (`INVALID_PERIOD`), y que la empresa nunca sale de un parámetro |

## Cosas a tener en cuenta

- **El RUC gasta una consulta de Chequea.pe**, salvo que ese RUC esté en la caché de 24 h de organization-access (`validated_ruc_cache`). El RUC por defecto (`20131312955`, ACTIVO y HABIDO) es el de `organization-access/src/test/resources/chequea/ruc-activo-habido.json`.
- **No se puede repetir sobre la misma base**: la invitación del Dueño y las demás quedan aceptadas, el RUC ya está registrado y el correo queda con cuenta. Para repetir: `docker compose down -v` (borra **todos** los datos; al arrancar organization-access el bootstrap crea otra invitación para el Dueño), o cambia `adminEmail`, `operatorEmail`, `ruc` y `businessName` en el entorno (el Dueño no se puede cambiar: su invitación se consume).
- **3 logins fallidos seguidos de un mismo correo lo bloquean 15 minutos** (RNF-06, por correo y exista o no la cuenta). Si un paso falla a mitad y reintentas, espera o usa otros correos.
- Los pasos de Kafka UI esperan 2 s antes de leer: el enlace sale a Kafka cuando el relay del outbox lo publica (~1 s después del commit); sin la espera se leería el enlace de una ejecución anterior. Los pasos de revocación esperan 2,5 s por lo mismo.
- Los límites de peticiones (`login` 60/min por IP, etc.) valen también para esta colección: no la ejecutes en bucle.

## Carpeta `07-production` (fase 04)

Va **después** de `00`–`05` en la misma corrida del Runner (usa `ownerToken`, `adminToken`, `operatorToken` y `operatorId` que dejan esas carpetas) y con **Production levantado** en el 8082 (`SPRING_PROFILES_ACTIVE=local java -jar production/target/production-0.1.0-SNAPSHOT-exec.jar`, sin `CLOUDINARY_URL`). Antes de correrla, en el entorno **local** de Bruno: `rucB` (un RUC **real ACTIVO y HABIDO, distinto de `ruc`**: gasta una consulta de Chequea.pe) y las dos contraseñas secretas nuevas `adminBPassword` y `operatorBPassword`.

| Pasos | Comprueba |
|---|---|
| `01`–`09` | Empresa B (Dueño), su administrador y su operario: invitación en Kafka UI, aceptación y login |
| `10`–`14` | Operario de A: registrar un lote de 100 kg, dividir en 40 + 60, maquinaria (administrador), transformación (iniciar y finalizar con desperdicio) |
| `15`–`18` | Transferencia parcial → `400 PARTIAL_TRANSFER_NOT_ALLOWED`; transferir a B; el administrador de B acepta; B ve el lote |
| `19`–`20` | Evidencia → `201 PENDING` (sin Cloudinary); archivo de 40 MB → `413 FILE_TOO_LARGE` como `ApiError` |
| `21`–`22` | Comando interno: por el Gateway `404`; directo a Production (`productionUrl`) `200` |
| `23`–`25` | Empresa B inactiva → su operario recibe `409 COMPANY_INACTIVE`; se reactiva (la colección queda repetible) |

**Archivo grande (paso 20).** No está en el repositorio. Créalo antes: `head -c 41943040 /dev/urandom > docs/http/07-production/files/grande.jpg && printf '\xff\xd8\xff\xe0' | dd of=docs/http/07-production/files/grande.jpg conv=notrunc` (40 MB con cabecera JPEG). Bórralo al terminar.

Los eventos de `production.batch`, `production.transformation` y `production.transfer` se ven en Kafka UI (http://localhost:8090). La inactivación de un usuario y el `401 USER_INACTIVE` por el Gateway ya los cubre la carpeta `05`.

> Estado (2026-10-21): la colección completa (`00`–`07`) se corrió de punta a punta con `bru run -r` (`@usebruno/cli` 4.2.1) sobre una base recién creada con `docker compose down -v`, con los dos RUC de arriba y los tres servicios reales: **57 peticiones, 101 aserciones y 6 pruebas, todas en verde**, en unos 28 s.

## Carpeta `08-traceability` (fase 05)

Va **después** de `07` en la misma corrida (usa los lotes y los tokens que dejan `02`–`07`: `lotId`, `child40Id`, `child60Id`, `outputId`, `transformationId`, `companyId`, `companyBId`, `operatorToken`, `adminToken`, `operatorBToken`, `ownerToken`) y con **Traceability levantado** en el 8084 (`SPRING_PROFILES_ACTIVE=local java -jar traceability/target/traceability-0.1.0-SNAPSHOT-exec.jar`, con el mismo `.env`; el Gateway lo enruta con `TRACEABILITY_URL`, por defecto `http://localhost:8084`). Todo va **por el Gateway**; Traceability armó el grafo solo con los eventos de la fase 04 (nada se carga a mano).

| Pasos | Comprueba |
|---|---|
| `01`–`02` | A busca su lote por el Traceability ID exacto (espera 4 s a los eventos) y por prefijo: solo los suyos (el hijo de 60 kg y la salida ya son de B) |
| `03`–`04` | Genealogía hacia atrás de la salida y hacia adelante del lote madre, vistas por A: sus lotes completos y los de B solo con lo siempre compartido; niveles, relaciones y la bifurcación |
| `05` | B ve la genealogía completa de su salida con el historial de transferencias; lo de A (que no compartió nada) sin ningún valor privado en la respuesta |
| `06`–`09` | El administrador de A comparte proveedor, origen y maquinaria (`PUT /api/privacy-settings`): B los ve **en lotes que ya existían**; A vuelve a hacer privados proveedor y maquinaria: B deja de verlos y el origen sigue compartido |
| `10`–`13` | Búsqueda por QR (cualquier empresa), por prefijo (solo lotes propios: B no lista los de A) y por Traceability ID exacto (sí, como compartido) |
| `14`–`20` | Lote inexistente → el mismo 404 sin pistas; `batchId` mal formado → 400; búsqueda sin criterios → 400; sin token → 401 y el Dueño de Licencia → 403, ambos con `no-store`; una `X-Company-Id` falsificada se pisa; un `POST` → 405 |

Al terminar, A queda con el **origen compartido** (el proveedor y la maquinaria privados): es el estado con el que se probó la reconstrucción del grafo (ver `docs/progress.md`). La colección no es repetible sobre la misma base (usa los datos de `07`); para repetirla hace falta otra base limpia.

## Carpeta `09-quality` (fase 06)

Va **después** de `08` en la misma corrida (usa `operatorToken`, `adminToken` y `adminBToken` de las carpetas anteriores y el archivo `07-production/files/foto.jpg`) y con **Quality levantado** en el 8083 (`SPRING_PROFILES_ACTIVE=local java -jar quality/target/quality-0.1.0-SNAPSHOT-exec.jar`, con el mismo `.env`, `PRODUCTION_URL` apuntando a Production y **sin** `CLOUDINARY_URL`; el Gateway lo enruta con `QUALITY_URL`, por defecto `http://localhost:8083`). Todo va **por el Gateway**; Quality conoce los lotes solo por los eventos de Production (los pasos que inician un control esperan 4 s a que llegue `BatchRegistered`). Registra sus propios lotes (`q1`, `q2`), así que no depende de los de `07`.

| Pasos | Comprueba |
|---|---|
| `01`–`05` | Registrar un lote (operario); el administrador de A inicia el control (Evaluación; Production confirma y el lote pasa a control de calidad); un segundo control sobre el mismo lote → `409 CONTROL_ALREADY_ACTIVE`; el operario → `403`; el administrador de B → `404 CONTROL_NOT_FOUND` |
| `06`–`08` | Pruebas conforme y con falla; el reporte de un control sin finalizar → `409 CONTROL_NOT_FINISHED` (con `no-store`) |
| `09`–`12` | Evidencias (US43): foto al control (`201`; `PENDING` sin Cloudinary), un archivo que no es imagen → `400 UNSUPPORTED_FILE_FORMAT` con los formatos, el operario → `403`, sin `controlId` ni `failureId` → `400 EVIDENCE_OWNER_REQUIRED` |
| `13`–`17` | Finalizar → `FINISHED`, `FAILED` y nace la Falla; **reporte PDF** (US44: `application/pdf`, `attachment`, `Cache-Control: no-store`); el reporte desde otra empresa → `404`, el operario → `403`, sin token → `401` |
| `18`–`22` | Completar la falla; foto a la falla; listados de evidencias (solo las de la propia empresa; B → `404`) |
| `23`–`27` | Enviar a reevaluación; iniciar la Reevaluación, prueba conforme, finalizar → el lote vuelve a Conforme y la Falla queda Resuelta; reporte de la reevaluación |
| `28`–`35` | Segundo lote: control con falla; descartar sin causa ni cantidad → `409 FAILURE_INFO_INCOMPLETE`; completar y descartar → Falla `CONFIRMED`; Production ve el lote `DISCARDED` |

**Ver el PDF.** Bruno comprueba las cabeceras del reporte (`14` y `27`); para abrirlo, usa «Save response» en esa petición o `curl -H "Authorization: Bearer $TOKEN" -o reporte.pdf http://localhost:8080/api/quality/controls/<id>/report`.

> Estado (2026-10-23): corrida completa `00`–`09` sobre una base recién creada: 111 peticiones, 107 en verde (la `09` falló en 24–27 por un `409` al iniciar la reevaluación, probablemente una proyección aún atrasada); la `09` sola, con los tres logins delante (`03-administrador/04-login.bru 04-operario/05-login.bru 07-production/05-login-admin-b.bru 09-quality`), pasó 38/38 (el paso 24 reintenta el inicio hasta 10 s ante 409 por proyección atrasada). `06-limites` no corre con `--tests-only`. Detalle en `docs/progress.md`.

## Carpetas `10-notification` y `11-analytics` (fases 07 y 08)

Numeración: la carpeta `09` ya es Quality, así que Notification es `10` y Analytics `11` (el prompt de la fase 07 decía `09-notification`, que chocaba con `09-quality`; ya está corregido).

- **`10-notification`** (16 peticiones). Depende de `07-production`: usa `transferId` (paso 16), la transferencia que A inició hacia B y que B aceptó (paso 17), y los tokens `adminToken`, `adminBToken` y `operatorBToken` que dejaron los logins. Comprueba que B (destino) recibe `TRANSFER_RECEIVED` y A (origen) `TRANSFER_ACCEPTED`, que ninguna ve la de la otra, que marcar como leída es idempotente, que una ajena responde `404` igual que una inexistente, y la paginación (`size` recortado a 100, `INVALID_PAGINATION`, `INVALID_PARAMETER`). El primer paso espera 2,5 s a que Notification lea el tópico (consistencia eventual).
- **`11-analytics`** (10 peticiones). El periodo es lo que va del año en America/Lima (se calcula en un script previo: nunca pasa de 366 días). Comprueba los cuatro bloques de EP05, que la empresa B no recibe el documento de A ni cambia con un `companyId` en la URL, `403` para el operario, `401` sin token, los cuatro casos de `INVALID_PERIOD` y que `/api/analytics` (sin `/summary`) ya no existe.
- Ninguna petición guarda un token o una contraseña: solo usan las variables de ejecución que dejan los logins de las carpetas anteriores.
- Estado: **escritas, sin correr** (hace falta el stack completo con Kafka y los siete servicios). Corren con el mismo comando de abajo, que ya recorre las carpetas en orden.

### Correrla completa por línea de comandos

Con la infraestructura y los cuatro servicios arriba (`organization-access`, `api-gateway`, `production` —**sin** `CLOUDINARY_URL`— y `traceability`), el archivo `files/grande.jpg` creado (ver arriba) y una base limpia:

```bash
set -a; . ./.env; set +a                      # TRAZATEX_OWNER_EMAIL
cd docs/http
npx @usebruno/cli run -r --env local \
  --env-var "ownerEmail=$TRAZATEX_OWNER_EMAIL" \
  --env-var "ownerPassword=$OWNER_PW" --env-var "adminPassword=$ADMIN_PW" --env-var "operatorPassword=$OP_PW" \
  --env-var "adminBPassword=$ADMINB_PW" --env-var "operatorBPassword=$OPB_PW"
```

Las cinco contraseñas las eliges tú (variables de shell; mínimo 8 caracteres y que no figuren en filtraciones). **No uses `--reporter-json` ni guardes la salida sin revisarla**: el JSON incluye las peticiones con tokens y contraseñas.

- La carpeta `06` entra en el `-r` (una sola petición: un login fallido de un correo que no existe) y no estorba a la `07`.
- **El CLI puede no terminar tras imprimir el resumen** (se vio en la corrida completa, con el paso `20`; con peticiones sueltas sí sale). El resultado ya es definitivo: córtalo con Ctrl+C. En automatización, ponle un tiempo máximo. No se ha confirmado la causa (se sospecha de la subida de 40 MB cortada por el 413). Se volvió a ver en la corrida de cierre de la fase 05 (77 peticiones en 42 s y el CLI sin salir); para detectar el final en una automatización, busca la fila `Requests` de la tabla del resumen (no hay una línea `Requests:`). La salida del CLI, sin `--reporter-json`, no incluye contraseñas ni tokens (se comprobó buscándolos).
- Para repetirla hace falta otra base limpia (`docker compose down -v`) y las 2 consultas a Chequea.pe de los RUC.
