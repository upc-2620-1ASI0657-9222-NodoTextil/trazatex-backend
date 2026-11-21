export const DEFAULT_TIMEOUT_MS = 2500;

export interface TimeoutRule {
  readonly name: string;
  readonly ms: number;
  readonly reason: string;
  readonly methods?: readonly string[];
  readonly path?: RegExp;
  readonly multipart?: true;
}

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
  readonly rule?: TimeoutRule;
}

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
