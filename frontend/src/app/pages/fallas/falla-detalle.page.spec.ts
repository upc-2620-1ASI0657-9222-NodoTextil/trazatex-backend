import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { StoreService } from '../../core/store.service';
import { UiService } from '../../shared/ui';
import { fakeIndexedDB } from '../../testing/fake-indexeddb';
import { FallaDetallePage } from './falla-detalle.page';

class FakeApi {
  readonly calls: string[] = [];
  async request<T>(method: string, path: string): Promise<T> { this.calls.push(method + ' ' + path); throw new Error('sin respuesta de prueba: ' + path); }
  async all<T>(path: string): Promise<T[]> { this.calls.push('ALL ' + path); return []; }
}

describe('Enviar a reevaluación no exige datos (FE-M12, US26 esc. 2)', () => {
  async function preparar() {
    vi.stubGlobal('indexedDB', fakeIndexedDB());
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Admin', role: 'COMPANY_ADMIN', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    const api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    const store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    store.lotes.set([{ id: 'TT-1', empresaId: 'c1', nombre: 'Algodón', material: 'Algodón', cantidadKg: 100 } as never]);
    store.controles.set([{ id: 'ctrl-1', loteId: 'TT-1', empresaId: 'c1', tipo: 'Evaluación', status: 'FINISHED', fin: '2026-01-03T10:00:00Z', inicio: '2026-01-02T10:00:00Z', pruebas: [], evidencias: [] } as never]);
    store.fallas.set([{ id: 'f1', loteId: 'TT-1', controlId: 'ctrl-1', empresaId: 'c1', fecha: '2026-01-02T10:00:00Z', evidencias: [] } as never]);
    const ui = TestBed.inject(UiService);
    const confirm = vi.spyOn(ui, 'confirm').mockResolvedValue(true);
    vi.spyOn(ui, 'toast').mockImplementation(() => undefined);
    const reevaluar = vi.spyOn(store, 'enviarAReevaluacion').mockResolvedValue({ ok: true } as never);
    const actualizar = vi.spyOn(store, 'actualizarFalla').mockResolvedValue({ ok: true } as never);
    const fixture = TestBed.createComponent(FallaDetallePage);
    fixture.componentRef.setInput('id', 'f1');
    await fixture.whenStable();
    fixture.detectChanges();
    return { page: fixture.componentInstance, confirm, reevaluar, actualizar };
  }
  afterEach(() => { sessionStorage.clear(); vi.unstubAllGlobals(); });

  it('sin escribir nada reevalúa de inmediato, sin guardar ni exigir causa o cantidad', async () => {
    const { page, reevaluar, actualizar, confirm } = await preparar();
    await page.reevaluar();
    expect(reevaluar).toHaveBeenCalledWith('f1');
    expect(actualizar).not.toHaveBeenCalled();
    expect(confirm.mock.calls[0][0]).not.toContain('sin guardarlos');
  });

  it('con datos a medias (solo observaciones) reevalúa igual, avisando de que esos datos no se guardan', async () => {
    const { page, reevaluar, actualizar, confirm } = await preparar();
    page.observaciones = 'Revisar el telar 3';
    await page.reevaluar();
    expect(confirm.mock.calls[0][0]).toContain('sin guardarlos');
    expect(reevaluar).toHaveBeenCalledWith('f1');
    expect(actualizar).not.toHaveBeenCalled();
  });

  it('si la persona no acepta ese aviso, no se reevalúa', async () => {
    const { page, reevaluar, confirm } = await preparar();
    confirm.mockResolvedValue(false);
    page.causa = 'Tensión';
    await page.reevaluar();
    expect(reevaluar).not.toHaveBeenCalled();
  });

  it('con el registro completo lo guarda primero y luego reevalúa', async () => {
    const { page, reevaluar, actualizar } = await preparar();
    page.causa = 'Tensión excesiva';
    page.cantidad = 20;
    await page.reevaluar();
    expect(actualizar).toHaveBeenCalledTimes(1);
    expect(reevaluar).toHaveBeenCalledWith('f1');
  });

  it('una cantidad escrita pero imposible (mayor que el lote) sí detiene la reevaluación y lo dice', async () => {
    const { page, reevaluar } = await preparar();
    page.causa = 'Tensión';
    page.cantidad = 500;
    await page.reevaluar();
    expect(reevaluar).not.toHaveBeenCalled();
    expect(page.error()).toContain('entre 0 y 100 kg');
  });
});
