import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from './api.service';
import { SessionService } from './session.service';
import { NOMBRE_OCULTO, StoreService } from './store.service';
import { UiService } from '../shared/ui';

/** ApiService de mentira: responde por «MÉTODO ruta» y anota las llamadas. */
class FakeApi {
  readonly calls: string[] = [];
  readonly routes = new Map<string, unknown>();
  async request<T>(method: string, path: string): Promise<T> {
    this.calls.push(method + ' ' + path);
    if (!this.routes.has(method + ' ' + path)) throw new Error('Ruta sin respuesta de prueba: ' + method + ' ' + path);
    const response = this.routes.get(method + ' ' + path);
    if (response instanceof Error || response instanceof HttpErrorResponse) throw response;   // una ruta con un error simula un fallo del servidor
    return response as T;
  }
  async all<T>(path: string): Promise<T[]> {
    this.calls.push('ALL ' + path);
    return (this.routes.get('ALL ' + path) as T[] | undefined) ?? [];
  }
}

const OWN_COMPANY = 'company-own';
const FOREIGN_COMPANY = 'company-foreign';

function sessionFor(role = 'OPERATOR') {
  sessionStorage.setItem('trazatex.auth', JSON.stringify({
    token: 'unit-test-token',
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
    user: {
      id: 'user-own', companyId: OWN_COMPANY, email: 'operator@example.test', firstName: 'Ana', lastName: 'Pérez',
      position: 'Producción', role, status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
    },
  }));
}

function batch(id: string, traceabilityId: string, companyId: string, access: 'OWN' | 'SHARED', name?: string) {
  return {
    kind: 'BATCH', access, id, traceabilityId, qrCode: 'QR-' + traceabilityId, companyId, businessName: 'Empresa ' + companyId,
    name, materialType: 'Algodón', phase: 'AVAILABLE', qualityStatus: 'NOT_REVIEWED', finalProduct: false,
    createdAt: '2026-01-02T10:00:00Z',
  };
}

