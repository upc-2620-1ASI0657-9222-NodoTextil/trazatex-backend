import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { StoreService } from '../../core/store.service';
import { LoteDetallePage } from './lote-detalle.page';

class FakeApi {
  readonly calls: string[] = [];
  async request<T>(method: string, path: string): Promise<T> { this.calls.push(method + ' ' + path); throw new Error('sin respuesta de prueba: ' + path); }
  async all<T>(path: string): Promise<T[]> { this.calls.push('ALL ' + path); return []; }
}

const lote = (id: string, empresaId: string) => ({
  id, qr: 'QR-' + id, origen: 'Ica', material: 'Algodón', composicion: [], cantidadKg: 100, caracteristicasRecepcion: '', empresaId,
  fase: 'Disponible', calidad: 'No revisado', fechaRegistro: '2026-01-02T10:00:00Z', productoFinal: false, evidencias: [],
});

describe('Lote compartido: aviso de pertenencia (FE-I8)', () => {
  async function render(rol: string, companyId: string, loteEmpresa: string, setup?: (store: StoreService) => void) {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId, email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Admin', role: rol, status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    const api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    const store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    store.empresas.set([{ id: 'c-otra', razonSocial: 'Hilados del Sur S.A.C.', estado: 'Activa' } as never]);
    store.lotes.set([lote('TT-1', loteEmpresa) as never]);
    setup?.(store);
    const fixture = TestBed.createComponent(LoteDetallePage);
    fixture.componentRef.setInput('id', 'TT-1');
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }
  afterEach(() => sessionStorage.clear());

  it('un lote de otra empresa dice a quién pertenece y que solo se ven los datos compartidos', async () => {
    const aviso = (await render('COMPANY_ADMIN', 'c1', 'c-otra')).querySelector('[data-testid="lote-ajeno"]');
    expect(aviso?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Pertenece a Hilados del Sur S.A.C.; solo ves los datos que comparte.');
  });

  it('en un lote propio no hay aviso', async () => {
    expect((await render('COMPANY_ADMIN', 'c1', 'c1')).querySelector('[data-testid="lote-ajeno"]')).toBeNull();
  });

  it('un padre que no está en el almacén (otra empresa) no rompe la genealogía: se muestra sin datos', async () => {
    const errores: unknown[] = [];
    const original = console.error; console.error = (...args: unknown[]) => { errores.push(args); };
    try {
      const html = await render('COMPANY_ADMIN', 'c1', 'c1', store => store.divisiones.set([{ id: 'D-1', loteOrigenId: 'TT-0', lotesHijosIds: ['TT-1'] } as never]));
      const cabeza = [...html.querySelectorAll('button.fold-head')].find(b => b.textContent?.includes('Genealogía')) as HTMLElement;
      if (cabeza.getAttribute('aria-expanded') !== 'true') cabeza.click();
      TestBed.tick();
      await Promise.resolve();
      const texto = html.textContent ?? '';
      expect(texto).toContain('TT-0');
      expect(texto).toContain('Lote de otra empresa');
      expect(errores.map(String).join(' ')).not.toMatch(/reading 'calidad'/);
    } finally { console.error = original; }
  });

  it('el detalle ofrece imprimir y descargar la etiqueta QR del lote (FE-M15)', async () => {
    const botones = [...(await render('OPERATOR', 'c1', 'c1')).querySelectorAll('.qr-actions button')].map(b => b.textContent?.trim());
    expect(botones).toEqual(['Imprimir etiqueta', 'Descargar QR (SVG)']);
  });
});
