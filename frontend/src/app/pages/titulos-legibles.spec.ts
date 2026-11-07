import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from '../core/api.service';
import { StoreService } from '../core/store.service';
import { UiService } from '../shared/ui';
import { fakeIndexedDB } from '../testing/fake-indexeddb';
import { CalidadPage } from './calidad/calidad.page';
import { TransferenciasPage } from './transferencias.page';

const UUID = '9f8e7d6c-1a2b-4c3d-8e9f-0a1b2c3d4e5f';

class FakeApi {
  readonly calls: string[] = [];
  async request<T>(method: string, path: string): Promise<T> { this.calls.push(method + ' ' + path); throw new Error('sin respuesta de prueba: ' + path); }
  async all<T>(path: string): Promise<T[]> { this.calls.push('ALL ' + path); return []; }
}

describe('Calidad y Transferencias: títulos sin UUID largo (FE-I8)', () => {
  let store: StoreService;
  async function preparar() {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Admin', role: 'COMPANY_ADMIN', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    const api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
  }
  afterEach(() => sessionStorage.clear());

  it('la lista de Calidad titula «Evaluación · nombre del lote» y deja el id corto en title', async () => {
    await preparar();
    store.lotes.set([{ id: 'TT-1', empresaId: 'c1', nombre: 'Algodón de Ica', material: 'Algodón' } as never]);
    store.controles.set([{ id: UUID, loteId: 'TT-1', empresaId: 'c1', tipo: 'Evaluación', inicio: '2026-01-02T10:00:00Z', pruebas: [], evidencias: [] } as never]);
    const fixture = TestBed.createComponent(CalidadPage);
    await fixture.whenStable();
    fixture.detectChanges();
    const fila = fixture.nativeElement.querySelector('button.row-card') as HTMLElement;
    expect(fila.querySelector('.rc-title')?.textContent?.trim()).toBe('Evaluación · Algodón de Ica');
    expect(fila.querySelector('.rc-id')?.textContent?.trim()).toBe('9F8E7D6C');
    expect(fila.getAttribute('title')).toBe('Control 9F8E7D6C');
    expect(fila.textContent).not.toContain(UUID);

    fila.click();
    fixture.detectChanges();
    const cajon = fixture.nativeElement.querySelector('app-drawer') as HTMLElement;
    expect(cajon.querySelector('h2')?.textContent?.trim()).toBe('Evaluación · Algodón de Ica');
    expect(cajon.querySelector('.label')?.textContent?.trim()).toBe('9F8E7D6C');
  });

  it('la lista de Transferencias muestra el id corto y lo deja también en title; el diálogo de rechazo no usa el UUID', async () => {
    await preparar();
    store.transferencias.set([{ id: UUID, estado: 'Pendiente', origenId: 'c2', destinoId: 'c1', lotesIds: [], inicio: '2026-01-02T10:00:00Z' } as never]);
    const fixture = TestBed.createComponent(TransferenciasPage);
    await fixture.whenStable();
    fixture.detectChanges();
    const fila = fixture.nativeElement.querySelector('button.row-card') as HTMLElement;
    expect(fila.querySelector('.rc-id')?.textContent?.trim()).toBe('9F8E7D6C');
    expect(fila.getAttribute('title')).toBe('Transferencia 9F8E7D6C');
    expect(fila.textContent).not.toContain(UUID);

    fila.click();
    store.loadedTransferDetails.set([UUID]);
    fixture.detectChanges();
    const rechazar = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent?.trim() === 'Rechazar') as HTMLButtonElement;
    rechazar.click();
    fixture.detectChanges();
    const titulos = [...fixture.nativeElement.querySelectorAll('app-dialog h2')].map((h: HTMLElement) => h.textContent?.trim());
    expect(titulos).toContain('Rechazar transferencia 9F8E7D6C');
    expect(titulos.join(' ')).not.toContain(UUID);
  });
});

describe('Avisos de transferencia sin UUID (FE-M13)', () => {
  const TRF = '9f8e7d6c-1a2b-4c3d-8e9f-0a1b2c3d4e5f';
  async function pagina(accion: (api: FakeApiTransfer) => void = () => undefined) {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Admin', role: 'COMPANY_ADMIN', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    vi.stubGlobal('indexedDB', fakeIndexedDB());
    const api = new FakeApiTransfer();
    accion(api);
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    const store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    store.lotes.set([{ id: 'TT-7', empresaId: 'c1', nombre: 'Hilo Pima', material: 'Algodón' } as never]);
    const fixture = TestBed.createComponent(TransferenciasPage);
    const ui = TestBed.inject(UiService);
    const avisos: string[] = [];
    vi.spyOn(ui, 'toast').mockImplementation((texto: string) => { avisos.push(texto); });
    vi.spyOn(ui, 'confirm').mockResolvedValue(true);
    return { fixture, store, avisos, page: fixture.componentInstance, api };
  }
  afterEach(() => { sessionStorage.clear(); vi.unstubAllGlobals(); });

  it('al aceptar, el aviso usa el id corto y el nombre del lote', async () => {
    const { page, avisos, store } = await pagina();
    vi.spyOn(store, 'aceptarTransferencia').mockResolvedValue({ ok: true } as never);
    await page.aceptar({ id: TRF, lotesIds: ['TT-7'] } as never);
    expect(avisos).toEqual(['Transferencia 9F8E7D6C aceptada / Hilo Pima ahora bajo tu empresa']);
    expect(avisos[0]).not.toContain(TRF);
  });

  it('al rechazar, el aviso usa el id corto', async () => {
    const { page, avisos, store } = await pagina();
    vi.spyOn(store, 'rechazarTransferencia').mockResolvedValue({ ok: true } as never);
    await page.rechazar({ id: TRF, lotesIds: [] } as never);
    expect(avisos).toEqual(['Transferencia 9F8E7D6C rechazada / lotes devueltos al origen']);
  });

  it('al enviar una transferencia nueva, el aviso usa el id corto', async () => {
    const { page, avisos, store } = await pagina();
    vi.spyOn(store, 'iniciarTransferencia').mockResolvedValue({ ok: true, id: TRF } as never);
    await page.iniciar();
    expect(avisos).toEqual(['Transferencia 9F8E7D6C enviada / Pendiente']);
  });
});

class FakeApiTransfer extends FakeApi {}