describe('Privacidad del nombre del lote (OWN)', () => {
  let api: FakeApi;
  let store: StoreService;
  beforeEach(async () => {
    sessionStorage.clear();
    sessionFor();
    api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    store = TestBed.inject(StoreService);
    // El efecto inicial del store limpia el estado y lanza un refresh(): se deja correr antes de cada prueba.
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    api.calls.length = 0;
  });
  afterEach(() => sessionStorage.clear());

  /** Genealogía con un lote propio y uno ajeno cuyo `name` llega aunque no debería (servidor defectuoso). */
  function genealogia() {
    api.routes.set('GET /traceability/batches/own-id/lineage', {
      complete: true, truncated: false,
      nodes: [
        batch('own-id', 'TT-OWN-0001', OWN_COMPANY, 'OWN', 'Algodón de Ica'),
        batch('foreign-id', 'TT-FOR-0002', FOREIGN_COMPANY, 'SHARED', 'Secreto del proveedor'),
      ],
      edges: [],
    });
  }

  it('muestra el nombre solo en lotes propios y «—» en los ajenos', async () => {
    genealogia();
    await store.loadLineage('own-id');
    expect(store.nombreLote('TT-OWN-0001')).toBe('Algodón de Ica');
    expect(store.nombreLote('TT-FOR-0002')).toBe(NOMBRE_OCULTO);
    expect(NOMBRE_OCULTO).toBe('—');
  });

  it('no guarda en el estado el nombre de un lote ajeno, aunque el servidor lo envíe', async () => {
    genealogia();
    await store.loadLineage('own-id');
    expect(store.lote('TT-FOR-0002')?.nombre).toBeUndefined();
    expect(store.lote('TT-OWN-0001')?.nombre).toBe('Algodón de Ica');
    expect(JSON.stringify(store.lotes())).not.toContain('Secreto del proveedor');
  });

  it('no guarda el nombre de un nodo SHARED aunque su companyId coincida con el de la sesión', async () => {
    api.routes.set('GET /traceability/batches/own-id/lineage', {
      complete: true, truncated: false,
      nodes: [batch('own-id', 'TT-OWN-0001', OWN_COMPANY, 'SHARED', 'No debería verse')],
      edges: [],
    });
    await store.loadLineage('own-id');
    expect(store.lote('TT-OWN-0001')?.nombre).toBeUndefined();
    expect(store.nombreLote('TT-OWN-0001')).not.toContain('No debería verse');
  });

  it('un lote desconocido muestra «—», no su identificador interno', () => {
    expect(store.nombreLote('00000000-0000-0000-0000-000000000000')).toBe(NOMBRE_OCULTO);
  });

  it('la búsqueda por nombre solo opera sobre lotes propios', async () => {
    genealogia();
    await store.loadLineage('own-id');
    api.routes.set('GET /traceability/batches/own-id/lineage', {
      complete: true, truncated: false,
      nodes: [batch('own-id', 'TT-OWN-0001', OWN_COMPANY, 'OWN', 'Algodón de Ica')], edges: [],
    });
    expect(await store.searchLineages('algodón de')).toEqual(['TT-OWN-0001']);
    api.calls.length = 0;
    // «secreto» solo existe como nombre ajeno: no hay coincidencia local y se consulta por ID/QR exactos.
    expect(await store.searchLineages('secreto del proveedor')).toEqual([]);
    expect(api.calls).toEqual([
      'ALL /traceability/lineages?traceabilityId=secreto%20del%20proveedor',
      'ALL /traceability/lineages?qrCode=secreto%20del%20proveedor',
    ]);
  });

  it('el detalle de una transferencia (excepción decidida) muestra el nombre sin exponerlo en el resto de la app', async () => {
    genealogia();
    await store.loadLineage('own-id');
    api.routes.set('GET /transfers/tr-1', {
      batches: [{ batchId: 'foreign-id', traceabilityId: 'TT-FOR-0002', name: 'Lote en tránsito', quantityKg: 10, materialType: 'Algodón' }],
    });
    store.requestTransferDetail('tr-1');
    await vi.waitFor(() => expect(store.loadedTransferDetails()).toContain('tr-1'));
    const lotesAntes = store.lotes();
    expect(store.nombreEnTransferencia('TT-FOR-0002')).toBe('Lote en tránsito');
    expect(store.nombreLote('TT-FOR-0002')).toBe(NOMBRE_OCULTO);
    // Leer el nombre no escribe nada, y el detalle de la transferencia nunca lo copia a `lotes`.
    expect(store.lotes()).toBe(lotesAntes);
    expect(store.lote('TT-FOR-0002')?.nombre).toBeUndefined();
    expect(JSON.stringify(store.lotes())).not.toContain('Lote en tránsito');
  });

  it('sin detalle de transferencia cargado, un lote ajeno se identifica por su Traceability ID', async () => {
    genealogia();
    await store.loadLineage('own-id');
    expect(store.nombreEnTransferencia('TT-FOR-0002')).toBe('TT-FOR-0002');
  });
});

/** Respuesta de `GET /notifications` (página del servidor). */
function pagina(items: unknown[], extra: Record<string, unknown> = {}) {
  return { items, page: 0, size: 6, totalElements: items.length, totalPages: items.length ? 1 : 0, ...extra };
}
function aviso(id: string, type: string, over: Record<string, unknown> = {}) {
  return { id, type, title: 'Título ' + id, message: 'Mensaje ' + id, resourceType: null, resourceId: null, data: {},
    createdAt: '2026-11-04T10:00:00.000Z', read: false, companyId: OWN_COMPANY, role: 'OPERATOR', ...over };
}

