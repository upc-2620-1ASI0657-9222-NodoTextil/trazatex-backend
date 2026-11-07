import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from '../core/api.service';
import { AnaliticaPage, MAX_DAYS, daysBetween } from './analitica.page';

const SUMMARY = {
  from: '2026-10-01', to: '2026-10-31', timeZone: 'America/Lima', generatedAt: '2026-11-04T10:00:00Z',
  quality: [{ status: 'CONFORMING', count: 1, percentage: 100 }], failureCauses: [], durations: [{ operation: 'Transferencias', count: 1, averageHours: 3 }],
  materials: { shrinkageKg: 0, wasteKg: 0, completedTransformations: 0 },
};

class FakeApi {
  readonly calls: string[] = [];
  failure: Error | null = null;
  async request<T>(method: string, path: string): Promise<T> {
    this.calls.push(method + ' ' + path);
    if (this.failure) throw this.failure;
    return SUMMARY as T;
  }
  async all<T>(): Promise<T[]> { return []; }
}

describe('daysBetween', () => {
  it('cuenta los días con ambas fechas inclusivas', () => {
    expect(daysBetween('2026-10-05', '2026-10-05')).toBe(1);
    expect(daysBetween('2026-10-01', '2026-10-31')).toBe(31);
    expect(daysBetween('2025-01-01', '2026-01-01')).toBe(366);
    expect(daysBetween('2025-01-01', '2026-01-02')).toBe(367);
  });
  it('no se deja engañar por el cambio de hora ni por el año bisiesto', () => {
    expect(daysBetween('2024-03-09', '2024-03-11')).toBe(3);
    expect(daysBetween('2024-01-01', '2024-12-31')).toBe(366);
    expect(MAX_DAYS).toBe(366);
  });
});

describe('Analítica: contrato con el servicio (I6)', () => {
  let api: FakeApi;
  let fixture: ComponentFixture<AnaliticaPage>;
  const el = () => fixture.nativeElement as HTMLElement;
  async function render() { await fixture.whenStable(); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges(); }
  const requests = () => api.calls.filter(call => call.startsWith('GET /analytics'));

  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Gerencia', role: 'COMPANY_ADMIN', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }, { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: new Map() } } }] });
    fixture = TestBed.createComponent(AnaliticaPage);
    await render();
  });
  afterEach(() => { fixture.destroy(); sessionStorage.clear(); });

  it('consulta GET /analytics/summary con el periodo, no la ruta antigua', async () => {
    expect(requests()).toHaveLength(1);
    expect(requests()[0]).toMatch(/^GET \/analytics\/summary\?from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}$/);
    expect(api.calls.some(call => /^GET \/analytics\?/.test(call))).toBe(false);
  });

  it('un periodo de más de 366 días se avisa sin llamar al servicio', async () => {
    const page = fixture.componentInstance;
    page.from = '2025-01-01'; page.to = '2026-01-02';
    await page.load(); await render();
    expect(requests()).toHaveLength(1);                                           // solo la carga inicial
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('366');
    page.to = '2026-01-01';                                                       // 366 días: sí se pide
    await page.load(); await render();
    expect(requests()).toHaveLength(2);
    expect(requests()[1]).toBe('GET /analytics/summary?from=2025-01-01&to=2026-01-01');
  });

  it('un Hasta anterior a Desde tampoco llega al servicio', async () => {
    const page = fixture.componentInstance;
    page.from = '2026-10-31'; page.to = '2026-10-01';
    await page.load(); await render();
    expect(requests()).toHaveLength(1);
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Hasta debe ser igual o posterior a Desde');
  });

  it('si el servicio responde INVALID_PERIOD muestra su mensaje y permite reintentar', async () => {
    api.failure = Object.assign(new Error('El periodo no puede superar 366 días.'), { code: 'INVALID_PERIOD' });
    await fixture.componentInstance.load(); await render();
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('El periodo no puede superar 366 días.');
    expect(el().querySelector('[role="alert"] button')?.textContent).toContain('Reintentar');
  });

  it('el texto de Tiempos dice que solo cuentan las transferencias aceptadas que la empresa envió (I8)', async () => {
    (fixture.componentInstance as unknown as { tab: { set(v: string): void } }).tab.set('tiempos');
    await render();
    const text = el().textContent ?? '';
    expect(text).toContain('solo las aceptadas que tu empresa envió');
    expect(text).not.toContain('aceptadas y rechazadas');
  });
});
