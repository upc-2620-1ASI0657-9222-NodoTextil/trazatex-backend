# Visión general de la arquitectura (Iteración 4)

Fuente: informe TF_1ASI0657_202610_9222_NodoTextil_TB1_v2, capítulo IV (4.3.4) y `workspace.dsl`.

## Contenedores

| Servicio (carpeta) | Puerto local | Base de datos | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | — | Único punto de entrada. Valida JWT RS256 (claves del JWKS de Organization & Access, en caché), rate limiting en memoria (Bucket4j), elimina las cabeceras `X-User-*` y `X-Company-*` entrantes e inyecta `X-User-Id`, `X-Company-Id`, `X-User-Role`. Rutas en `api.md`. |
| `organization-access` | 8081 | PostgreSQL `orgaccess_db` | Licencia, empresas, usuarios, invitaciones, solicitudes de administrador, privacidad B2B. Identity Provider (emite JWT RS256, publica `/.well-known/jwks.json`). Bootstrap del Dueño de Licencia. Valida RUC con Chequea.pe. |
| `production` | 8082 | PostgreSQL `production_db` | Lotes, división, transformaciones (Strategy + Factory), maquinaria, transferencias. **Única autoridad sobre la fase operativa del lote** y su retención por calidad. Endpoint interno para los comandos de Quality. |
| `quality` | 8083 | PostgreSQL `quality_db` | Controles, pruebas, fallas, estado de calidad (State). Genera el reporte PDF localmente (OpenHTMLtoPDF). Proyección local de lotes. *(Siguiente sprint.)* |
| `traceability` | 8084 | Neo4j | Genealogía (DAG), consultas hacia atrás/adelante (≤ 10 niveles), análisis de impacto ante `FailureConfirmed`, autorización B2B por nodo y atributo (ABAC). |
| `notification` | 8085 | PostgreSQL `notification_db` | Notificaciones (leída/no leída), correos por SendGrid con cola de diferidos. *(Siguiente sprint.)* |
| `analytics` | 8086 | PostgreSQL `analytics_db` | Indicadores por periodo (CQRS, solo lectura). *(Siguiente sprint.)* |
| `shared-kernel` | — | — | Librería Maven: sobre de eventos (`DomainEvent`, `BaseDomainEvent`), `ApiError`, excepciones base, filtro de idempotencia reutilizable. Sin lógica de negocio. |

Infraestructura: Apache Kafka (local: un nodo KRaft; Azure: Event Hubs nivel Standard con protocolo Kafka), PostgreSQL 16, Neo4j 5.

## Sistemas externos

| Sistema | Quién lo usa | Notas |
|---|---|---|
| Chequea.pe (`GET https://api.chequea.pe/api/v1/ruc/{ruc}`, Bearer token) | organization-access | ADR-012. Caché `validated_ruc_cache`. Nunca aceptar un RUC sin validar. |
| Have I Been Pwned (k-anonymity) | organization-access | ADR-007. Timeout 2 s; si cae, se acepta con advertencia auditada. |
| Cloudinary | production, quality | ADR-007. Si cae, cola local de subida diferida. |
| SendGrid | notification | ADR-007. Si cae, tabla de correos diferidos + job de reenvío. |

PDFMonkey **ya no se usa** (ADR-011).

## Roles

`LICENSE_OWNER` (Dueño de Licencia), `COMPANY_ADMIN` (Administrador de empresa), `OPERATOR` (Operario). Ver `docs/domain/glossary.md`.

Creación de cuentas: todas por invitación (US04). El Dueño se crea con el bootstrap al iniciar `organization-access` (ADR-013): lee `TRAZATEX_OWNER_EMAIL`, `TRAZATEX_OWNER_NAME`, `TRAZATEX_LICENSE_MAX_COMPANIES`, `TRAZATEX_LICENSE_MAX_USERS_PER_COMPANY`; si no existe licencia, la crea e invita al dueño. No hay contraseñas por defecto.

## Versiones

| Pieza | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.x (último parche) |
| Spring Cloud | 2025.1.3 "Oakwood" (compatible con Boot 4.1.x) |
| PostgreSQL | 16 |
| Neo4j | 5 |
| Kafka | KRaft, un nodo en local |

Paquete raíz: `com.nodotextil.trazatex.<servicio>` (informe 5.2.3).

## Fuera de alcance

Kubernetes, service mesh, Redis, gRPC, Feign. El cliente HTTP interno es `RestClient` (o una interfaz `@HttpExchange` sobre él).
