import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { SessionService } from '../core/session.service';
import { UiService } from '../shared/ui';
import { LoginPage } from './login.page';

describe('Login: el rol lo define la cuenta (FE-M4)', () => {
  let real: string;
  let toasts: string[];
  async function crear() {
    real = 'Operario';
    toasts = [];
    const session = { endNotice: signal(''), login: vi.fn(async () => undefined), rol: () => real, esDueno: () => real === 'Dueño de Licencia' };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: SessionService, useValue: session }] });
    vi.spyOn(TestBed.inject(UiService), 'toast').mockImplementation((text: string) => { toasts.push(text); });
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    return fixture;
  }
  const entrar = async (fixture: Awaited<ReturnType<typeof crear>>) => { await fixture.componentInstance.entrar(); fixture.detectChanges(); };

  it('dice «El rol lo define tu cuenta» y lo enlaza con el selector por aria-describedby', async () => {
    const fixture = await crear();
    const ayuda = fixture.nativeElement.querySelector('#rol-ayuda') as HTMLElement;
    expect(ayuda.textContent?.trim()).toBe('El rol lo define tu cuenta.');
    expect(fixture.nativeElement.querySelector('.roles').getAttribute('aria-describedby')).toBe('rol-ayuda');
  });

  it('si la cuenta es de otro rol que el elegido, al entrar se dice cuál es', async () => {
    const fixture = await crear();
    fixture.componentInstance.rol.set('Dueño de Licencia');
    real = 'Administrador de Empresa';
    await entrar(fixture);
    expect(toasts).toEqual(['Tu cuenta es de Administrador de Empresa; entraste con ese rol.']);
  });

  it('si el rol elegido coincide con el de la cuenta no hay aviso', async () => {
    const fixture = await crear();
    fixture.componentInstance.rol.set('Operario');
    await entrar(fixture);
    expect(toasts).toEqual([]);
  });
});

describe('Login: aviso de por qué terminó la sesión (FE-M7)', () => {
  it('muestra el aviso del Gateway como estado y lo oculta si no hay', () => {
    const endNotice = signal('Tu sesión venció. Inicia sesión de nuevo.');
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: SessionService, useValue: { endNotice, login: async () => undefined, rol: () => 'Operario', esDueno: () => false } }] });
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    const aviso = () => fixture.nativeElement.querySelector('[data-testid="fin-sesion"]') as HTMLElement | null;
    expect(aviso()?.getAttribute('role')).toBe('status');
    expect(aviso()?.textContent?.trim()).toBe('Tu sesión venció. Inicia sesión de nuevo.');
    endNotice.set('');
    fixture.detectChanges();
    expect(aviso()).toBeNull();
  });
});
