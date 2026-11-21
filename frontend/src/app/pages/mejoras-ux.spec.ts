import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from '../core/api.service';
import { StoreService } from '../core/store.service';
import { fakeIndexedDB } from '../testing/fake-indexeddb';
import { DividirLoteDialog } from './lotes/lote-dialogs';
import { NotificacionesPage } from './notificaciones.page';

class FakeApi {
  readonly calls: string[] = [];
  async request<T>(method: string, path: string): Promise<T> {
    this.calls.push(method + ' ' + path);
    if (path === '/notifications/unread-count') return { count: 0 } as T;
    if (path.startsWith('/notifications?')) return { items: [], page: 0, size: 6, totalElements: 0, totalPages: 0 } as T;
    throw new Error('sin respuesta de prueba: ' + path);
  }
  async all<T>(path: string): Promise<T[]> { this.calls.push('ALL ' + path); return []; }
}

const lote = (id: string, empresaId: string, extra: Record<string, unknown> = {}) => ({
  id, qr: 'QR-' + id, origen: 'Ica', material: 'Hilo peinado', nombre: 'Hilo ' + id, composicion: [], cantidadKg: 30, caracteristicasRecepcion: '',
  empresaId, fase: 'Disponible', calidad: 'No revisado', fechaRegistro: '2026-01-02T10:00:00Z', productoFinal: false, evidencias: [], ...extra,
});

async function preparar(role = 'OPERATOR') {
  vi.stubGlobal('indexedDB', fakeIndexedDB());
  sessionStorage.clear();
  sessionStorage.setItem('trazatex.auth', JSON.stringify({
    token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
    user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Producción', role, status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
  }));
  const api = new FakeApi();
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
  const store = TestBed.inject(StoreService);
  TestBed.tick();
  await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
  await vi.waitFor(() => expect(store.loading()).toBe(false));
  return store;
}

describe('H9: avance de la división del lote padre', () => {
  afterEach(() => { sessionStorage.clear(); vi.unstubAllGlobals(); });

  it('muestra «X de Y kg» y se actualiza al cambiar cada cantidad', async () => {
    await preparar();
    const fixture = TestBed.createComponent(DividirLoteDialog);
    fixture.componentRef.setInput('lote', lote('TT-1', 'c1'));
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.paso.set(1);
    dialog.setQ(0, 18);
    dialog.setQ(1, 0);
    fixture.detectChanges();
    const avance = () => (fixture.nativeElement.querySelector('[data-testid="split-progress"]') as HTMLElement).textContent!.replace(/\s+/g, ' ');
    expect(avance()).toContain('Distribuido: 18 de 30 kg');
    expect(avance()).toContain('Faltan 12 kg');
    expect(dialog.cuadra()).toBe(false);
    dialog.setQ(1, 12);
    fixture.detectChanges();
    expect(avance()).toContain('30 de 30 kg');
    expect(avance()).toContain('Cantidad completa');
    expect(dialog.cuadra()).toBe(true);
    dialog.setQ(1, 15);
    fixture.detectChanges();
    expect(avance()).toContain('Excede en 3 kg');
    expect(dialog.cuadra()).toBe(false);
  });
});

describe('H8: motivo concreto del bloqueo', () => {
  afterEach(() => { sessionStorage.clear(); vi.unstubAllGlobals(); });

  it('nombra la transformación que usa el lote y la empresa destino de la transferencia', async () => {
    const store = await preparar();
    store.empresas.set([{ id: 'c2', razonSocial: 'Tejidos Andinos', ruc: '', actividades: [], estado: 'Activa', correoPrimerAdmin: '' }]);
    store.lotes.set([lote('TT-1', 'c1', { fase: 'En transformación' }) as never, lote('TT-2', 'c1', { fase: 'En transferencia' }) as never]);
    store.procesos.set([{ id: 'p1', operarioId: 'u1', empresaId: 'c1', entradas: [{ loteId: 'TT-1', cantidadKg: 30 }], tipo: 'Tejido', maquinariaId: 'M1', inicio: '2026-01-03T10:00:00Z', salidasIds: [], evidencias: [] }]);
    store.transferencias.set([{ id: 't1', origenId: 'c1', destinoId: 'c2', lotesIds: ['TT-2'], operarioId: 'u1', inicio: '2026-01-03T10:00:00Z', estado: 'Pendiente' }]);
    expect(store.disponibilidad(store.lote('TT-1')!).motivo).toBe('Es entrada de la transformación «Tejido de hilo peinado», que aún no se finaliza.');
    expect(store.disponibilidad(store.lote('TT-2')!).motivo).toBe('Transferencia a Tejidos Andinos pendiente de aceptación.');
  });
});

describe('H1: notificaciones con quién, qué y sobre qué', () => {
  afterEach(() => { sessionStorage.clear(); vi.unstubAllGlobals(); });

  it('una transferencia entrante nombra a la empresa de origen y sus lotes, sin datos del operario', async () => {
    const store = await preparar('COMPANY_ADMIN');
    store.empresas.set([{ id: 'c2', razonSocial: 'Hilandería Norte', ruc: '', actividades: [], estado: 'Activa', correoPrimerAdmin: '' }]);
    const page = TestBed.createComponent(NotificacionesPage).componentInstance;
    const r = page.resumen({
      id: 'n1', tipo: 'Transferencia entrante', titulo: 'Transferencia pendiente de aceptación', descripcion: '', fecha: new Date().toISOString(),
      leida: false, para: 'Administrador de Empresa', datos: { transferId: 't9', fromCompanyId: 'c2', toCompanyId: 'c1', batchIds: ['TT-7', 'TT-8', 'TT-9'] },
      entidad: { tipo: 'transferencia', id: 't9' },
    });
    expect(r.actor).toBe('Hilandería Norte');
    expect(r.accion).toBe('envió a tu empresa una transferencia con');
    expect(r.objeto).toBe('3 lotes: TT-7, TT-8 y 1 más');
    expect(page.iniciales(r.actor)).toBe('HN');
  });

  it('una posible falla derivada la atribuye a Trazabilidad y nombra el lote propio', async () => {
    const store = await preparar('COMPANY_ADMIN');
    store.lotes.set([lote('TT-1', 'c1') as never]);
    const page = TestBed.createComponent(NotificacionesPage).componentInstance;
    const r = page.resumen({
      id: 'n2', tipo: 'Posible falla derivada', titulo: '', descripcion: '', fecha: new Date().toISOString(), leida: false,
      para: 'Administrador de Empresa', datos: { batchIds: ['b1', 'b2'] }, entidad: { tipo: 'lote', id: 'TT-1' },
    });
    expect(r.actor).toBe('Trazabilidad');
    expect(r.objeto).toBe('Hilo TT-1 y 1 más');
  });

  it('el tiempo es relativo', async () => {
    await preparar();
    const page = TestBed.createComponent(NotificacionesPage).componentInstance;
    expect(page.hace(new Date(Date.now() - 5 * 60000).toISOString())).toBe('hace 5 min');
    expect(page.hace(new Date(Date.now() - 26 * 3600000).toISOString())).toBe('ayer');
  });
});
