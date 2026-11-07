/**
 * Plazos del cliente por tipo de operación (ADR-010, enmienda del 2026-11-04). Esta tabla es el ÚNICO sitio donde se
 * clasifican las rutas: `ApiService` solo llama a `timeoutFor()`. Una ruta que ninguna regla reconoce usa
 * `DEFAULT_TIMEOUT_MS`. Al añadir una ruta al backend hay que decidir aquí si merece un plazo propio y registrarla en
 * `service-routes.ts` (la prueba `timeouts.spec.ts` y `npm run check:routes` fallan si falta).
 *
 * Un plazo agotado NO aborta la operación en el servidor: el cliente deja de esperar, muestra «No se pudo confirmar la
 * respuesta» y conserva los datos y la `Idempotency-Key` pendiente. El reintento reutiliza la misma clave (el servidor
 * las guarda 24 h) y por eso es seguro repetirlo.
 */
export const DEFAULT_TIMEOUT_MS = 2500;

export interface TimeoutRule {
  /** Nombre corto, también usado por las pruebas. */
  readonly name: string;
  readonly ms: number;
  /** Por qué ese plazo: el límite del colaborador más lento que interviene (ver `docs/architecture`). */
  readonly reason: string;
  /** Métodos a los que aplica; sin definir, a todos. */
  readonly methods?: readonly string[];
  /** Ruta del Gateway sin `/api` y sin query. Sin definir, no se mira la ruta. */
  readonly path?: RegExp;
  /** Solo cuerpos `multipart/form-data` (`FormData`). */
  readonly multipart?: true;
}

/** Orden = prioridad: gana la primera regla que coincide. */
export const TIMEOUT_RULES: readonly TimeoutRule[] = [
  {
    name: 'subida multipart',
    ms: 30000,
    multipart: true,
    reason: 'Hasta 5 fotos de 10 MB; Cloudinary tarda hasta 8 s por archivo y la subida depende de la red del operario.',
  },
  {
    name: 'alta de empresa',
    ms: 30000,
    methods: ['POST'],
    path: /^\/companies$/,
    reason: 'Valida el RUC con Chequea.pe (3 s + circuit breaker) y envía la invitación del primer administrador.',
  },
  {
    name: 'informe PDF',
    ms: 15000,
    methods: ['GET'],
    path: /^\/quality\/controls\/[^/]+\/report$/,
    reason: 'El generador de PDF tiene un límite de 10 s en el servidor.',
  },
  {
    name: 'comando de Quality',
    ms: 8000,
    methods: ['POST', 'PUT', 'PATCH', 'DELETE'],
    path: /^\/quality\//,
    reason: 'Quality llama a Production (2 s, 2 intentos y 300 ms ≈ 4,3 s) antes de responder 202.',
  },
];

export interface ResolvedTimeout {
  readonly ms: number;
  /** Regla que decidió el plazo; `undefined` cuando cae en el plazo por defecto. */
  readonly rule?: TimeoutRule;
}

/** Ruta del Gateway sin `/api`, sin query ni barra final. */
export function normalizePath(path: string): string {
  const clean = path.split('?')[0].replace(/^\/api(?=\/)/, '');
  return clean.length > 1 ? clean.replace(/\/+$/, '') : clean;
}

export function timeoutFor(method: string, path: string, body?: unknown): ResolvedTimeout {
  const verb = method.toUpperCase();
  const route = normalizePath(path);
  const multipart = typeof FormData !== 'undefined' && body instanceof FormData;
  const rule = TIMEOUT_RULES.find(candidate =>
    (!candidate.multipart || multipart) &&
    (!candidate.methods || candidate.methods.includes(verb)) &&
    (!candidate.path || candidate.path.test(route)));
  return rule ? { ms: rule.ms, rule } : { ms: DEFAULT_TIMEOUT_MS };
}