describe('Contador de notificaciones no leídas (FE-I9)', () => {
  let api: FakeApi;
  let store: StoreService;
  const unreadCalls = () => api.calls.filter(call => call === 'GET /notifications/unread-count');
  const setCount = (count: number) => api.routes.set('GET /notifications/unread-count', { count });

  /** Espera con setTimeout real: `vi.waitFor` adelantaría los intervalos simulados y falsearía los 30 s. */
  async function hasta(condition: () => boolean) {
    for (let i = 0; i < 400 && !condition(); i++) await new Promise(resolve => setTimeout(resolve, 5));
    expect(condition()).toBe(true);
  }

  /** Arranca el store (con o sin sesión) y deja que termine el refresh() inicial. Solo se simulan los intervalos. */
  async function arrancar(conSesion: boolean, count = 3) {
    sessionStorage.clear();
    if (conSesion) sessionFor();
    api = new FakeApi();
    api.routes.set('GET /notifications?page=0&size=6', pagina([]));
    setCount(count);
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    store = TestBed.inject(StoreService);
    TestBed.tick();
    if (conSesion) {
      await hasta(() => api.calls.includes('ALL /transfers/recipients'));
      await hasta(() => !store.loading());
    }
  }
  afterEach(() => { vi.useRealTimers(); sessionStorage.clear(); });

  it('al iniciar sesión consulta GET /notifications/unread-count y expone el valor', async () => {
    await arrancar(true, 3);
    expect(unreadCalls().length).toBeGreaterThanOrEqual(1);
    expect(store.unreadCount()).toBe(3);
    expect(store.unreadBadge()).toBe('3');
  });

  it('muestra 99+ a partir de 100 y el número exacto hasta 99', async () => {
    await arrancar(true, 99);
    expect(store.unreadBadge()).toBe('99');
    setCount(100);
    await store.refreshUnreadCount();
    expect(store.unreadBadge()).toBe('99+');
    setCount(2500);
    await store.refreshUnreadCount();
    expect(store.unreadBadge()).toBe('99+');
  });

  it('con 0 no hay contador que mostrar', async () => {
    await arrancar(true, 0);
    expect(store.unreadCount()).toBe(0);
  });

  it('se actualiza con el sondeo de 30 s ya existente, sin crear otro temporizador', async () => {
    await arrancar(true, 1);
    api.calls.length = 0;
    setCount(4);
    vi.advanceTimersByTime(29999);
    expect(unreadCalls()).toHaveLength(0);
    vi.advanceTimersByTime(1);
    await hasta(() => store.unreadCount() === 4);
    expect(unreadCalls()).toHaveLength(1);
    vi.advanceTimersByTime(30000);
    await hasta(() => unreadCalls().length === 2);
  });

  it('se actualiza al marcar una notificación como leída y al marcar todas', async () => {
    await arrancar(true, 3);
    api.routes.set('PUT /notifications/n1/read?value=true', undefined);
    setCount(2);
    expect(await store.marcarLeida('n1')).toBe(true);
    expect(store.unreadCount()).toBe(2);
    api.routes.set('PUT /notifications/n1/read?value=false', undefined);
    setCount(3);
    await store.marcarLeida('n1', false);
    expect(store.unreadCount()).toBe(3);
    api.routes.set('PUT /notifications/read-all', undefined);
    setCount(0);
    await store.marcarTodasLeidas();
    expect(store.unreadCount()).toBe(0);
    expect(api.calls).toContain('PUT /notifications/read-all');
  });

  it('una notificación que ya no existe (404 NOTIFICATION_NOT_FOUND) actualiza la bandeja y avisa sin alarma', async () => {
    await arrancar(true, 3);
    api.routes.set('PUT /notifications/n9/read?value=true', new HttpErrorResponse({ status: 404, error: { code: 'NOTIFICATION_NOT_FOUND', message: 'La notificación no existe.' } }));
    setCount(2);
    const toasts: Array<[string, boolean]> = [];
    const ui = TestBed.inject(UiService);
    vi.spyOn(ui, 'toast').mockImplementation((text: string, error = false) => { toasts.push([text, error]); });
    api.calls.length = 0;

    expect(await store.marcarLeida('n9')).toBe(false);

    expect(api.calls.filter(call => call.startsWith('GET /notifications'))).toContain('GET /notifications/unread-count');
    expect(store.unreadCount()).toBe(2);
    expect(toasts).toEqual([['Esa notificación ya no está disponible. Se actualizó la bandeja.', false]]);
  });

  it('cualquier otro error al marcar como leída sí se muestra como error', async () => {
    await arrancar(true, 3);
    api.routes.set('PUT /notifications/n9/read?value=true', new HttpErrorResponse({ status: 500, error: { code: 'INTERNAL_ERROR', message: 'Falló.' } }));
    const toasts: Array<[string, boolean]> = [];
    vi.spyOn(TestBed.inject(UiService), 'toast').mockImplementation((text: string, error = false) => { toasts.push([text, error]); });

    expect(await store.marcarLeida('n9')).toBe(false);

    expect(toasts).toEqual([['Falló.', true]]);
  });

  it('si la consulta falla, conserva el último valor y no lanza', async () => {
    await arrancar(true, 5);
    api.routes.delete('GET /notifications/unread-count');
    await expect(store.refreshUnreadCount()).resolves.toBeUndefined();
    expect(store.unreadCount()).toBe(5);
  });

  it('sin sesión no consulta ni sondea', async () => {
    await arrancar(false);
    await store.refreshUnreadCount();
    vi.advanceTimersByTime(5 * 60000);
    expect(api.calls).toEqual([]);
    expect(store.unreadCount()).toBe(0);
  });

  it('tras cerrar sesión se detiene el sondeo y el contador vuelve a 0', async () => {
    await arrancar(true, 7);
    expect(store.unreadCount()).toBe(7);
    TestBed.inject(SessionService).logout();
    TestBed.tick();
    expect(store.unreadCount()).toBe(0);
    api.calls.length = 0;
    vi.advanceTimersByTime(5 * 60000);
    await store.refreshUnreadCount();
    expect(api.calls).toEqual([]);
  });
});

