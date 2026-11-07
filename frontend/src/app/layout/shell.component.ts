import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Rol } from '../core/models';
import { DraftStorage } from '../core/draft-storage.service';
import { SessionService } from '../core/session.service';
import { StoreService } from '../core/store.service';
import { IconComponent } from '../shared/icon.component';
import { DialogComponent, UiService } from '../shared/ui';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  roles: Rol[];
}

const O: Rol = 'Operario';
const A: Rol = 'Administrador de Empresa';
const D: Rol = 'Dueño de Licencia';

export const NAV: NavItem[] = [
  { path: '/trazabilidad', label: 'Trazabilidad', icon: 'trace', roles: [O, A] },
  { path: '/lotes', label: 'Lotes', icon: 'lot', roles: [O, A] },
  { path: '/procesos', label: 'Procesos', icon: 'process', roles: [O, A] },
  { path: '/calidad', label: 'Calidad', icon: 'quality', roles: [A] },
  { path: '/transferencias', label: 'Transferencias', icon: 'transfer', roles: [O, A] },
  { path: '/analitica', label: 'Analítica', icon: 'analytics', roles: [A] },
  { path: '/maquinaria', label: 'Maquinaria', icon: 'machine', roles: [O, A] },
  { path: '/empresas', label: 'Empresas', icon: 'company', roles: [D] },
  { path: '/usuarios', label: 'Usuarios', icon: 'user', roles: [A, D] },
  { path: '/privacidad', label: 'Privacidad B2B', icon: 'privacy', roles: [A] },
  { path: '/licencia', label: 'Licencia', icon: 'license', roles: [D] },
];

