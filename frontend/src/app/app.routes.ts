import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { Rol } from './core/models';
import { SessionService } from './core/session.service';
import { ShellComponent } from './layout/shell.component';

const auth: CanActivateFn = (_route, state) => inject(SessionService).loggedIn() || inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });

                                                                                   
const roles =
  (...allowed: Rol[]): CanActivateFn =>
  () =>
    allowed.includes(inject(SessionService).rol()) || inject(Router).parseUrl(inject(SessionService).esDueno() ? '/empresas' : '/trazabilidad');

const A: Rol = 'Administrador de Empresa';
const D: Rol = 'Dueño de Licencia';
const O: Rol = 'Operario';

export const routes: Routes = [
  { path: 'invitations/:token', loadComponent: () => import('./pages/invitation.page').then(m => m.InvitationPage), title: 'Invitación / TrazaTex' },
  { path: 'login', loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage), title: 'TrazaTex / Ingresar' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [auth],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'trazabilidad' },
      { path: 'trazabilidad', canActivate: [roles(O, A)], loadComponent: () => import('./pages/trazabilidad/trazabilidad.page').then((m) => m.TrazabilidadPage), title: 'Trazabilidad / TrazaTex' },
      { path: 'lotes', canActivate: [roles(O, A)], loadComponent: () => import('./pages/lotes/lotes.page').then((m) => m.LotesPage), title: 'Lotes / TrazaTex' },
      { path: 'lotes/:id', canActivate: [roles(O, A)], loadComponent: () => import('./pages/lotes/lote-detalle.page').then((m) => m.LoteDetallePage), title: 'Lote / TrazaTex' },
      { path: 'procesos', canActivate: [roles(O, A)], loadComponent: () => import('./pages/procesos/procesos.page').then((m) => m.ProcesosPage), title: 'Procesos / TrazaTex' },
      { path: 'procesos/:id', canActivate: [roles(O, A)], loadComponent: () => import('./pages/procesos/proceso-detalle.page').then((m) => m.ProcesoDetallePage), title: 'Proceso / TrazaTex' },
      { path: 'calidad', canActivate: [roles(A)], loadComponent: () => import('./pages/calidad/calidad.page').then((m) => m.CalidadPage), title: 'Calidad / TrazaTex' },
      { path: 'calidad/:id', canActivate: [roles(A)], loadComponent: () => import('./pages/calidad/control-detalle.page').then((m) => m.ControlDetallePage), title: 'Calidad / TrazaTex' },
      // Una redirección con cadena descarta el parámetro y aterrizaba en «Calidad»: se devuelve un UrlTree para conservar ?seccion=fallas.
      { path: 'fallas', redirectTo: () => inject(Router).parseUrl('/analitica?seccion=fallas'), pathMatch: 'full' },
      { path: 'fallas/:id', canActivate: [roles(A)], loadComponent: () => import('./pages/fallas/falla-detalle.page').then((m) => m.FallaDetallePage), title: 'Fallas / TrazaTex' },
      { path: 'transferencias', canActivate: [roles(O, A)], loadComponent: () => import('./pages/transferencias.page').then((m) => m.TransferenciasPage), title: 'Transferencias / TrazaTex' },
      { path: 'maquinaria', canActivate: [roles(O, A)], loadComponent: () => import('./pages/maquinaria.page').then((m) => m.MaquinariaPage), title: 'Maquinaria / TrazaTex' },
      { path: 'analitica', canActivate: [roles(A)], loadComponent: () => import('./pages/analitica.page').then((m) => m.AnaliticaPage), title: 'Analítica / TrazaTex' },
      { path: 'usuarios', canActivate: [roles(A, D)], loadComponent: () => import('./pages/usuarios.page').then((m) => m.UsuariosPage), title: 'Usuarios / TrazaTex' },
      { path: 'privacidad', canActivate: [roles(A)], loadComponent: () => import('./pages/privacidad.page').then((m) => m.PrivacidadPage), title: 'Privacidad B2B / TrazaTex' },
      { path: 'empresas', canActivate: [roles(D)], loadComponent: () => import('./pages/empresas.page').then((m) => m.EmpresasPage), title: 'Empresas / TrazaTex' },
      { path: 'licencia', canActivate: [roles(D)], loadComponent: () => import('./pages/licencia.page').then((m) => m.LicenciaPage), title: 'Licencia / TrazaTex' },
      { path: 'notificaciones', loadComponent: () => import('./pages/notificaciones.page').then((m) => m.NotificacionesPage), title: 'Notificaciones / TrazaTex' },
      { path: 'perfil', loadComponent: () => import('./pages/perfil.page').then((m) => m.PerfilPage), title: 'Perfil / TrazaTex' },
    ],
  },
  { path: '**', redirectTo: '' },
];

