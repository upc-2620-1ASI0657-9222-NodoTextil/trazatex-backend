import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { Rol } from '../core/models';
import { RolNoCoincide, SessionService } from '../core/session.service';
import { LoginPage } from './login.page';

describe('Login: el rol elegido debe coincidir con el de la cuenta (H4)', () => {
  let real: string;
  let login: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  async function crear() {
    real = 'Operario';
    login = vi.fn(async (_email: string, _password: string, rol?: Rol) => { if (rol && rol !== real) throw new RolNoCoincide(rol); });
    const session = { endNotice: signal(''), login, rol: () => real, esDueno: () => real === 'Dueño de Licencia' };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: SessionService, useValue: session }] });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    return fixture;
  }
  const entrar = async (fixture: Awaited<ReturnType<typeof crear>>) => { await fixture.componentInstance.entrar(); fixture.detectChanges(); };
  const alerta = (fixture: Awaited<ReturnType<typeof crear>>) => (fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement).textContent?.trim();

  it('conserva el selector y lo enlaza con su ayuda por aria-describedby', async () => {
    const fixture = await crear();
    const ayuda = fixture.nativeElement.querySelector('#rol-ayuda') as HTMLElement;
    expect(ayuda.textContent?.trim()).toBe('Elige el rol registrado en tu cuenta.');
    expect(fixture.nativeElement.querySelector('.roles').getAttribute('aria-describedby')).toBe('rol-ayuda');
    expect(fixture.nativeElement.querySelectorAll('.role').length).toBe(3);
  });

  it('envía el rol elegido para validarlo contra la cuenta', async () => {
    const fixture = await crear();
    fixture.componentInstance.email = ' ana@example.test ';
    fixture.componentInstance.password = 'secreta';
    await entrar(fixture);
    expect(login).toHaveBeenCalledWith('ana@example.test', 'secreta', 'Operario');
  });

  it('si el rol no coincide no entra, explica el motivo y conserva las credenciales', async () => {
    const fixture = await crear();
    fixture.componentInstance.rol.set('Dueño de Licencia');
    real = 'Administrador de Empresa';
    fixture.componentInstance.email = 'admin@example.test';
    fixture.componentInstance.password = 'secreta';
    await entrar(fixture);
    expect(navigate).not.toHaveBeenCalled();
    expect(alerta(fixture)).toBe('Esta cuenta no tiene el rol Dueño de Licencia. Selecciona el rol de tu cuenta y vuelve a ingresar.');
    expect(fixture.componentInstance.email).toBe('admin@example.test');
    expect(fixture.componentInstance.password).toBe('secreta');
    expect(fixture.componentInstance.busy()).toBe(false);
  });

  it('al corregir la selección se borra el aviso y puede entrar sin reiniciar', async () => {
    const fixture = await crear();
    fixture.componentInstance.rol.set('Operario');
    real = 'Administrador de Empresa';
    await entrar(fixture);
    expect(alerta(fixture)).toContain('no tiene el rol Operario');
    const admin = [...fixture.nativeElement.querySelectorAll('.role')].find((b: HTMLButtonElement) => b.textContent?.includes('Administrador')) as HTMLButtonElement;
    admin.click();
    fixture.detectChanges();
    expect(alerta(fixture)).toBe('');
    await entrar(fixture);
    expect(login).toHaveBeenLastCalledWith('', '', 'Administrador de Empresa');
    expect(navigate).toHaveBeenCalledWith('/trazabilidad');
  });

  it('un error de credenciales no se borra al cambiar de rol', async () => {
    const fixture = await crear();
    login.mockRejectedValueOnce(new Error('La sesión no es válida o las credenciales son incorrectas.'));
    await entrar(fixture);
    (fixture.nativeElement.querySelectorAll('.role')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(alerta(fixture)).toBe('La sesión no es válida o las credenciales son incorrectas.');
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