describe('Lectura de QR: lote propio, lote de otra empresa y enlaces (FE-I1)', () => {
  let api: FakeApi;
  let store: StoreService;
  const link = (id: string) => window.location.origin + '/lotes/' + id;
  const hit = (over: Record<string, unknown> = {}) => ({
    batchId: 'foreign-id', traceabilityId: 'TT-FOR-0002', qrCode: 'QR-TT-FOR-0002', companyId: FOREIGN_COMPANY, businessName: 'Hilandería Sur',
    materialType: 'Algodón', phase: 'AVAILABLE', qualityStatus: 'NOT_REVIEWED', createdAt: '2026-01-02T10:00:00Z', finalProduct: false, access: 'SHARED', ...over,
  });
  const sharedBatch = () => ({
    id: 'foreign-id', traceabilityId: 'TT-FOR-0002', qrCode: 'QR-TT-FOR-0002', companyId: FOREIGN_COMPANY, materialType: 'Algodón',
    phase: 'AVAILABLE', qualityStatus: 'NOT_REVIEWED', finalProduct: false, createdAt: '2026-01-02T10:00:00Z',
  });
  const ownBatch = () => ({ ...sharedBatch(), id: 'own-id', traceabilityId: 'TT-OWN-0001', qrCode: 'QR-TT-OWN-0001', companyId: OWN_COMPANY, name: 'Algodón de Ica', quantityKg: 40, origin: 'Ica', supplierName: 'Proveedor' });
  const detailCalls = () => api.calls.filter(call => call.startsWith('GET /batches/') && !call.startsWith('GET /batches/qr/'));

  beforeEach(async () => {
    sessionStorage.clear();
    sessionFor();
    api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    api.calls.length = 0;
  });
  afterEach(() => sessionStorage.clear());

  describe('código del sistema: GET /batches/qr/{qr} → {access, batch}', () => {
    it('un QR de otra empresa muestra la vista compartida, no un error, y sin nombre', async () => {
      api.routes.set('GET /batches/qr/QR-TT-FOR-0002', { access: 'SHARED', batch: { ...sharedBatch(), name: 'Secreto del proveedor' } });
      const code = await store.resolveLotQr('QR-TT-FOR-0002');
      expect(code).toBe('TT-FOR-0002');
      expect(store.lote(code)?.empresaId).toBe(FOREIGN_COMPANY);
      expect(store.lote(code)?.nombre).toBeUndefined();
      expect(store.nombreLote(code)).toBe(NOMBRE_OCULTO);
      expect(JSON.stringify(store.lotes())).not.toContain('Secreto del proveedor');
      expect(api.calls).toEqual(['GET /batches/qr/QR-TT-FOR-0002']);
    });

    it('en la vista compartida no se ven los campos privados (solo lo que llega)', async () => {
      api.routes.set('GET /batches/qr/QR-TT-FOR-0002', { access: 'SHARED', batch: sharedBatch() });
      await store.resolveLotQr('QR-TT-FOR-0002');
      for (const campo of ['proveedor', 'origen', 'cantidad', 'composicion'] as const) expect(store.puedeVer(campo, FOREIGN_COMPANY)).toBe(false);
    });

    it('un QR propio entra completo, con su nombre', async () => {
      api.routes.set('GET /batches/qr/QR-TT-OWN-0001', { access: 'OWN', batch: ownBatch() });
      const code = await store.resolveLotQr('QR-TT-OWN-0001');
      expect(code).toBe('TT-OWN-0001');
      expect(store.nombreLote(code)).toBe('Algodón de Ica');
      expect(store.lote(code)?.cantidadKg).toBe(40);
    });

    it('un QR desconocido propaga el error del servidor (404 QR_NOT_FOUND)', async () => {
      await expect(store.resolveLotQr('QR-NO-EXISTE-1')).rejects.toThrow();
    });

    it('rechaza sin consultar al servidor lo que no tiene forma de QR de TrazaTex', async () => {
      for (const malo of ['', '   ', 'corto', 'con espacios y símbolos !!', 'x'.repeat(2049)])
        await expect(store.resolveLotQr(malo)).rejects.toThrow('no corresponde');
      expect(api.calls).toEqual([]);
    });
  });

  describe('enlace /lotes/<Traceability ID>: búsqueda exacta, también de otra empresa', () => {
    const search = '/traceability/lineages?traceabilityId=TT-FOR-0002&size=100';

    it('resuelve un lote de otra empresa por su Traceability ID y entra como vista compartida', async () => {
      api.routes.set('GET ' + search, { items: [hit()] });
      const code = await store.resolveLotQr(link('TT-FOR-0002'));
      expect(code).toBe('TT-FOR-0002');
      expect(store.lote(code)?.nombre).toBeUndefined();
      expect(store.nombreLote(code)).toBe(NOMBRE_OCULTO);
      expect(store.nombreEmpresa(FOREIGN_COMPANY)).toBe('Hilandería Sur');
      expect(detailCalls()).toEqual([]); // GET /batches/{id} de un lote ajeno sería un 404
    });

    it('no pasa por la búsqueda por nombre ni por QR: una sola consulta exacta por Traceability ID', async () => {
      api.routes.set('GET ' + search, { items: [hit()] });
      await store.resolveLotQr(link('TT-FOR-0002'));
      expect(api.calls).toEqual(['GET ' + search]);
    });

    it('deja registrado el identificador interno para abrir luego la genealogía del lote ajeno', async () => {
      api.routes.set('GET ' + search, { items: [hit()] });
      api.routes.set('GET /traceability/batches/foreign-id/lineage', {
        complete: true, truncated: false, nodes: [batch('foreign-id', 'TT-FOR-0002', FOREIGN_COMPANY, 'SHARED', 'Secreto')], edges: [],
      });
      await store.resolveLotQr(link('TT-FOR-0002'));
      await store.loadLineage('TT-FOR-0002');
      expect(api.calls).toContain('GET /traceability/batches/foreign-id/lineage');
    });

    it('elige la coincidencia exacta aunque haya otras con el mismo prefijo', async () => {
      api.routes.set('GET ' + search, { items: [hit({ batchId: 'otro', traceabilityId: 'TT-FOR-00029' }), hit()] });
      expect(await store.resolveLotQr(link('TT-FOR-0002'))).toBe('TT-FOR-0002');
    });

    it('un enlace de un lote propio se abre con su detalle completo', async () => {
      api.routes.set('GET /traceability/lineages?traceabilityId=TT-OWN-0001&size=100', { items: [hit({ batchId: 'own-id', traceabilityId: 'TT-OWN-0001', companyId: OWN_COMPANY, access: 'OWN' })] });
      api.routes.set('GET /batches/own-id', ownBatch());
      const code = await store.resolveLotQr(link('TT-OWN-0001'));
      expect(store.nombreLote(code)).toBe('Algodón de Ica');
    });

    it('un lote ajeno ya conocido no se degrada a la vista mínima', async () => {
      api.routes.set('GET /traceability/batches/own-id/lineage', {
        complete: true, truncated: false, edges: [],
        nodes: [batch('own-id', 'TT-OWN-0001', OWN_COMPANY, 'OWN', 'Algodón de Ica'), { ...batch('foreign-id', 'TT-FOR-0002', FOREIGN_COMPANY, 'SHARED'), quantityKg: 12, origin: 'Arequipa' }],
      });
      await store.loadLineage('own-id');
      api.routes.set('GET ' + search, { items: [hit()] });
      await store.resolveLotQr(link('TT-FOR-0002'));
      expect(store.lote('TT-FOR-0002')?.cantidadKg).toBe(12);
      expect(store.puedeVer('origen', FOREIGN_COMPANY)).toBe(true);
    });

    it('si el lote no existe o no hay acceso, lo dice (sin inventar una vista)', async () => {
      api.routes.set('GET ' + search, { items: [] });
      await expect(store.resolveLotQr(link('TT-FOR-0002'))).rejects.toThrow('no existe o no tienes acceso');
      expect(store.lote('TT-FOR-0002')).toBeUndefined();
    });

    it('rechaza otros orígenes y rutas que no son de un lote', async () => {
      await expect(store.resolveLotQr('https://invalid.test/lotes/TT-FOR-0002')).rejects.toThrow('esta plataforma');
      await expect(store.resolveLotQr(window.location.origin + '/otra/ruta')).rejects.toThrow('esta plataforma');
      await expect(store.resolveLotQr('https://')).rejects.toThrow('no corresponde');
      expect(api.calls).toEqual([]);
    });
  });
});

