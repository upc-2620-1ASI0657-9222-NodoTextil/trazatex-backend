import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { StoreService } from '../../core/store.service';
import { ControlDetallePage } from './control-detalle.page';

class FakeApi {
  readonly calls: string[] = [];
  async request<T>(method: string, path: string): Promise<T> { this.calls.push(method + ' ' + path); throw new Error('sin respuesta de prueba: ' + path); }
  async all<T>(path: string): Promise<T[]> { this.calls.push('ALL ' + path); return []; }
}

describe('Control cancelado: motivo legible (FE-M7)', () => {
  async function aviso(cancelReason: string) {
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
    store.lotes.set([{ id: 'TT-1', empresaId: 'c1', nombre: 'Algodón', material: 'Algodón' } as never]);
    store.controles.set([{ id: 'ctrl-1', loteId: 'TT-1', empresaId: 'c1', tipo: 'Evaluación', status: 'CANCELLED', cancelReason, inicio: '2026-01-02T10:00:00Z', pruebas: [], evidencias: [] } as never]);
    const fixture = TestBed.createComponent(ControlDetallePage);
    fixture.componentRef.setInput('id', 'ctrl-1');
    await fixture.whenStable();
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('.notice[role="alert"]')?.textContent?.replace(/\s+/g, ' ').trim();
  }
  afterEach(() => sessionStorage.clear());

  it('dice el motivo con palabras, no con el código de Production', async () => {
    const texto = await aviso('INVALID_PHASE_TRANSITION');
    expect(texto).toBe('No se pudo iniciar este control: el lote ya no estaba disponible para un control (cambió de fase mientras tanto). Regresa a Calidad para revisar el lote.');
    expect(texto).not.toContain('INVALID_PHASE_TRANSITION');
  });

  it('un código que el front no conoce no se muestra en crudo', async () => {
    const texto = await aviso('CODIGO_NUEVO');
    expect(texto).toContain('Producción no aceptó el inicio');
    expect(texto).not.toContain('CODIGO_NUEVO');
  });
});

describe('Informe PDF: fallo con «Reintentar» (FE-M8)', () => {
  it('si la descarga falla queda un aviso con Reintentar, que vuelve a pedir el informe y lo quita al lograrlo', async () => {
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
    store.lotes.set([{ id: 'TT-1', empresaId: 'c1', nombre: 'Algodón', material: 'Algodón' } as never]);
    store.controles.set([{ id: 'ctrl-1', loteId: 'TT-1', empresaId: 'c1', tipo: 'Evaluación', status: 'FINISHED', fin: '2026-01-03T10:00:00Z', inicio: '2026-01-02T10:00:00Z', pruebas: [], evidencias: [] } as never]);
    let intentos = 0;
    (api as unknown as { download: (path: string, name: string) => Promise<void> }).download = async () => {
      intentos++;
      if (intentos === 1) throw new Error('El servicio de informes no responde.');
    };
    const fixture = TestBed.createComponent(ControlDetallePage);
    fixture.componentRef.setInput('id', 'ctrl-1');
    await fixture.whenStable();
    fixture.detectChanges();
    const aviso = () => fixture.nativeElement.querySelector('[data-testid="pdf-error"]') as HTMLElement | null;
    expect(aviso()).toBeNull();

    await fixture.componentInstance.pdf();
    fixture.detectChanges();
    expect(aviso()?.textContent).toContain('El servicio de informes no responde.');
    const reintentar = [...aviso()!.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Reintentar') as HTMLButtonElement;
    reintentar.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(intentos).toBe(2);
    expect(aviso()).toBeNull();
    sessionStorage.clear();
  });
});
