# Convenciones

## Estructura del monorepo

```
Trazatex-microservices/
├── CLAUDE.md
├── README.md
├── pom.xml                      # POM padre (packaging pom): versiones, plugins, módulos
├── mvnw, .mvn/
├── docker-compose.yml           # Kafka (KRaft), PostgreSQL 16, Neo4j 5, Kafka UI
├── infra/postgres/init.sql      # crea orgaccess_db, production_db, quality_db, notification_db, analytics_db
├── .env.example                 # variables sin valores reales; el .env real nunca se comparte
├── shared-kernel/
├── shared-testing/              # soporte de Testcontainers (solo alcance test)
├── api-gateway/
├── organization-access/
├── production/
├── traceability/
├── quality/ notification/ analytics/   # siguiente sprint
└── docs/
```

Versiones: Java 21, Spring Boot 4.1.x y Spring Cloud 2025.1.3 (ver `docs/architecture/overview.md`).

## Paquetes dentro de cada servicio

```
com.nodotextil.trazatex.<servicio>
├── domain/
│   ├── model/          # agregados, entidades, value objects, enums
│   ├── event/          # eventos de dominio
│   ├── service/        # servicios de dominio (si hacen falta)
│   └── exception/
├── application/
│   ├── port/in/        # casos de uso (interfaces)
│   ├── port/out/       # *Port: repositorios, publicador de eventos, clientes externos
│   ├── usecase/        # implementaciones (@Service, @Transactional)
│   └── dto/            # commands y results
└── infrastructure/
    ├── web/            # @RestController, request/response, @ControllerAdvice
    ├── persistence/    # entidades JPA / nodos Neo4j, repositorios Spring Data, adaptadores *Port
    ├── messaging/      # OutboxRelay, @KafkaListener, configuración de tópicos
    ├── external/       # Chequea, HIBP, Cloudinary, SendGrid, ProductionPhaseClient
    ├── scheduling/     # jobs @Scheduled
    └── config/
```

Nombres de enums, roles y estados: los de `docs/domain/glossary.md`, sin variantes.

Reglas de dependencia (se comprueban con ArchUnit en cada servicio):
- `domain` no importa Spring, JPA, Kafka ni nada de `application`/`infrastructure`.
- `application` no importa `infrastructure`.
- Los controladores solo llaman a casos de uso.
- Ni `application` ni `infrastructure.web` importan `org.springframework.jdbc` (I11): el SQL vive en los adaptadores de `infrastructure.persistence` detrás de un puerto de salida. La regla está en el `ArchitectureTest` de **cada** módulo (los siete con servicio y el Gateway).
- Un listado que crece con los datos va **paginado** (`page` desde 0, `size` por defecto 20 y máximo 100, `{items,page,size,totalElements,totalPages}`, `400 INVALID_PAGINATION`; en `shared-kernel`: `PageRequest` y `PageResult`) y lee lo que necesita de cada fila con una consulta por página, no una por fila (I12).

## Nombres

- Puertos de salida: `<Cosa>Port` (`BatchRepositoryPort`, `RucValidatorPort`, `ProductionPhasePort`).
- Adaptadores: `<Tecnología><Cosa>Adapter` (`JpaBatchRepositoryAdapter`, `ChequeaRucValidatorAdapter`).
- Eventos: verbo en pasado, en inglés, igual que el catálogo (`BatchRegistered`).
- Tópicos: `<servicio>.<agregado>` en minúsculas.
- Errores HTTP: cuerpo `ApiError { code, message, details, timestamp }` del shared-kernel.

## Configuración

