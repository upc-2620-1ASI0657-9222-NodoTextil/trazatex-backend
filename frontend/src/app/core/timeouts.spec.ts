import { DEFAULT_TIMEOUT_MS, TIMEOUT_RULES, normalizePath, timeoutFor } from './timeouts';
import { ClasePlazo, SERVICE_ROUTES } from './service-routes';

const REGLA_DE: Record<ClasePlazo, string | undefined> = {
  estandar: undefined,
  subida: 'subida multipart',
  'alta-empresa': 'alta de empresa',
  pdf: 'informe PDF',
  quality: 'comando de Quality',
};
const MS_DE: Record<ClasePlazo, number> = { estandar: 2500, subida: 30000, 'alta-empresa': 30000, pdf: 15000, quality: 8000 };
const concreta = (path: string) => path.replace(/\{[^}]*\}/g, 'abc-123');
const cuerpo = (route: { multipart?: true }) => (route.multipart ? new FormData() : undefined);

describe('Plazos por tipo de operación (ADR-010, enmienda 2026-11-04)', () => {
  it('los plazos acordados: 2,5 s / 30 s / 15 s / 8 s', () => {
    expect(DEFAULT_TIMEOUT_MS).toBe(2500);
    expect(Object.fromEntries(TIMEOUT_RULES.map(rule => [rule.name, rule.ms]))).toEqual({
      'subida multipart': 30000,
      'alta de empresa': 30000,
      'informe PDF': 15000,
      'comando de Quality': 8000,
    });
  });

  it('lecturas y escrituras JSON normales usan 2,5 s', () => {
    for (const [method, path] of [['GET', '/batches'], ['POST', '/batches'], ['POST', '/transfers/t1/accept'], ['PATCH', '/machines/m1/status'], ['GET', '/quality/controls'], ['GET', '/quality/batches']] as const)
      expect(timeoutFor(method, path)).toEqual({ ms: 2500 });
  });

  it('una subida multipart usa 30 s en cualquier ruta, también en Quality (gana a «comando de Quality»)', () => {
    expect(timeoutFor('POST', '/batches/b1/evidence', new FormData()).ms).toBe(30000);
    expect(timeoutFor('POST', '/quality/evidence', new FormData()).ms).toBe(30000);
    expect(timeoutFor('POST', '/quality/evidence', new FormData()).rule?.name).toBe('subida multipart');
    expect(timeoutFor('POST', '/quality/evidence', {}).ms).toBe(8000); // sin FormData ya no es una subida
  });

  it('el alta de empresa usa 30 s, pero no otras rutas de /companies', () => {
    expect(timeoutFor('POST', '/companies').ms).toBe(30000);
    expect(timeoutFor('GET', '/companies').ms).toBe(2500);
    expect(timeoutFor('PATCH', '/companies/c1/status').ms).toBe(2500);
    expect(timeoutFor('GET', '/companies/current/capacity').ms).toBe(2500);
  });

  it('el informe PDF usa 15 s solo con GET al informe de un control', () => {
    expect(timeoutFor('GET', '/quality/controls/c1/report').ms).toBe(15000);
    expect(timeoutFor('GET', '/quality/controls/c1').ms).toBe(2500);
    expect(timeoutFor('GET', '/quality/controls/c1/evidence').ms).toBe(2500);
  });

  it('los comandos de Quality (escrituras) usan 8 s; sus lecturas no', () => {
    for (const [method, path] of [['POST', '/quality/batches/b1/controls'], ['POST', '/quality/controls/c1/tests'], ['POST', '/quality/controls/c1/finish'],
      ['PUT', '/quality/failures/f1'], ['POST', '/quality/failures/f1/discard'], ['POST', '/quality/failures/f1/reevaluation'], ['DELETE', '/quality/controls/c1/evidence/e1']] as const)
      expect(timeoutFor(method, path).ms).toBe(8000);
    expect(timeoutFor('GET', '/quality/failures').ms).toBe(2500);
  });

  it('ignora la query, el prefijo /api y la barra final', () => {
    expect(normalizePath('/api/quality/controls/c1/report?x=1')).toBe('/quality/controls/c1/report');
    expect(normalizePath('/batches/')).toBe('/batches');
    expect(timeoutFor('GET', '/api/quality/controls/c1/report?download=1').ms).toBe(15000);
    expect(timeoutFor('post', '/quality/batches/b1/controls/').ms).toBe(8000);
  });

  it('una ruta no clasificada cae en 2,5 s, sin regla', () => {
    expect(timeoutFor('POST', '/ruta-nueva/que-nadie-clasifico')).toEqual({ ms: DEFAULT_TIMEOUT_MS });
    expect(timeoutFor('DELETE', '/otra/ruta').rule).toBeUndefined();
  });
});

describe('Catálogo de rutas de los servicios frente a la tabla de plazos', () => {
  it('no hay rutas repetidas en el catálogo', () => {
    const claves = SERVICE_ROUTES.map(route => route.method + ' ' + route.path);
    expect(claves.filter((clave, i) => claves.indexOf(clave) !== i)).toEqual([]);
  });

  it('cada ruta cae en la clase de plazo que declara el catálogo', () => {
    const desviadas = SERVICE_ROUTES
      .map(route => ({ route, actual: timeoutFor(route.method, concreta(route.path), cuerpo(route)) }))
      .filter(({ route, actual }) => actual.ms !== MS_DE[route.clase] || actual.rule?.name !== REGLA_DE[route.clase])
      .map(({ route, actual }) => `${route.method} ${route.path}: declara «${route.clase}» pero resulta ${actual.ms} ms (${actual.rule?.name ?? 'por defecto'})`);
    expect(desviadas).toEqual([]);
  });

  it('las rutas que caen en el plazo por defecto están declaradas «estandar» a propósito (lista para revisar)', () => {
    const porDefecto = SERVICE_ROUTES.filter(route => !timeoutFor(route.method, concreta(route.path), cuerpo(route)).rule);
    expect(porDefecto.every(route => route.clase === 'estandar')).toBe(true);
    // Toda escritura de Quality o subida declarada «estandar» sería un olvido: no debe haber ninguna.
    expect(porDefecto.filter(route => route.path.startsWith('/quality/') && route.method !== 'GET')).toEqual([]);
    expect(porDefecto.filter(route => route.multipart)).toEqual([]);
  });

  it('ninguna regla de la tabla queda sin usar por el catálogo', () => {
    const usadas = new Set(SERVICE_ROUTES.map(route => timeoutFor(route.method, concreta(route.path), cuerpo(route)).rule?.name));
    expect(TIMEOUT_RULES.filter(rule => !usadas.has(rule.name)).map(rule => rule.name)).toEqual([]);
  });

  it('el catálogo cubre las rutas que el front usa hoy', () => {
    const catalogo = new Set(SERVICE_ROUTES.map(route => route.method + ' ' + route.path.replace(/\{[^}]*\}/g, '{}')));
    for (const ruta of ['POST /auth/login', 'GET /batches/qr/{}', 'POST /quality/evidence', 'GET /quality/controls/{}/report', 'GET /notifications/unread-count', 'GET /analytics/summary'])
      expect(catalogo.has(ruta), ruta).toBe(true);
  });
});