@Component({
  selector: 'app-shell',
  host: { '(window:offline)': 'online.set(false)', '(window:online)': 'online.set(true)' },
  imports: [NgTemplateOutlet, RouterOutlet, RouterLink, RouterLinkActive, IconComponent, DialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shell" [class.expanded]="expanded()" [class.mobile-open]="mobileOpen()">
      <aside class="rail" aria-label="Navegación principal" [style.--nav-count]="items().length + 4">
        <div class="brand">
          <a routerLink="/" class="mark" aria-label="TrazaTex — Trazabilidad">
            <img class="word" src="brand/mark-blanco-480.webp" alt="TrazaTex" />
          </a>
        </div>
        <div class="ctx" [attr.data-tip]="contexto()">
          <app-icon class="ctx-icon" [name]="session.esDueno() ? 'license' : 'company'" [size]="20" />
          <span class="ctx-text"
            ><b [attr.title]="contexto()">{{ contexto() }}</b></span
          >
        </div>

        <nav class="nav">
          @for (item of items(); track item.path) {
            <a
              class="nav-item"
              [routerLink]="item.path"
              routerLinkActive="active"
              [attr.data-tip]="expanded() ? '' : item.label"
              (click)="mobileOpen.set(false)"
            >
              <app-icon [name]="item.icon" [size]="20" />
              <span class="txt">{{ item.label }}</span>
            </a>
          }
        </nav>

        <div class="bottom">
          <a class="nav-item" routerLink="/notificaciones" routerLinkActive="active" [attr.data-tip]="expanded() ? '' : 'Notificaciones'" [attr.aria-label]="etiquetaNotificaciones()" (click)="mobileOpen.set(false)">
            <span class="bell">
              <app-icon name="bell" [size]="20" />
              @if (store.unreadCount() > 0) { <span class="dot" data-testid="unread-badge" aria-hidden="true">{{ store.unreadBadge() }}</span> }
            </span>
            <span class="txt">Notificaciones</span>
          </a>
          <a class="nav-item" routerLink="/perfil" routerLinkActive="active" [attr.data-tip]="expanded() ? '' : 'Perfil'" (click)="mobileOpen.set(false)">
            <app-icon class="profile-icon" name="user" [size]="21" />
            <span class="txt">{{ session.usuario().nombres }} {{ session.usuario().apellidos.split(' ')[0] }}</span>
          </a>
          <button class="nav-item" type="button" (click)="logout()" [attr.data-tip]="expanded() ? '' : 'Cerrar sesión'">
            <app-icon name="logout" [size]="20" />
            <span class="txt">Cerrar sesión</span>
          </button>
          <button class="nav-item toggle" type="button" (click)="toggle()" [attr.aria-label]="expanded() ? 'Contraer menú' : 'Expandir menú'">
            <app-icon [name]="expanded() ? 'chevronLeft' : 'chevronRight'" [size]="18" />
            <span class="txt">Contraer</span>
          </button>
        </div>
      </aside>
      <div class="scrim" (click)="mobileOpen.set(false)"></div>

      <div class="main">
        <header class="topbar">
          <button class="x menu-btn" type="button" (click)="mobileOpen.set(true)" aria-label="Abrir menú"><app-icon name="menu" /></button>
          <img class="top-word" src="brand/wordmark-verde-480.webp" alt="TrazaTex" />
          <div class="top-current"><app-icon [name]="iconoSeccion()" [size]="18" /><span role="heading" aria-level="1">{{ tituloSeccion() }}</span></div>
          <div class="actions header-actions"><ng-container [ngTemplateOutlet]="ui.headerActions()" /></div>
          <div class="proto">@if (ui.readOnlyCount()) { <span class="header-readonly" role="img" aria-label="Solo lectura" data-tip="Tu rol solo puede consultar esta sección."><app-icon name="eye" [size]="16" /></span> }<span class="label">{{ session.rol() }}</span></div>
          <a class="x bell-top" routerLink="/notificaciones" [attr.aria-label]="etiquetaNotificaciones()">
            <app-icon name="bell" />
            @if (store.unreadCount() > 0) { <span class="dot" data-testid="unread-badge-top" aria-hidden="true">{{ store.unreadBadge() }}</span> }
          </a>
        </header>
        @if (!online()) { <div role="alert" class="notice">Sin conexión. Conservamos el formulario para que puedas reintentar.</div> }
        @if (drafts.notice()) { <div role="status" class="notice">{{ drafts.notice() }}</div> }
        @if (store.loading()) { <p role="status" class="label">Cargando datos…</p> }
@if (store.saving()) {
  <p role="status" class="label" data-testid="saving">{{ store.uploadProgress() === null ? 'Guardando cambios…' : 'Subiendo… ' + store.uploadProgress() + ' %' }}</p>
  @if (store.uploadProgress() !== null) { <progress class="upload-progress" max="100" [value]="store.uploadProgress()" aria-label="Progreso de la subida"></progress> }
}
@if (store.error()) { <div role="alert">{{ store.error() }} <button type="button" class="btn btn--ghost" (click)="store.refresh()">Reintentar</button></div> }
<main class="content" [attr.aria-busy]="store.loading() || store.saving()">
          <router-outlet />
        </main>
      </div>
    </div>

    @if (ui.confirmation(); as request) {
      <app-dialog title="Confirmar acción" (closed)="ui.answerConfirmation(false)">
        <p>{{ request.message }}</p>
        <ng-container footer>
          <button type="button" class="btn btn--ghost" (click)="ui.answerConfirmation(false)">Cancelar</button>
          <button type="button" class="btn btn--primary" (click)="ui.answerConfirmation(true)">Confirmar</button>
        </ng-container>
      </app-dialog>
    }
    <div class="toasts">
      @for (t of ui.toasts(); track t.id) {
        <div class="toast" [class.toast--error]="t.error" [attr.role]="t.error ? 'alert' : 'status'">
          <span>{{ t.text }}</span>
          @if (t.error) { <button type="button" class="toast-close" aria-label="Cerrar aviso de error" (click)="ui.dismissToast(t.id)">Cerrar</button> }
        </div>
      }
    </div>
  `,
  styles: `
    .top-current { min-width: 0; max-width: 100%; }
    .upload-progress { display: block; width: min(24rem, 100%); height: 0.5rem; margin: 0 0 0.5rem; accent-color: var(--green-600); }
    .top-current span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .shell { display: grid; grid-template-columns: var(--rail) minmax(0, 1fr); min-height: 100dvh; transition: grid-template-columns 220ms var(--ease-out); }
    .shell.expanded { grid-template-columns: var(--rail-open) minmax(0, 1fr); }
    .expanded .rail { width: var(--rail-open); }
    .rail {
      position: fixed; left: 0; top: 0; width: var(--rail); height: 100dvh; z-index: 50;
      --item-height: min(2.6rem, calc((100dvh - 12rem) / var(--nav-count)));
      display: flex; flex-direction: column;
      background: linear-gradient(rgb(4 29 17 / .48), rgb(4 29 17 / .48)), url('/tex/verde.webp') center / 380px, var(--green-950);
      background-blend-mode: normal;
      color: var(--cream);
      border-right: 1px solid rgb(200 169 121 / 0.18);
    }
    .brand { flex-shrink: 0; padding: 0.9rem 0 0.7rem; display: flex; justify-content: center; }
    .expanded .brand { justify-content: flex-start; padding-left: 1rem; }
    .mark { display: flex; align-items: center; gap: 0.6rem; }
    .mark img:first-child { height: 32px; width: auto; max-width: 100%; object-fit: contain; }
    .word { display: block; height: 18px; width: auto; }
    .expanded .word { display: block; }
    .ctx { flex-shrink: 0; display: flex; align-items: center; gap: 0.65rem; margin: .15rem .55rem .95rem; padding: .75rem .3rem .95rem; justify-content: center; border-bottom: 0; }
    .expanded .ctx { justify-content: flex-start; }
    .ctx[data-tip]::after { left: 100%; top: 50%; bottom: auto; translate: 8px -50%; }
    .expanded .ctx[data-tip]::after { display: none; }
    .ctx-icon { color: var(--green-900); background: #fffdf6; border-radius: 50%; flex: none; width: 2.4rem; height: 2.4rem; display: inline-flex; align-items: center; justify-content: center; box-shadow: 0 1px 0 rgb(246 241 228 / .3); }
    .ctx-text { display: none; min-width: 0; line-height: 1.2; }
    .ctx-text .label { color: var(--kraft); display: block; }
    .ctx-text b { font-size: 0.82rem; font-weight: 650; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .expanded .ctx-text { display: block; }
    .nav { flex-shrink: 0; display: grid; gap: 2px; padding: 0 0.5rem; }
    .bottom { flex-shrink: 0; margin-top: auto; display: grid; gap: 2px; padding: 0.6rem 0.5rem 0.75rem; border-top: 0; }
    .nav-item {
      position: relative; display: flex; align-items: center; gap: 0.8rem;
      height: var(--item-height); min-height: 0; padding: 0 0.75rem; justify-content: center;
      color: var(--muted-on-green); text-decoration: none; border: 0; background: none; cursor: pointer;
      font-weight: 600; font-size: 0.88rem; width: 100%;
      transition: background-color 140ms ease, color 140ms ease;
    }
    .expanded .nav-item { justify-content: flex-start; }
    .nav-item:hover { color: var(--cream); background: rgb(239 231 210 / 0.09); }
    .nav-item.active { color: var(--green-900); background: var(--cream); }
    .nav-item.active::before { display: none; }
    .nav-item[data-tip]::after { left: 100%; top: 50%; bottom: auto; translate: 10px -50%; }
    .nav-item[data-tip]:hover::after { translate: 6px -50%; }
    .nav-item :focus-visible, .nav-item:focus-visible { outline-color: var(--cream); }
    .txt { display: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .expanded .txt { display: inline; }
    /* El contador va AL LADO de la campana, no encima (FE-I8): así no tapa el icono ni se recorta con «99+». */
    .bell { display: inline-flex; align-items: flex-start; gap: 2px; }
    .dot { flex: none; margin-top: -6px; min-width: 1rem; height: 1rem; padding: 0 0.2rem; display: grid; place-items: center; background: var(--kraft); color: var(--green-950); font: 700 0.58rem/1 var(--f-body); font-style: normal; }
    .profile-icon { flex: none; }
    .rail .nav-item app-icon { flex: none; color: inherit; }
    .toggle { opacity: 0.7; }

    .main { grid-column: 2; min-width: 0; display: flex; flex-direction: column; }
    .topbar {
      position: sticky; top: 0; z-index: 40;
      display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap;
      padding: 0.5rem var(--gutter);
      background: linear-gradient(90deg, #f5efe3 0%, #fdf9f0 58%, #f5efe3 100%);
      border-bottom: 0;
    }
    .menu-btn, .top-word, .bell-top { display: none; }
    .top-word { height: 18px; width: auto; }
    .top-current { text-align: left; display: flex; align-items: center; gap: .5rem; font-size: .9rem; font-weight: 700; color: var(--green-800); }
    .top-current app-icon { color: var(--green-700); }
    @media (max-width: 680px) { .top-current { display: flex; } }
    .header-actions { margin-left: auto; gap: .4rem; }
    .header-actions .btn { white-space: normal; }
    .header-actions:empty { display: none; }
    .header-readonly { display: inline-flex; color: var(--brown-ink); margin-right: .4rem; }
    .proto .label { white-space: nowrap; font-weight: 700; }
    .proto { border-left: 0 !important; display: flex; align-items: center; justify-content: flex-end; margin-left: auto; order: 5; }
    .proto-tag { padding: 0.2rem 0.35rem; color: var(--brown-ink); text-transform: none; letter-spacing: 0; font-size: 0.75rem; }
    .proto .seg button { min-height: 2rem; font-size: 0.74rem; padding: 0 0.65rem; }
    .company { width: auto; min-height: 2rem; padding-top: 0.2rem; padding-bottom: 0.2rem; font-size: 0.82rem; }
    .bell-top { align-items: flex-start; gap: 2px; color: var(--green-800); }
    .content { flex: 1; min-width: 0; }
    .scrim { display: none; }

    @media (max-width: 900px) {
      .shell, .shell.expanded { grid-template-columns: minmax(0, 1fr); }
      .main { grid-column: 1; }
      .rail { position: fixed; left: 0; top: 0; bottom: 0; width: var(--rail-open); translate: -100% 0; transition: translate 260ms var(--ease-expo); }
      .mobile-open .rail { translate: 0 0; }
      .rail .txt, .rail .word, .rail .ctx-text { display: inline; }
      .rail .ctx-text { display: block; }
      .rail .nav-item, .rail .ctx { justify-content: flex-start; }
      .rail .brand { justify-content: flex-start; padding-left: 1rem; }
      .rail .word { display: block; }
      .rail [data-tip]::after { display: none; }
      .toggle { display: none; }
      .mobile-open .scrim { display: block; position: fixed; inset: 0; z-index: 45; background: rgb(9 31 21 / 0.4); }
      .menu-btn { display: inline-grid; }
      .bell-top { display: inline-flex; }
      .top-word { display: block; }
      .proto { order: 5; width: auto; margin-left: auto; }
      .proto-tag { display: none; }
      .bell-top { margin-left: auto; }
    }

    @media (max-height: 650px) {
      .rail { --item-height: min(2.6rem, calc((100dvh - 5rem) / var(--nav-count))); }
      .brand { padding-top: .25rem; padding-bottom: .25rem; }
      .mark img:first-child { height: 24px; }
      .ctx { margin-top: 0; margin-bottom: .25rem; padding-top: .15rem; padding-bottom: .15rem; }
      .ctx-icon { width: 1.5rem; height: 1.5rem; }
      .nav, .bottom { gap: 0; }
      .bottom { padding-top: .2rem; padding-bottom: .2rem; }
      .nav-item { font-size: clamp(.65rem, 2.2dvh, .88rem); }
      .nav-item app-icon { width: min(20px, calc(var(--item-height) - 2px)); height: min(20px, calc(var(--item-height) - 2px)); }
      .nav-item app-icon svg { width: 100%; height: 100%; }
    }
  `,
})
export class ShellComponent {
  readonly online = signal(navigator.onLine);
  readonly drafts = inject(DraftStorage);
  readonly session = inject(SessionService);
  readonly store = inject(StoreService);
  readonly ui = inject(UiService);
  private readonly router = inject(Router);

  /** Nombre accesible de los accesos a la bandeja: el punto visual es aria-hidden, así que el aviso va aquí. */
  readonly etiquetaNotificaciones = computed(() => {
    const unread = this.store.unreadCount();
    return unread > 0 ? 'Notificaciones, ' + (unread > 99 ? 'más de 99' : unread) + ' sin leer' : 'Notificaciones';
  });
  readonly currentPath = signal(this.router.url);
  readonly navActual = computed(() => NAV.find((n) => this.currentPath().startsWith(n.path)));
  readonly tituloSeccion = computed(() => {
    const path = this.currentPath().split('?')[0].split('/');
    if (path[1] === 'lotes' && path[2]) { const lot = this.store.lote(decodeURIComponent(path[2])); return lot ? (this.store.esPropio(lot) ? lot.nombre : 'Lote compartido') + ' / ' + lot.id : 'Detalle del lote'; }
    if (path[1] === 'calidad' && path[2]) { const control = this.store.control(path[2]); return control ? control.tipo + ' / ' + this.store.nombreLote(control.loteId) : 'Control de calidad'; }
    if (path[1] === 'procesos' && path[2]) { const process = this.store.proceso(path[2]); return process ? this.store.nombreProceso(process) : 'Detalle del proceso'; }
    return this.currentPath().startsWith('/perfil') ? this.session.usuario().nombres + ' ' + this.session.usuario().apellidos : this.currentPath().startsWith('/notificaciones') ? 'Notificaciones' : this.navActual()?.label ?? 'TrazaTex';
  });
  readonly iconoSeccion = computed(() => this.currentPath().startsWith('/notificaciones') ? 'bell' : this.currentPath().startsWith('/fallas') ? 'failure' : this.currentPath().startsWith('/perfil') ? 'user' : this.navActual()?.icon ?? 'lot');
  readonly expanded = signal(this.readExpanded());
  readonly mobileOpen = signal(false);

  readonly roles: { rol: Rol; corto: string }[] = [
    { rol: 'Operario', corto: 'Operario' },
    { rol: 'Administrador de Empresa', corto: 'Administrador' },
    { rol: 'Dueño de Licencia', corto: 'Dueño de licencia' },
  ];

  readonly items = computed(() => NAV.filter((n) => n.roles.includes(this.session.rol())));
  readonly contexto = computed(() =>
    this.session.esDueno() ? 'Empresas de la licencia' : this.store.nombreEmpresa(this.session.empresaId()),
  );

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.mobileOpen.set(false);
      this.currentPath.set(this.router.url);
    });
  }

  toggle() {
    this.expanded.update((v) => !v);
    try {
      localStorage.setItem('trazatex.rail', this.expanded() ? '1' : '0');
    } catch {}
  }
  private readExpanded() {
    try {
      const pref = localStorage.getItem('trazatex.rail');
      return pref === null ? true : pref === '1';
    } catch {
      return true;
    }
  }

  logout() {
    this.session.logout();
    this.router.navigateByUrl('/login');
  }
}


