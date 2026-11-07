import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from '../../core/api.service';
import { StoreService } from '../../core/store.service';
import { ProcesoDetallePage } from './proceso-detalle.page';

class FakeApi {
  readonly calls: string[] = [];
  async request<T>(method: string, path: string): Promise<T> { this.calls.push(method + ' ' + path); throw new Error('sin respuesta de prueba: ' + path); }
  async all<T>(path: string): Promise<T[]> { this.calls.push('ALL ' + path); return []; }
}

describe('Responsable de un proceso visible para el operario (FE-M14)', () => {
  async function responsable(rol: string, operarioId: string, empresaDelProceso = 'c1') {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u-yo', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Producción', role: rol, status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    const api = new FakeApi();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    const store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    store.lotes.set([{ id: 'TT-1', empresaId: empresaDelProceso, nombre: 'Algodón', material: 'Algodón', cantidadKg: 100 } as never]);
    store.procesos.set([{ id: 'p1', operarioId, empresaId: empresaDelProceso, entradas: [{ loteId: 'TT-1', cantidadKg: 100 }], tipo: 'Hilado', maquinariaId: 'MQ-1', inicio: '2026-01-02T10:00:00Z', salidasIds: [], evidencias: [] } as never]);
    const fixture = TestBed.createComponent(ProcesoDetallePage);
    fixture.componentRef.setInput('id', 'p1');
    await fixture.whenStable();
    fixture.detectChanges();
    const dt = [...(fixture.nativeElement as HTMLElement).querySelectorAll('dt')].find(x => x.textContent?.trim() === 'Operario responsable')!;
    return dt.nextElementSibling!.textContent!.trim();
  }
  afterEach(() => sessionStorage.clear());

  it('el operario ve su propio nombre en sus procesos', async () => {
    expect(await responsable('OPERATOR', 'u-yo')).toBe('Ana Pérez');
  });

  it('el operario ve «Otro miembro de tu empresa» en el proceso de un compañero, no «—»', async () => {
    expect(await responsable('OPERATOR', 'u-otro')).toBe('Otro miembro de tu empresa');
  });

  it('en un proceso de otra empresa sigue siendo privado', async () => {
    const texto = await responsable('OPERATOR', 'u-otro', 'c-ajena');
    expect(texto).not.toContain('Otro miembro');
    expect(texto).not.toContain('Ana');
  });

  it('el administrador, que sí tiene la lista de usuarios, ve el nombre real', async () => {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u-adm', companyId: 'c1', email: 'adm@example.test', firstName: 'Beto', lastName: 'Ruiz', position: 'Admin', role: 'COMPANY_ADMIN', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    const api = new FakeApi();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    const store = TestBed.inject(StoreService);
    TestBed.tick();
    await vi.waitFor(() => expect(api.calls).toContain('ALL /transfers/recipients'));
    await vi.waitFor(() => expect(store.loading()).toBe(false));
    store.usuarios.set([{ id: 'u-otro', nombres: 'Carla', apellidos: 'Díaz', correo: 'c@example.test', cargo: 'Tejido', rol: 'Operario', empresaId: 'c1', estado: 'Activo' } as never]);
    expect(store.nombreResponsable('u-otro')).toBe('Carla Díaz');
    expect(store.nombreResponsable('desconocido')).toBe('—');
  });
});