describe('Enlace /lotes/<Traceability ID> abierto directamente (FE-I1)', () => {
  let api: FakeApi;
  let store: StoreService;
  beforeEach(async () => {
    sessionStorage.clear();
    sessionFor();
    api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    api.calls.length = 0;
  });
  afterEach(() => sessionStorage.clear());

  it('un lote ajeno sin identificador interno conocido se resuelve por su Traceability ID antes de pedir la genealogía', async () => {
    api.routes.set('GET /traceability/lineages?traceabilityId=TT-FOR-0002&size=100', { items: [{
      batchId: 'foreign-id', traceabilityId: 'TT-FOR-0002', qrCode: 'QR-X', companyId: FOREIGN_COMPANY, businessName: 'Hilandería Sur', materialType: 'Algodón',
      phase: 'AVAILABLE', qualityStatus: 'NOT_REVIEWED', createdAt: '2026-01-02T10:00:00Z', finalProduct: false, access: 'SHARED' }] });
    api.routes.set('GET /traceability/batches/foreign-id/lineage', {
      complete: true, truncated: false, nodes: [batch('foreign-id', 'TT-FOR-0002', FOREIGN_COMPANY, 'SHARED', 'Secreto del proveedor')], edges: [] });
    store.requestLineage('TT-FOR-0002');
    await vi.waitFor(() => expect(api.calls).toContain('GET /traceability/batches/foreign-id/lineage'));
    expect(api.calls).toEqual([
      'GET /traceability/lineages?traceabilityId=TT-FOR-0002&size=100',
      'GET /traceability/batches/foreign-id/lineage',
    ]);
    expect(store.nombreLote('TT-FOR-0002')).toBe(NOMBRE_OCULTO);
    expect(JSON.stringify(store.lotes())).not.toContain('Secreto del proveedor');
  });

  it('un lote cuyo identificador ya se conoce no repite la búsqueda', async () => {
    api.routes.set('GET /traceability/batches/own-id/lineage', {
      complete: true, truncated: false, nodes: [batch('own-id', 'TT-OWN-0001', OWN_COMPANY, 'OWN', 'Algodón de Ica')], edges: [] });
    await store.loadLineage('own-id');
    api.calls.length = 0;
    store.requestLineage('TT-OWN-0001');
    await vi.waitFor(() => expect(api.calls).toContain('GET /traceability/batches/own-id/lineage'));
    expect(api.calls.filter(call => call.includes('/lineages?'))).toEqual([]);
  });

  it('si el lote no existe o no hay acceso, informa el error y permite reintentar', async () => {
    api.routes.set('GET /traceability/lineages?traceabilityId=TT-NADA-0000&size=100', { items: [] });
    store.requestLineage('TT-NADA-0000');
    await vi.waitFor(() => expect(store.error()).toContain('no existe o no tienes acceso'));
    api.calls.length = 0;
    store.requestLineage('TT-NADA-0000');
    await vi.waitFor(() => expect(api.calls).toContain('GET /traceability/lineages?traceabilityId=TT-NADA-0000&size=100'));
  });
});

