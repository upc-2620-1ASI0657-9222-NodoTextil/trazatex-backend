workspace "TrazaTex" "Plataforma B2B de trazabilidad textil - Iteracion 4 (Comunicacion entre microservicios, continuidad ante desconexion y reduccion de dependencias externas)" {

    !impliedRelationships true

    model {

        # ===============================================================
        # Actores
        # ===============================================================
        operario = person "Operario de Transformacion Textil" "Registra lotes, transformaciones y transferencias en planta."
        administrador = person "Administrador de Empresa" "Gestiona controles de calidad, fallas, usuarios, maquinaria y privacidad B2B de su empresa."
        duenoLicencia = person "Dueno de Licencia" "Administra la cadena: empresas, primeros administradores y solicitudes de administradores."

        # ===============================================================
        # Sistemas externos (R10 revisada en Iteracion 4)
        #  - PDFMonkey se elimina: el reporte se genera localmente en Quality (ADR-011).
        #  - El RUC se valida con la API de Chequea.pe, que expone el padron de SUNAT (ADR-012).
        # ===============================================================
        chequea = softwareSystem "Chequea.pe" "API REST de consulta RUC con datos del padron de SUNAT actualizados a diario. No oficial y sin SLA." "External"
        hibp = softwareSystem "Have I Been Pwned" "Verifica si una contrasena se encuentra comprometida (k-anonymity)." "External"
        cloudinary = softwareSystem "Cloudinary" "Almacena evidencias fotograficas de lotes, transformaciones, controles y fallas." "External"
        sendgrid = softwareSystem "SendGrid" "Envia invitaciones y alertas por correo electronico." "External"

        # ===============================================================
        # Sistema TrazaTex
        # ===============================================================
        trazatex = softwareSystem "TrazaTex" "Plataforma B2B de trazabilidad de lotes en la cadena textil." {

            webapp = container "Aplicacion Web" "Interfaz para operarios, administradores y dueno de licencia. Detecta desconexion (<3 s), conserva borradores en el navegador y reintenta con la misma Idempotency-Key (RNF-05)." "React" "WebApp"

            # -----------------------------------------------------------
            # API GATEWAY
            # -----------------------------------------------------------
            apiGateway = container "API Gateway" "Punto unico de entrada. Valida JWT RS256, rechaza tokens de usuarios inactivos, aplica rate limiting y enruta a los microservicios." "Spring Cloud Gateway" {
                gwJwtFilter = component "JWT Authentication Filter" "Valida firma RS256, expiracion y claims. Rutas publicas: login y aceptacion de invitacion." "Spring Security OAuth2 Resource Server" "Inbound Adapter"
                gwRateLimiter = component "Rate Limiting Filter" "Token Bucket por usuario y empresa en rutas autenticadas, y por IP en login, invitaciones publicas y fallos de autenticacion. Contadores en la memoria del propio Gateway." "Bucket4j (en memoria)" "Application"
                gwHeaderInjector = component "Header Sanitizer & Context Injector" "Elimina las cabeceras X-User-* y X-Company-* externas e inyecta X-User-Id, X-Company-Id y X-User-Role." "GatewayFilter" "Application"
                gwRouteLocator = component "Route Locator" "Enruta por prefijo de ruta hacia cada microservicio." "Spring Cloud Gateway" "Outbound Adapter"
                gwRevocation = component "Revoked Users Consumer" "Consume UserAccountUpdated del topico compactado orgaccess.user y mantiene en memoria los usuarios inactivos. Al arrancar relee el topico antes de declararse listo." "Spring Kafka Listener" "Inbound Adapter"
                gwJwksClient = component "JWKS Client" "Obtiene y cachea las claves publicas de Organization & Access." "NimbusReactiveJwtDecoder" "Outbound Adapter"
            }

            # -----------------------------------------------------------
            # MESSAGE BROKER (ADR-004)
            # -----------------------------------------------------------
            kafka = container "Event Broker" "Un topico por tipo de agregado; clave de particion = aggregateId; retencion para replay; topicos .DLT para errores." "Apache Kafka (Azure Event Hubs, protocolo Kafka)" "Broker"

            # -----------------------------------------------------------
            # 1. ORGANIZATION & ACCESS
            # -----------------------------------------------------------
            orgAccess = container "Organization & Access" "Licencia, empresas, usuarios, invitaciones y privacidad B2B. Identity Provider (JWT RS256). Aprovisiona al Dueno de Licencia al iniciar. Valida RUC con la API de Chequea.pe." "Spring Boot / Java 21" {
                oaRest = component "Organization REST Controllers" "Licencia, empresas, usuarios, invitaciones, solicitudes de administrador, privacidad y login." "Spring MVC" "Inbound Adapter"
                oaJwks = component "JWKS Controller" "Publica las claves publicas en /.well-known/jwks.json." "Spring MVC" "Inbound Adapter"
                oaBootstrap = component "License Owner Bootstrap" "Al iniciar: si no existe licencia, la crea con los limites de las variables de entorno e invita al Dueno de Licencia. Idempotente." "ApplicationRunner" "Inbound Adapter"

                oaLicenseUc = component "License & Company Use Cases" "Consultar licencia, crear empresa (valida RUC y cupo) y cambiar su estado." "Application Service" "Application"
                oaIdentityUc = component "Identity Use Cases" "Invitar, aceptar invitacion, iniciar sesion (bloqueo tras 3 intentos), emitir JWT, gestionar usuarios y solicitudes de administrador." "Application Service" "Application"
                oaPrivacyUc = component "Privacy Configuration Use Cases" "Configura atributos compartidos/privados de la empresa (RF-015 a RF-018)." "Application Service" "Application"
                oaDomain = component "Organization Domain Model" "License, Company, User, Invitation, AdminRequest, PrivacySetting." "Aggregates / Value Objects" "Domain"

                oaPersistence = component "Organization Persistence Adapter" "Implementa los puertos de repositorio, incluida la cache de RUC validados." "Spring Data JPA" "Outbound Adapter"
                oaRucValidator = component "RUC Validator Adapter" "Implementa RucValidatorPort: digito verificador local y consulta a Chequea.pe (ACTIVO / HABIDO). Timeout 3 s, Circuit Breaker y cache local de RUC validados." "RestClient / Resilience4j" "Outbound Adapter"
                oaHibp = component "HIBP Adapter" "Implementa PasswordBreachPort con Circuit Breaker y Timeout 2 s." "Adapter / Resilience4j" "Outbound Adapter"
                oaTokenSigner = component "JWT Signer" "Firma tokens con clave privada RS256 y expone la publica." "Nimbus JOSE" "Outbound Adapter"
                oaOutbox = component "Outbox Relay" "Lee la tabla outbox y publica en Kafka (at-least-once)." "Transactional Outbox" "Outbound Adapter"
            }
            orgAccessDb = container "Organization & Access DB" "Licencia, empresas, usuarios, invitaciones, privacidad, cache de RUC validados, outbox." "PostgreSQL" "Database"

            # -----------------------------------------------------------
            # 2. PRODUCTION
            # -----------------------------------------------------------
            production = container "Production" "Lotes, composicion, divisiones, transformaciones, maquinaria y transferencias. Unica autoridad sobre la fase operativa del lote." "Spring Boot / Java 21" "Core" {
                prRest = component "Production REST Controllers" "BatchController, TransformationController, TransferController, MachineController." "Spring MVC" "Inbound Adapter"
                prInternalPhase = component "Internal Phase Controller" "POST /internal/batches/{id}/phase-transitions. Solo red interna; idempotente por requestId (controlId)." "Spring MVC" "Inbound Adapter"
                prIdempotency = component "Idempotency Filter" "Guarda Idempotency-Key + respuesta en la misma transaccion; ante una clave repetida devuelve la respuesta guardada." "HandlerInterceptor" "Inbound Adapter"
                prConsumer = component "Organization Events Consumer" "Consume CompanyRegistered y CompanyStatusChanged (proyeccion de empresas) y QualityStatusChanged (estado de calidad mostrado). Idempotente (processed_events)." "Spring Kafka Listener" "Inbound Adapter"

                prBatchUc = component "Batch Use Cases" "RegisterBatchUseCase, SplitBatchUseCase." "Application Service" "Application"
                prTransformationUc = component "Transformation Use Cases" "StartTransformationUseCase, CompleteTransformationUseCase." "Application Service" "Application"
                prTransferUc = component "Transfer Use Cases" "Iniciar, aceptar y rechazar transferencias B2B." "Application Service" "Application"
                prPhaseUc = component "Batch Phase Transition Use Case" "Valida y aplica los comandos de Quality: iniciar evaluacion, liberar, descartar y retener / liberar por calidad. Bloqueo optimista." "Application Service" "Application"
                prProjectionUc = component "Company Projection Updater" "Mantiene la proyeccion local de empresas (activa/inactiva)." "Application Service" "Application"

                prDomain = component "Production Domain Model" "Batch (fase operativa + retencion por calidad), Transformation (invariante de balance), Transfer, Machine, OperationalPhase (State), BatchFactory." "Aggregates / State / Factory Method" "Domain"
                prStrategies = component "Transformation Strategies" "TransformationStrategy + TransformationStrategyFactory (Spinning, Weaving, Dyeing...). RNF-09." "Strategy / Factory Method" "Domain"

                prPersistence = component "Production Persistence Adapter" "Lotes, transformaciones, transferencias, proyeccion de empresas, idempotency_keys, processed_events." "Spring Data JPA" "Outbound Adapter"
                prCloudinary = component "Evidence Storage Adapter" "Implementa EvidenceStoragePort; Circuit Breaker y cola local de subida diferida." "Adapter / Resilience4j" "Outbound Adapter"
                prOutbox = component "Outbox Relay" "Publica eventos del outbox en Kafka (at-least-once)." "Transactional Outbox" "Outbound Adapter"
            }
            productionDb = container "Production DB" "Lotes, composicion, transformaciones, transferencias, maquinaria, proyeccion de empresas, outbox, processed_events, idempotency_keys." "PostgreSQL" "Database"

            # -----------------------------------------------------------
            # 3. QUALITY
            # -----------------------------------------------------------
            quality = container "Quality" "Controles de calidad, pruebas, estados de calidad y fallas. Genera el reporte del control finalizado (sin servicio externo)." "Spring Boot / Java 21" "Core" {
                qaRest = component "Quality REST Controllers" "QualityControlController, FailureController, ReportController." "Spring MVC" "Inbound Adapter"
                qaIdempotency = component "Idempotency Filter" "Guarda Idempotency-Key + respuesta en la misma transaccion." "HandlerInterceptor" "Inbound Adapter"
                qaConsumer = component "Quality Events Consumer" "Consume BatchRegistered, BatchSplit, TransformationCompleted, BatchPhaseChanged, TransferAccepted (proyeccion de lotes) y PossibleDerivedFailureDetected. Idempotente." "Spring Kafka Listener" "Inbound Adapter"
                qaRecoveryJob = component "Pending Control Recovery Job" "Reintenta con el mismo requestId los controles que quedaron en INICIANDO." "@Scheduled" "Inbound Adapter"

                qaControlUc = component "Quality Control Use Cases" "StartQualityControlUseCase, RegisterTestUseCase, CompleteQualityControlUseCase." "Application Service" "Application"
                qaFailureUc = component "Failure Use Cases" "ManageFailureUseCase: completar falla, reevaluar o descartar (confirma la falla)." "Application Service" "Application"
                qaDerivedUc = component "Derived Failure Use Case" "Marca POSIBLE_FALLA_DERIVADA y solicita la retencion por calidad de cada lote afectado." "Application Service" "Application"
                qaReportUc = component "Generate Report Use Case" "GenerateQualityControlReportUseCase: arma el reporte de un control finalizado." "Application Service" "Application"
                qaProjectionUc = component "Batch Projection Updater" "Mantiene la proyeccion local de lotes (fase, empresa propietaria)." "Application Service" "Application"

                qaDomain = component "Quality Domain Model" "QualityControl, QualityTest, Failure, QualityStatus (State: CONFORME, CON_FALLA, POSIBLE_FALLA_DERIVADA)." "Aggregates / State" "Domain"

                qaPersistence = component "Quality Persistence Adapter" "Controles, pruebas, fallas, proyeccion de lotes, idempotency_keys, processed_events." "Spring Data JPA" "Outbound Adapter"
                qaPhaseClient = component "Production Phase Client" "Implementa ProductionPhasePort: comando sincrono, Timeout 2 s, Circuit Breaker, Retry seguro (idempotente). Sin fallback de exito." "RestClient / Resilience4j" "Outbound Adapter"
                qaPdf = component "PDF Report Adapter" "Implementa PdfGeneratorPort con plantilla HTML -> PDF en proceso." "Thymeleaf + OpenHTMLtoPDF" "Outbound Adapter"
                qaCloudinary = component "Evidence Storage Adapter" "Implementa EvidenceStoragePort; Circuit Breaker y cola de subida diferida." "Adapter / Resilience4j" "Outbound Adapter"
                qaOutbox = component "Outbox Relay" "Publica eventos del outbox en Kafka (at-least-once)." "Transactional Outbox" "Outbound Adapter"
            }
            qualityDb = container "Quality DB" "Controles, pruebas, fallas, proyeccion de lotes, outbox, processed_events, idempotency_keys." "PostgreSQL" "Database"

            # -----------------------------------------------------------
            # 4. TRACEABILITY
            # -----------------------------------------------------------
            traceability = container "Traceability" "Genealogia de lotes, trazabilidad bidireccional, analisis de impacto y autorizacion B2B (ABAC)." "Spring Boot / Java 21" "Core" {
                trRest = component "Lineage REST Controller" "Consulta de genealogia hacia atras y hacia adelante y busqueda por QR." "Spring MVC" "Inbound Adapter"
                trConsumer = component "Lineage Events Consumer" "Consume los eventos de lotes de Production, QualityStatusChanged, FailureConfirmed, CompanyRegistered y CompanyPrivacyUpdated. Idempotente (ProcessedEvent)." "Spring Kafka Listener" "Inbound Adapter"

                trLineageUc = component "Lineage Use Cases" "Actualiza el grafo desde eventos y resuelve GetLineageUseCase (<=10 niveles)." "Application Service" "Application"
                trImpactUc = component "Impact Analysis Use Case" "Ante FailureConfirmed recorre la genealogia y detecta lotes disponibles relacionados." "Application Service" "Application"
                trPolicy = component "B2B Authorization Policy" "Policy Enforcement Point por nodo y atributo; denegacion por defecto (RNF-07, ADR-006)." "ABAC" "Application"
                trPrivacyUc = component "Privacy Projection Updater" "Mantiene la configuracion de privacidad y la empresa propietaria de cada nodo." "Application Service" "Application"

                trDomain = component "Lineage Domain Model" "Batch, Transformation, Company; relaciones SPLIT_INTO, INPUT_OF, PRODUCED, OWNS_BATCH, TRANSFERRED_VIA; PrivacyPolicy." "Graph Model" "Domain"

                trGraph = component "Neo4j Graph Adapter" "Cypher con MERGE por traceabilityId; nodos ProcessedEvent y OutboxEvent." "Spring Data Neo4j" "Outbound Adapter"
                trOutbox = component "Outbox Relay" "Publica PossibleDerivedFailureDetected desde nodos OutboxEvent." "Transactional Outbox" "Outbound Adapter"
            }
            traceabilityDb = container "Traceability DB" "Grafo genealogico (DAG), empresa propietaria por nodo, privacidad, ProcessedEvent, OutboxEvent." "Neo4j" "Database"

            # -----------------------------------------------------------
            # 5. NOTIFICATION
            # -----------------------------------------------------------
            notification = container "Notification" "Entrega y seguimiento (leida / no leida) de invitaciones y alertas." "Spring Boot / Java 21" {
                ntRest = component "Notification REST Controller" "Lista notificaciones del usuario y las marca como leidas." "Spring MVC" "Inbound Adapter"
                ntConsumer = component "Notification Events Consumer" "Consume InvitationCreated, AdminRequestResolved, UserAccountUpdated, Transfer*, PossibleDerivedFailureDetected. Idempotente." "Spring Kafka Listener" "Inbound Adapter"
                ntRetryJob = component "Deferred Email Job" "Reenvia correos pendientes cuando SendGrid se recupera." "@Scheduled" "Inbound Adapter"

                ntUc = component "Notification Use Cases" "Resuelve destinatarios, crea la notificacion y gestiona su estado." "Application Service" "Application"
                ntDomain = component "Notification Domain Model" "Notification, DeliveryStatus, RecipientDirectory (proyeccion de usuarios)." "Aggregates" "Domain"

                ntPersistence = component "Notification Persistence Adapter" "Notificaciones, correos diferidos, directorio de destinatarios, processed_events." "Spring Data JPA" "Outbound Adapter"
                ntEmail = component "Email Adapter" "Implementa EmailSenderPort; Circuit Breaker + Retry; si falla, deja el correo como diferido." "SendGrid SDK / Resilience4j" "Outbound Adapter"
            }
            notificationDb = container "Notification DB" "Notificaciones, estado de lectura, correos diferidos, directorio de destinatarios, processed_events." "PostgreSQL" "Database"

            # -----------------------------------------------------------
            # 6. ANALYTICS
            # -----------------------------------------------------------
            analytics = container "Analytics" "Indicadores por periodo (CQRS, solo lectura). Ya no genera reportes PDF." "Spring Boot / Java 21" {
                anRest = component "Analytics REST Controller" "Consulta de indicadores por periodo." "Spring MVC" "Inbound Adapter"
                anConsumer = component "Analytics Events Consumer" "Consume eventos de Production, Quality y Traceability. Idempotente." "Spring Kafka Listener" "Inbound Adapter"

                anQueryUc = component "Indicator Queries" "Estados de calidad, fallas y causas, duraciones, merma y desperdicio." "Application Service" "Application"
                anProjectionUc = component "Read Model Projector" "Actualiza el modelo de lectura agregado a partir de los eventos." "Application Service" "Application"

                anPersistence = component "Analytics Read Model Adapter" "Tablas agregadas de lectura, processed_events." "Spring Data JPA" "Outbound Adapter"
            }
            analyticsDb = container "Analytics DB" "Modelo de lectura agregado (CQRS), processed_events." "PostgreSQL" "Database"
        }

        # =================================================================
        # RELACIONES A NIVEL CONTENEDOR (se declaran primero para que las
        # relaciones de componentes no generen duplicados implicitos)
        # =================================================================
        operario -> webapp "Usa" "HTTPS"
        administrador -> webapp "Usa" "HTTPS"
        duenoLicencia -> webapp "Usa" "HTTPS"
        sendgrid -> operario "Entrega invitaciones" "Email"
        sendgrid -> administrador "Entrega invitaciones y alertas" "Email"
        sendgrid -> duenoLicencia "Entrega invitaciones y solicitudes" "Email"

        webapp -> apiGateway "Peticiones con JWT e Idempotency-Key" "HTTPS/REST" "Sync"

        apiGateway -> orgAccess "Enruta peticiones y obtiene claves JWKS (en cache)" "HTTP interno" "Sync"
        apiGateway -> production "Enruta" "HTTP interno" "Sync"
        apiGateway -> quality "Enruta" "HTTP interno" "Sync"
        apiGateway -> traceability "Enruta" "HTTP interno" "Sync"
        apiGateway -> notification "Enruta" "HTTP interno" "Sync"
        apiGateway -> analytics "Enruta" "HTTP interno" "Sync"

        # Unica comunicacion sincrona entre microservicios (ADR-009)
        quality -> production "Comando sobre el lote: iniciar evaluacion, descartar, retener por calidad" "REST interno, idempotente, Resilience4j" "Sync,InterService"

        # Integraciones externas
        orgAccess -> chequea "Valida RUC (GET /api/v1/ruc/{ruc})" "HTTPS/REST, Bearer token, Resilience4j" "Sync"
        orgAccess -> hibp "Verifica contrasena" "HTTPS, Resilience4j" "Sync"
        production -> cloudinary "Sube evidencias" "HTTPS, Resilience4j" "Sync"
        quality -> cloudinary "Sube evidencias" "HTTPS, Resilience4j" "Sync"
        notification -> sendgrid "Envia correos" "HTTPS, Resilience4j" "Sync"

        # Kafka: los servicios publican y consumen (modelo pull)
        orgAccess -> kafka "Publica eventos de empresa, usuario, invitacion y privacidad" "Kafka" "Async"
        production -> kafka "Publica eventos de lote, transformacion y transferencia; consume eventos de empresa y de estado de calidad" "Kafka" "Async"
        quality -> kafka "Publica eventos de control y falla; consume eventos de lote y PossibleDerivedFailureDetected" "Kafka" "Async"
        traceability -> kafka "Publica PossibleDerivedFailureDetected; consume eventos de lote, transferencia, calidad, falla y privacidad" "Kafka" "Async"
        notification -> kafka "Consume eventos que requieren aviso" "Kafka" "Async"
        analytics -> kafka "Consume eventos de Production, Quality y Traceability" "Kafka" "Async"
        apiGateway -> kafka "Consume UserAccountUpdated (revocacion de usuarios inactivos)" "Kafka" "Async"

        # Persistencia (Database per Service)
        orgAccess -> orgAccessDb "Lee y escribe" "JDBC"
        production -> productionDb "Lee y escribe" "JDBC"
        quality -> qualityDb "Lee y escribe" "JDBC"
        traceability -> traceabilityDb "Lee y escribe el grafo" "Bolt"
        notification -> notificationDb "Lee y escribe" "JDBC"
        analytics -> analyticsDb "Lee y escribe" "JDBC"

        # =================================================================
        # COMPONENTES - API GATEWAY
        # =================================================================
        webapp -> gwJwtFilter "Envia peticion" "HTTPS/REST"
        gwJwtFilter -> gwJwksClient "Obtiene clave publica"
        gwJwtFilter -> gwRevocation "Rechaza tokens de usuarios inactivos"
        gwRevocation -> kafka "Consume UserAccountUpdated (orgaccess.user)" "Kafka"
        gwJwksClient -> oaJwks "GET /.well-known/jwks.json (cache)" "HTTP interno"
        gwJwtFilter -> gwRateLimiter "Pasa peticion autenticada"
        gwRateLimiter -> gwHeaderInjector "Pasa peticion"
        gwHeaderInjector -> gwRouteLocator "Peticion con cabeceras de contexto"
        gwRouteLocator -> oaRest "Enruta /api/auth, /api/license, /api/companies, /api/users, /api/invitations, /api/admin-requests, /api/privacy-settings" "HTTP interno"
        gwRouteLocator -> prRest "Enruta /api/batches, /api/machines, /api/transformations, /api/transfers" "HTTP interno"
        gwRouteLocator -> qaRest "Enruta /api/quality" "HTTP interno"
        gwRouteLocator -> trRest "Enruta /api/traceability" "HTTP interno"
        gwRouteLocator -> ntRest "Enruta /api/notifications" "HTTP interno"
        gwRouteLocator -> anRest "Enruta /api/analytics" "HTTP interno"

        # =================================================================
        # COMPONENTES - ORGANIZATION & ACCESS
        # =================================================================
        oaRest -> oaLicenseUc "Invoca"
        oaRest -> oaIdentityUc "Invoca"
        oaRest -> oaPrivacyUc "Invoca"
        oaJwks -> oaTokenSigner "Obtiene clave publica"
        oaBootstrap -> oaLicenseUc "Crea licencia si no existe"
        oaBootstrap -> oaIdentityUc "Invita al Dueno de Licencia"
        oaLicenseUc -> oaDomain "Aplica reglas"
        oaIdentityUc -> oaDomain "Aplica reglas"
        oaPrivacyUc -> oaDomain "Aplica reglas"
        oaLicenseUc -> oaRucValidator "Valida RUC (RucValidatorPort)"
        oaRucValidator -> chequea "GET /api/v1/ruc/{ruc}" "HTTPS"
        oaRucValidator -> oaPersistence "Lee / guarda cache de RUC validados"
        oaIdentityUc -> oaHibp "Verifica contrasena (PasswordBreachPort)"
        oaHibp -> hibp "Consulta prefijo SHA-1" "HTTPS"
        oaIdentityUc -> oaTokenSigner "Firma JWT"
        oaLicenseUc -> oaPersistence "Guarda agregado + evento en outbox"
        oaIdentityUc -> oaPersistence "Guarda agregado + evento en outbox"
        oaPrivacyUc -> oaPersistence "Guarda configuracion + evento en outbox"
        oaPersistence -> orgAccessDb "Lee y escribe" "JDBC"
        oaOutbox -> orgAccessDb "Lee eventos pendientes" "JDBC"
        oaOutbox -> kafka "Publica eventos de organizacion" "Kafka"

        # =================================================================
        # COMPONENTES - PRODUCTION
        # =================================================================
        prRest -> prIdempotency "Pasa por"
        prIdempotency -> prBatchUc "Invoca"
        prIdempotency -> prTransformationUc "Invoca"
        prIdempotency -> prTransferUc "Invoca"
        prInternalPhase -> prPhaseUc "Invoca"
        prConsumer -> kafka "Consume eventos de empresa y QualityStatusChanged" "Kafka"
        prConsumer -> prProjectionUc "Invoca"
        prBatchUc -> prDomain "Crea / divide lote (BatchFactory)"
        prTransformationUc -> prDomain "Finaliza transformacion (balance)"
        prTransformationUc -> prStrategies "Obtiene estrategia por tipo"
        prTransferUc -> prDomain "Aplica reglas de transferencia"
        prPhaseUc -> prDomain "Valida transicion de fase"
        prBatchUc -> prCloudinary "Sube evidencia (EvidenceStoragePort)"
        prTransformationUc -> prCloudinary "Sube evidencia"
        prCloudinary -> cloudinary "Sube con Circuit Breaker" "HTTPS"
        prBatchUc -> prPersistence "Guarda + outbox (1 transaccion)"
        prTransformationUc -> prPersistence "Guarda + outbox (1 transaccion)"
        prTransferUc -> prPersistence "Guarda + outbox (1 transaccion)"
        prPhaseUc -> prPersistence "Guarda fase + outbox (bloqueo optimista)"
        prProjectionUc -> prPersistence "Actualiza proyeccion de empresas"
        prIdempotency -> prPersistence "Lee / guarda idempotency_keys"
        prPersistence -> productionDb "Lee y escribe" "JDBC"
        prOutbox -> productionDb "Lee eventos pendientes" "JDBC"
        prOutbox -> kafka "Publica eventos de produccion" "Kafka"

        # =================================================================
        # COMPONENTES - QUALITY
        # =================================================================
        qaRest -> qaIdempotency "Pasa por"
        qaIdempotency -> qaControlUc "Invoca"
        qaIdempotency -> qaFailureUc "Invoca"
        qaRest -> qaReportUc "Invoca (lectura)"
        qaConsumer -> kafka "Consume eventos de lote y de trazabilidad" "Kafka"
        qaConsumer -> qaProjectionUc "Actualiza proyeccion"
        qaConsumer -> qaDerivedUc "PossibleDerivedFailureDetected"
        qaRecoveryJob -> qaControlUc "Reintenta controles INICIANDO"
        qaControlUc -> qaDomain "Aplica transiciones (State)"
        qaFailureUc -> qaDomain "Aplica reglas de falla"
        qaDerivedUc -> qaDomain "Marca POSIBLE_FALLA_DERIVADA"
        qaControlUc -> qaPhaseClient "Solicita START_EVALUATION / RELEASE (ProductionPhasePort)"
        qaFailureUc -> qaPhaseClient "Solicita descarte del lote"
        qaDerivedUc -> qaPhaseClient "Solicita retencion por calidad de cada lote afectado"
        qaPhaseClient -> prInternalPhase "POST /internal/batches/{id}/phase-transitions" "REST interno"
        qaControlUc -> qaCloudinary "Sube evidencia (EvidenceStoragePort)"
        qaFailureUc -> qaCloudinary "Sube evidencia"
        qaCloudinary -> cloudinary "Sube con Circuit Breaker" "HTTPS"
        qaReportUc -> qaPdf "Genera PDF (PdfGeneratorPort)"
        qaReportUc -> qaPersistence "Lee control finalizado"
        qaControlUc -> qaPersistence "Guarda + outbox"
        qaFailureUc -> qaPersistence "Guarda + outbox"
        qaDerivedUc -> qaPersistence "Guarda estado"
        qaProjectionUc -> qaPersistence "Actualiza proyeccion de lotes"
        qaIdempotency -> qaPersistence "Lee / guarda idempotency_keys"
        qaPersistence -> qualityDb "Lee y escribe" "JDBC"
        qaOutbox -> qualityDb "Lee eventos pendientes" "JDBC"
        qaOutbox -> kafka "Publica eventos de calidad" "Kafka"

        # =================================================================
        # COMPONENTES - TRACEABILITY
        # =================================================================
        trRest -> trLineageUc "GetLineageUseCase"
        trLineageUc -> trPolicy "Filtra la respuesta por nodo y atributo"
        trConsumer -> kafka "Consume eventos de lote, transferencia, calidad, falla y privacidad" "Kafka"
        trConsumer -> trLineageUc "Actualiza grafo"
        trConsumer -> trImpactUc "FailureConfirmed"
        trConsumer -> trPrivacyUc "CompanyPrivacyUpdated, TransferAccepted"
        trLineageUc -> trDomain "Usa"
        trImpactUc -> trDomain "Usa"
        trPolicy -> trDomain "Evalua PrivacyPolicy"
        trLineageUc -> trGraph "Lee / escribe nodos y relaciones"
        trImpactUc -> trGraph "Recorre genealogia + registra OutboxEvent"
        trPrivacyUc -> trGraph "Actualiza propietario y privacidad"
        trPolicy -> trGraph "Lee configuracion de privacidad"
        trGraph -> traceabilityDb "Cypher" "Bolt"
        trOutbox -> traceabilityDb "Lee OutboxEvent pendientes" "Bolt"
        trOutbox -> kafka "Publica PossibleDerivedFailureDetected" "Kafka"

        # =================================================================
        # COMPONENTES - NOTIFICATION
        # =================================================================
        ntRest -> ntUc "Invoca"
        ntConsumer -> kafka "Consume eventos" "Kafka"
        ntConsumer -> ntUc "Invoca"
        ntRetryJob -> ntUc "Reintenta diferidos"
        ntUc -> ntDomain "Usa"
        ntUc -> ntPersistence "Guarda notificacion"
        ntUc -> ntEmail "Envia correo (EmailSenderPort)"
        ntEmail -> sendgrid "Envia con Circuit Breaker + Retry" "HTTPS"
        ntPersistence -> notificationDb "Lee y escribe" "JDBC"

        # =================================================================
        # COMPONENTES - ANALYTICS
        # =================================================================
        anRest -> anQueryUc "Invoca"
        anConsumer -> kafka "Consume eventos" "Kafka"
        anConsumer -> anProjectionUc "Invoca"
        anQueryUc -> anPersistence "Consulta"
        anProjectionUc -> anPersistence "Actualiza"
        anPersistence -> analyticsDb "Lee y escribe" "JDBC"
    }

    views {

        systemContext trazatex "Contexto" "Iteration 4 - System Context" {
            include *
            autoLayout lr
        }

        container trazatex "Contenedores" "Iteration 4 - Containers diagram" {
            include *
            autoLayout tb 250 150
        }

        container trazatex "Contenedores-Sincrono" "Iteration 4 - Solo comunicacion sincrona (punto 3 del profesor)" {
            include *
            exclude kafka
            autoLayout tb 250 150
        }

        component apiGateway "Componentes-ApiGateway" "Iteration 4 - API Gateway components" {
            include *
            exclude "orgAccess -> kafka"
            exclude "production -> kafka"
            exclude "quality -> kafka"
            exclude "traceability -> kafka"
            exclude "notification -> kafka"
            exclude "analytics -> kafka"
            exclude "quality -> production"
            autoLayout lr
        }
        component orgAccess "Componentes-OrganizationAccess" "Iteration 4 - Organization & Access components (hexagonal)" {
            include *
            exclude "apiGateway -> kafka"
            autoLayout lr
        }
        component production "Componentes-Production" "Iteration 4 - Production components (hexagonal)" {
            include *
            exclude "apiGateway -> kafka"
            exclude "quality -> kafka"
            exclude "quality -> cloudinary"
            exclude "apiGateway -> quality"
            autoLayout lr
        }
        component quality "Componentes-Quality" "Iteration 4 - Quality components (hexagonal)" {
            include *
            exclude "apiGateway -> kafka"
            exclude "apiGateway -> production"
            exclude "production -> cloudinary"
            exclude "production -> kafka"
            autoLayout lr
        }
        component traceability "Componentes-Traceability" "Iteration 4 - Traceability components (hexagonal)" {
            include *
            exclude "apiGateway -> kafka"
            autoLayout lr
        }
        component notification "Componentes-Notification" "Iteration 4 - Notification components (hexagonal)" {
            include *
            exclude "apiGateway -> kafka"
            autoLayout lr
        }
        component analytics "Componentes-Analytics" "Iteration 4 - Analytics components (hexagonal)" {
            include *
            exclude "apiGateway -> kafka"
            autoLayout lr
        }

        # -------------------------------------------------------------
        # Vistas dinamicas (Iteracion 4)
        # -------------------------------------------------------------
        dynamic trazatex "Dinamica-1-Bootstrap" "Aprovisionamiento del primer usuario: Dueno de Licencia (punto 4)" {
            orgAccess -> orgAccessDb "Al iniciar: si no existe licencia, la crea con las variables de entorno y registra la invitacion del Dueno + evento en outbox"
            orgAccess -> kafka "Publica InvitationCreated"
            notification -> kafka "Consume InvitationCreated"
            notification -> sendgrid "Envia correo de invitacion"
            sendgrid -> duenoLicencia "Entrega enlace de invitacion"
            duenoLicencia -> webapp "Abre el enlace y define su contrasena"
            webapp -> apiGateway "POST /api/invitations/{token}/accept (ruta publica)"
            apiGateway -> orgAccess "Enruta"
            orgAccess -> hibp "Verifica que la contrasena no este comprometida"
            orgAccess -> orgAccessDb "Crea la cuenta LICENSE_OWNER y marca la invitacion como Aceptada"
            autoLayout lr
        }

        dynamic trazatex "Dinamica-2-InicioControl" "Inicio de control de calidad con comando sincrono Quality -> Production (punto 3)" {
            administrador -> webapp "Inicia control sobre un lote"
            webapp -> apiGateway "POST /api/quality/batches/{batchId}/controls + Idempotency-Key"
            apiGateway -> quality "Enruta con X-User-Id / X-Company-Id"
            quality -> qualityDb "Valida contra la proyeccion y registra el control en INICIANDO"
            quality -> production "POST /internal/batches/{id}/phase-transitions {START_EVALUATION, requestId=controlId}"
            production -> productionDb "Verifica fase AVAILABLE, cambia a IN_QUALITY_CONTROL y escribe BatchPhaseChanged en outbox (bloqueo optimista)"
            quality -> qualityDb "Marca el control EN_CURSO y guarda la respuesta de la Idempotency-Key"
            production -> kafka "Publica BatchPhaseChanged"
            traceability -> kafka "Consume BatchPhaseChanged"
            autoLayout lr
        }

        dynamic trazatex "Dinamica-3-ReintentoIdempotente" "Desconexion durante el registro de un lote y reintento sin duplicar (punto 2, RNF-05)" {
            operario -> webapp "Registra un lote"
            webapp -> apiGateway "POST /api/batches + Idempotency-Key K1 (la red se corta antes de recibir la respuesta)"
            apiGateway -> production "Enruta"
            production -> productionDb "Guarda lote + evento en outbox + K1 con su respuesta, en una sola transaccion"
            webapp -> apiGateway "Al detectar la red: reintenta con la misma K1"
            apiGateway -> production "Enruta"
            production -> productionDb "Encuentra K1 y devuelve la respuesta guardada: no crea un segundo lote"
            autoLayout lr
        }

        dynamic trazatex "Dinamica-4-FallaDerivada" "Falla confirmada y deteccion de posibles fallas derivadas" {
            administrador -> webapp "Decide descartar el lote con falla"
            webapp -> apiGateway "PUT /api/quality/failures/{failureId} {decision: DISCARD} + Idempotency-Key"
            apiGateway -> quality "Enruta"
            quality -> production "Comando de descarte del lote"
            quality -> qualityDb "Confirma la falla y escribe FailureConfirmed en outbox"
            quality -> kafka "Publica FailureConfirmed"
            traceability -> kafka "Consume FailureConfirmed"
            traceability -> traceabilityDb "Recorre la genealogia (<=10 niveles) y registra PossibleDerivedFailureDetected en outbox"
            traceability -> kafka "Publica PossibleDerivedFailureDetected"
            quality -> kafka "Consume PossibleDerivedFailureDetected"
            quality -> production "Comando de retencion por calidad para cada lote afectado"
            notification -> kafka "Consume PossibleDerivedFailureDetected"
            notification -> sendgrid "Alerta a los administradores responsables"
            autoLayout lr
        }

        branding {
            font "Arial"
        }

        styles {
            element "Person" {
                shape person
                background #08427b
                color #ffffff
            }
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            element "External" {
                background #999999
                color #ffffff
            }
            element "Optional" {
                border dashed
                opacity 70
            }
            element "Container" {
                background #438dd5
                color #ffffff
            }
            element "Core" {
                background #0f6e56
                color #ffffff
            }
            element "WebApp" {
                shape WebBrowser
            }
            element "Broker" {
                shape Pipe
                background #6c6c6c
                color #ffffff
            }
            element "Database" {
                shape Cylinder
            }
            element "Component" {
                background #85bbf0
                color #000000
            }
            element "Inbound Adapter" {
                background #f2d7a6
                color #000000
            }
            element "Application" {
                background #9fd3c7
                color #000000
            }
            element "Domain" {
                shape Hexagon
                background #2e7d5b
                color #ffffff
            }
            element "Outbound Adapter" {
                background #c7b8e6
                color #000000
            }
            relationship "Relationship" {
                dashed false
                width 260
                fontSize 22
            }
            relationship "Async" {
                dashed true
                color #b05f00
            }
            relationship "InterService" {
                color #c0392b
                thickness 4
            }
        }
    }
}
