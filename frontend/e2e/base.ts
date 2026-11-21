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

const PACE_MS = Number(process.env['TRAZATEX_PACE_MS'] ?? 0);
export const test = base.extend<{ pace: void }>({
  request: async ({ request }, use) => { await use(withRetry(request)); },
  pace: [async ({}, use) => { if (PACE_MS > 0) await new Promise(resolve => setTimeout(resolve, PACE_MS)); await use(); }, { auto: true }],
});