- `application.yml` con valores por defecto para local; todo lo sensible como `${VARIABLE}`.
- Perfiles: `local` (docker compose) y `azure`.
- Variables principales: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` (PEM en Base64, una línea), `JWT_KEY_ID`, `JWKS_URI`, `ORGANIZATION_ACCESS_URL`, `PRODUCTION_URL`, `TRACEABILITY_URL`, `QUALITY_URL`, `NOTIFICATION_URL`, `ANALYTICS_URL` (URLs base que usa el Gateway), `CHEQUEA_API_TOKEN`, `HIBP_ENABLED`, `CLOUDINARY_URL`, `SENDGRID_API_KEY`, `TRAZATEX_FRONTEND_URL`, `TRAZATEX_OWNER_EMAIL`, `TRAZATEX_OWNER_NAME`, `TRAZATEX_LICENSE_MAX_COMPANIES`, `TRAZATEX_LICENSE_MAX_USERS_PER_COMPANY`, `PRODUCTION_INTERNAL_URL`; solo en Azure, para el Gateway: `TRAZATEX_RATE_LIMIT_TRUST_FORWARDED_FOR` y `TRAZATEX_RATE_LIMIT_TRUSTED_HOPS` (el resto de límites se ajusta en `trazatex.rate-limit.*`). Evidencias de production (`trazatex.evidence.*`, `trazatex.cloudinary.*` y `spring.servlet.multipart.*`: 10 MB por archivo, 5 archivos y `server.tomcat.max-swallow-size` de 60 MB para que un archivo rechazado por grande reciba su `ApiError` 413 y no un corte de conexión). Opcionales de production (rendimiento mínimo por estrategia, en %; 60 / 80 / 80 por defecto): `TRAZATEX_TRANSFORMATION_MIN_YIELD_SPINNING`, `TRAZATEX_TRANSFORMATION_MIN_YIELD_WEAVING`, `TRAZATEX_TRANSFORMATION_MIN_YIELD_DYEING`. Cuando una propiedad lleva guiones (`min-yield-percent`, `rate-limit`) el *relaxed binding* de Spring Boot no la une con la variable de entorno: `application.yml` la lee con un placeholder explícito (`${NOMBRE_COMPLETO:valor}`) y una prueba lo comprueba.

## Tópicos de Kafka

El broker local (y el de Azure) tiene `auto.create.topics.enable=false`: **un tópico que nadie declaró no existe** y publicar en él falla. Cada servicio declara lo suyo con beans `NewTopic` (los crea `KafkaAdmin` al arrancar) en `infrastructure/config`:

- El **productor** declara los tópicos que publica (`<servicio>.<agregado>`, por ejemplo `production.batch`).
- El **consumidor** declara el `<tópico>.DLT` de cada tópico que consume (la DLT la publica el consumidor al agotar los reintentos, ver `KafkaConsumerErrorHandling`). Puede redeclarar el tópico de origen con los mismos parámetros: `NewTopic` es idempotente.
- Un `NewTopic` con configuración (p. ej. `.compact()` en `orgaccess.user`) solo se aplica al **crear** el tópico; para uno que ya existe organization-access activa `KafkaAdmin.setModifyTopicConfigs` (`trazatex.messaging.modify-topic-configs`, `true` por defecto, **`false` en Azure**, donde el event hub debe crearse ya con la configuración correcta).
- Local: 1 partición y 1 réplica. Misma clave de mensaje (`aggregateId`) = misma partición = orden por agregado; no cambiar el número de particiones de un tópico en uso.
- **Nadie crea tópicos ajenos.** El consumidor declara solo el `.DLT` de lo que consume, nunca el tópico de origen cuando es de otro servicio, y suscribe con `allow.auto.create.topics=false`. Si el tópico ajeno puede no existir todavía (p. ej. `quality.control`, de Quality), el consumidor debe arrancar sin él: **no se suscribe a un tópico ausente** (el cliente de Kafka escribe un `UNKNOWN_TOPIC_OR_PARTITION` por segundo, sin parar); un vigilante con `listTopics` arranca el contenedor cuando aparece (`autoStartup=false`). El vigilante es el `TopicWatcher` de `shared-messaging` (no programa nada por sí mismo: el servicio lo crea con sus `TopicWatcher.Source(listenerId, topic)` y lo llama desde su propio `@Scheduled`, como el `OutboxRelay`).
- **Retención de `orgaccess.company`**: sin borrado por tiempo (`retention.ms=-1`), porque un consumidor nuevo lo lee desde el inicio para conocer las empresas ya registradas; organization-access lo declara así y, con `modify-topic-configs=true`, también lo corrige en un Kafka local donde ya existía. No se puede compactar (tres tipos de evento comparten la clave `companyId`).
- Un grupo de consumidores nuevo lee desde el principio (`auto.offset.reset=earliest`); un grupo por tópico consumido (`<servicio>.<tópico con guion>`), para que un tópico ausente o con rebalanceos no afecte a los demás.
- Un `NewTopic` olvidado se nota rápido: el `OutboxRelay` deja la fila `PENDING`, sube `attempts` y escribe el error en `last_error`.

Conexión desde la Mac: `KAFKA_BOOTSTRAP_SERVERS=localhost:9092` (listener EXTERNAL). Desde un contenedor de la red de compose: `kafka:29092` (listener INTERNAL).

## Pruebas

| Tipo | Herramienta | Qué cubre |
|---|---|---|
| Unitarias | JUnit 5, Mockito | Dominio y casos de uso (sin Spring) |
| Propiedades (casos generados) | JUnit `@ParameterizedTest` + `@MethodSource` con `java.util.Random` de **semilla fija** (≥ 1000 casos) | Invariantes de balance de masa y división de lotes. Sin jqwik (ver `progress.md`): la semilla fija hace reproducible cualquier fallo |
| Arquitectura | ArchUnit | Reglas de dependencia hexagonal |
| Integración | Spring Boot Test + **Testcontainers** (PostgreSQL, Kafka, Neo4j) | Repositorios, outbox → Kafka, consumidores idempotentes, comando interno |
| Aceptación | Cucumber | Escenarios Gherkin de las historias del informe |
| Contrato HTTP | MockMvc / WebTestClient | Códigos y cuerpos de los endpoints |

**Escenarios que describen el efecto de otro servicio.** Si un escenario describe un efecto que ejecuta otro servicio (p. ej. US33, escenario 3: Quality cambia la calidad y Production retiene el lote), el paso **verifica el evento que lo dispara y no se reescribe el escenario** (su texto es el de `user-stories.md` y una prueba lo compara). El paso lo explica en su Javadoc y la nota va también en el `.md` del contexto; el escenario completo se ejecuta cuando existan los servicios que lo realizan.

**Kafka en las pruebas.** Toda prueba con Kafka real usa `TrazatexKafkaContainer` (módulo `shared-testing`, dependencia de alcance `test`) y nunca `new KafkaContainer(...)`: es la misma imagen exacta del `docker-compose.yml` y, como él, **no crea tópicos solos** (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`). Con el valor por omisión de la imagen el broker crea el tópico al primer uso y una prueba que olvidara declararlo —o que creara uno ajeno sin querer— pasaría sin probar lo que dice. **Neo4j en las pruebas**: igual, `TrazatexNeo4jContainer` (misma imagen y misma memoria que el compose: heap de 512 MB y 256 MB de caché de páginas; con los valores por omisión Neo4j usa varios GB y una prueba de rendimiento pasaría con una holgura que el entorno real no tiene), nunca `new Neo4jContainer(...)`; `TrazatexNeo4jContainerTest` lo compara con el compose. La de Kafka: su propia prueba (`TrazatexKafkaContainerTest`) comprueba la configuración del broker, que publicar en un tópico no declarado falla y que la versión de la imagen coincide con la del compose.

Pruebas obligatorias por servicio: un evento duplicado no aplica el efecto dos veces; una misma `Idempotency-Key` no crea dos registros; el outbox publica tras el commit y no si hay rollback.

## Control de versiones

No se usa git por ahora: no ejecutes `git init`, `git add`, `git commit`, `git push` ni crees ramas. Antes de cada fase el usuario hace una copia de la carpeta.