describe('Bandeja de notificaciones paginada por el servidor (Notification I5)', () => {
  let api: FakeApi;
  let store: StoreService;
  const inicial = 'GET /notifications?page=0&size=6';

  beforeEach(async () => {
    sessionStorage.clear();
    sessionFor();
    api = new FakeApi();
    api.routes.set(inicial, pagina([]));
    api.routes.set('GET /notifications/unread-count', { count: 0 });
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    api.calls.length = 0;
  });
  afterEach(() => sessionStorage.clear());
  const pedidas = () => api.calls.filter(call => call.startsWith('GET /notifications?'));

  it('traduce el código estable del servicio al texto que se muestra y conserva `data`', async () => {
    api.routes.set(inicial, pagina([
      aviso('a', 'TRANSFER_RECEIVED', { resourceType: 'transferencia', resourceId: 't1', data: { transferId: 't1' } }),
      aviso('b', 'ADMIN_REQUEST_SUBMITTED'), aviso('c', 'ADMIN_REQUEST_RESOLVED'), aviso('d', 'TRANSFER_ACCEPTED'),
      aviso('e', 'TRANSFER_REJECTED'), aviso('f', 'POTENTIAL_DERIVED_FAILURE', { data: { batchIds: ['x', 'y'] } }),
    ]));
    await store.refreshNotifications();
    expect(store.notificaciones().map(n => n.tipo)).toEqual([
      'Transferencia entrante', 'Solicitud de administrador', 'Solicitud revisada', 'Transferencia aceptada',
      'Transferencia rechazada', 'Posible falla derivada']);
    expect(store.notificaciones()[0].entidad).toEqual({ tipo: 'transferencia', id: 't1' });
    expect(store.notificaciones()[0].datos).toEqual({ transferId: 't1' });
    expect(store.notificaciones()[5].datos).toEqual({ batchIds: ['x', 'y'] });
  });

  it('un código que el front no conoce se muestra como «Notificación», no en blanco', async () => {
    api.routes.set(inicial, pagina([aviso('a', 'TIPO_FUTURO')]));
    await store.refreshNotifications();
    expect(store.notificaciones()[0].tipo).toBe('Notificación');
  });

  it('pide al servidor el filtro y la página, y los repite al refrescar (sondeo o marcar leída)', async () => {
    api.routes.set('GET /notifications?page=2&size=10&unread=true&type=TRANSFER_RECEIVED', pagina([aviso('a', 'TRANSFER_RECEIVED')], { page: 2, size: 10, totalElements: 21, totalPages: 3 }));
    await store.consultarNotificaciones({ unread: true, type: 'TRANSFER_RECEIVED', page: 2, size: 10 });
    expect(pedidas()).toEqual(['GET /notifications?page=2&size=10&unread=true&type=TRANSFER_RECEIVED']);
    expect(store.notificacionesTotal()).toBe(21);
    api.routes.set('PUT /notifications/a/read?value=true', undefined);
    api.routes.set('GET /notifications/unread-count', { count: 20 });
    await store.marcarLeida('a');
    expect(pedidas()).toEqual(Array(2).fill('GET /notifications?page=2&size=10&unread=true&type=TRANSFER_RECEIVED'));
  });

  it('«Todas» solo se actualiza con consultas sin filtro, y «No leídas» sale del contador del servidor', async () => {
    api.routes.set(inicial, pagina([aviso('a', 'TRANSFER_RECEIVED')], { totalElements: 40, totalPages: 7 }));
    await store.refreshNotifications();
    expect(store.notificacionesTodas()).toBe(40);
    api.routes.set('GET /notifications?page=0&size=6&unread=true', pagina([], { totalElements: 3, totalPages: 1 }));
    await store.consultarNotificaciones({ unread: true });
    expect(store.notificacionesTodas()).toBe(40);
    expect(store.notificacionesTotal()).toBe(3);
    api.routes.set('GET /notifications/unread-count', { count: 17 });
    await store.refreshUnreadCount();
    expect(store.noLeidas()).toBe(17);
  });

  it('descarta la respuesta de una consulta que ya fue reemplazada por otra', async () => {
    let soltar: (value: unknown) => void = () => {};
    const lenta = new Promise(resolve => { soltar = resolve; });
    const request = api.request.bind(api);
    api.request = async <T>(method: string, path: string) => {
      if (path === inicial.slice(4)) { api.calls.push(method + ' ' + path); return await lenta as T; }
      return request<T>(method, path);
    };
    const primera = store.refreshNotifications();
    api.routes.set('GET /notifications?page=0&size=6&unread=true', pagina([aviso('nueva', 'TRANSFER_RECEIVED')]));
    await store.consultarNotificaciones({ unread: true });
    soltar(pagina([aviso('vieja', 'TRANSFER_RECEIVED')]));
    await primera;
    expect(store.notificaciones().map(n => n.id)).toEqual(['nueva']);
  });

  it('si el servidor falla muestra el error, conserva lo cargado y consultarNotificaciones no lanza', async () => {
    api.routes.set(inicial, pagina([aviso('a', 'TRANSFER_RECEIVED')]));
    await store.refreshNotifications();
    api.routes.delete('GET /notifications?page=0&size=6&unread=true');
    await expect(store.consultarNotificaciones({ unread: true })).resolves.toBeUndefined();
    expect(store.notificationError()).not.toBe('');
    expect(store.notificaciones().map(n => n.id)).toEqual(['a']);
  });
});

