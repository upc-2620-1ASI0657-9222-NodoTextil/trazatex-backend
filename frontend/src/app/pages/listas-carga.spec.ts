import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ApiService } from '../core/api.service';
import { StoreService } from '../core/store.service';
import { CalidadPage } from './calidad/calidad.page';
import { EmpresasPage } from './empresas.page';
import { FallasPage } from './fallas/fallas.page';
import { LotesPage } from './lotes/lotes.page';
import { MaquinariaPage } from './maquinaria.page';
import { ProcesosPage } from './procesos/procesos.page';
import { TransferenciasPage } from './transferencias.page';
import { UsuariosPage } from './usuarios.page';

class FakeApi {
  readonly calls: string[] = [];
  async request<T>(method: string, path: string): Promise<T> { this.calls.push(method + ' ' + path); throw new Error('sin respuesta de prueba: ' + path); }
  async all<T>(path: string): Promise<T[]> { this.calls.push('ALL ' + path); return []; }
}

const LISTAS: [string, Type<unknown>, RegExp][] = [
  ['Lotes', LotesPage, /Sin lotes en esta vista/],
  ['Procesos', ProcesosPage, /Sin procesos en esta vista/],
  ['Transferencias', TransferenciasPage, /Sin transferencias en esta vista/],
  ['Maquinaria', MaquinariaPage, /Sin maquinaria en este estado/],
  ['Empresas', EmpresasPage, /Sin empresas en esta vista/],
  ['Usuarios', UsuariosPage, /Sin usuarios/],
  ['Fallas', FallasPage, /Sin fallas en esta vista/],
  ['Calidad', CalidadPage, /Sin controles en esta vista/],
];

describe('Listas: carga con marcador y no con un falso vacío (FE-M11)', () => {
  async function pintar(pagina: Type<unknown>, cargando: boolean) {
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
    store.loading.set(cargando);
    const fixture = TestBed.createComponent(pagina);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }
  afterEach(() => sessionStorage.clear());

  for (const [nombre, pagina, vacio] of LISTAS) {
    it(`${nombre}: mientras carga muestra el marcador y NO el texto de vacío`, async () => {
      const dom = await pintar(pagina, true);
      expect(dom.querySelector('[data-testid="skeleton"]')).not.toBeNull();
      expect(dom.textContent).not.toMatch(vacio);
      expect(dom.querySelector('[data-testid="skeleton"]')?.textContent).toContain('Cargando');
    });

    it(`${nombre}: terminada la carga, sin datos, dice que está vacío y quita el marcador`, async () => {
      const dom = await pintar(pagina, false);
      expect(dom.querySelector('[data-testid="skeleton"]')).toBeNull();
      expect(dom.textContent).toMatch(vacio);
    });
  }
});
