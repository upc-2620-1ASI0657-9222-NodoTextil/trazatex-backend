// Ronda 5c: `test` con un `request` que reintenta las respuestas 429 del Gateway (cubo de 120 solicitudes/min por usuario) respetando Retry-After.
// El ritmo de la suite (misma cuenta en muchos specs, sondeos con expect.poll) agota ese cubo sin que haya un defecto; el límite no se toca.
// No lo usan architecture.spec.ts ni zz-rate-limit.spec.ts, que comprueban justamente que el Gateway devuelve 429.
import { test as base, APIRequestContext, APIResponse } from '@playwright/test';

export * from '@playwright/test';

const VERBS = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'fetch']);

function withRetry(request: APIRequestContext): APIRequestContext {
  return new Proxy(request, {
    get(target, prop) {
      const value = Reflect.get(target, prop, target);
      if (typeof value !== 'function') return value;
      if (typeof prop !== 'string' || !VERBS.has(prop)) return value.bind(target);
      return async (...args: unknown[]) => {
        for (let attempt = 0; ; attempt++) {
          const response: APIResponse = await value.apply(target, args);
          if (response.status() !== 429 || attempt >= 6) return response;
          const seconds = Number(response.headers()['retry-after']) || 2;
          await new Promise(resolve => setTimeout(resolve, (Math.min(seconds, 30) + 1) * 1000));
        }
      };
    },
  });
}

// Pausa antes de cada prueba (TRAZATEX_PACE_MS, 0 por defecto): deja que se rellenen los cubos del Gateway (120/min por usuario) y de login (60/min por IP)
// entre pruebas que navegan mucho. Las respuestas 429 del NAVEGADOR no se pueden reintentar desde aquí; por eso se espacia el ritmo.
const PACE_MS = Number(process.env['TRAZATEX_PACE_MS'] ?? 0);
export const test = base.extend<{ pace: void }>({
  request: async ({ request }, use) => { await use(withRetry(request)); },
  pace: [async ({}, use) => { if (PACE_MS > 0) await new Promise(resolve => setTimeout(resolve, PACE_MS)); await use(); }, { auto: true }],
});