describe('Listados paginados (I12)', () => {
  let api: FakeApi;

  beforeEach(() => {
    sessionStorage.clear();
    sessionFor('COMPANY_ADMIN');
    api = new FakeApi();
    api.routes.set('GET /notifications?page=0&size=6', { items: [], page: 0, size: 6, totalElements: 0, totalPages: 0 });
    api.routes.set('GET /notifications/unread-count', { count: 0 });
    api.routes.set('GET /machines', []);
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    TestBed.inject(StoreService);
    TestBed.tick();
  });
  afterEach(() => sessionStorage.clear());

  it('lee los destinatarios y los listados de calidad con api.all (todas las páginas), nunca como un arreglo único', async () => {
    await vi.waitFor(() => {
      expect(api.calls).toContain('ALL /transfers/recipients');
      expect(api.calls).toContain('ALL /quality/controls');
      expect(api.calls).toContain('ALL /quality/failures');
      expect(api.calls).toContain('ALL /quality/batches');
    });
    for (const path of ['/transfers/recipients', '/quality/controls', '/quality/failures', '/quality/batches'])
      expect(api.calls, 'GET ' + path + ' devuelve una página, no un arreglo').not.toContain('GET ' + path);
  });
});

describe('Títulos legibles de controles y transferencias (FE-I8)', () => {
  const UUID = '9f8e7d6c-1a2b-4c3d-8e9f-0a1b2c3d4e5f';
  let store: StoreService;
  beforeEach(async () => {
    sessionStorage.clear();
    sessionFor();
    const api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    store.lotes.set([{ id: 'TT-OWN-0001', empresaId: OWN_COMPANY, nombre: 'Algodón de Ica' } as never, { id: 'TT-FOR-0002', empresaId: FOREIGN_COMPANY, nombre: 'Secreto' } as never]);
  });
  afterEach(() => sessionStorage.clear());

  it('el título es «Evaluación · nombre del lote», nunca el UUID', () => {
    const titulo = store.tituloControl({ tipo: 'Evaluación', loteId: 'TT-OWN-0001' });
    expect(titulo).toBe('Evaluación · Algodón de Ica');
    expect(titulo).not.toContain(UUID);
    expect(store.tituloControl({ tipo: 'Reevaluación', loteId: 'TT-OWN-0001' })).toBe('Reevaluación · Algodón de Ica');
  });

  it('con un lote ajeno el título no filtra el nombre', () => {
    const titulo = store.tituloControl({ tipo: 'Evaluación', loteId: 'TT-FOR-0002' });
    expect(titulo).toBe('Evaluación · ' + NOMBRE_OCULTO);
    expect(titulo).not.toContain('Secreto');
  });

  it('el id corto de control y de transferencia va en el texto de title/aria-label', () => {
    expect(store.refControl({ id: UUID })).toBe('Control 9F8E7D6C');
    expect(store.refTransferencia(UUID)).toBe('Transferencia 9F8E7D6C');
    expect(store.refControl({ id: UUID })).not.toContain(UUID);
  });

  it('dos controles con el mismo comienzo reciben referencias distintas', () => {
    const otro = UUID.slice(0, 10) + 'ffffffff-ffff-ffffffffffff'.slice(0, UUID.length - 10);
    store.controles.set([{ id: UUID } as never, { id: otro } as never]);
    expect(store.referencia(UUID)).not.toBe(store.referencia(otro));
  });
});
