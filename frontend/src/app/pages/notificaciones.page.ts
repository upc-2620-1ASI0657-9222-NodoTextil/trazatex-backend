import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { CodigoNotificacion, Notificacion, TIPO_DE_NOTIFICACION } from '../core/models';
import { StoreService } from '../core/store.service';
import { DENSITY, SubnavItem } from '../shared/density';
import { SHARED, UiService } from '../shared/ui';
import { errorMessage } from '../core/api.service';

const ICONO: Record<string, string> = {
  Invitación: 'users',
  'Transferencia entrante': 'transfer',
  'Transferencia aceptada': 'transfer',
  'Transferencia rechazada': 'transfer',
  'Alerta de calidad': 'quality',
  'Posible falla derivada': 'failure',
  'Solicitud de administrador': 'user',
  'Solicitud revisada': 'user',
  Capacidad: 'license',
};

@Component({
  selector: 'app-notificaciones',
  imports: [...SHARED, ...DENSITY],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <ng-template appHeaderActions>
        @if (!store.noLeidas()) { <small class="action-reason" id="leidas-razon">No hay notificaciones sin leer.</small> }
        <button class="btn btn--ghost" type="button" [disabled]="!store.noLeidas()" [attr.aria-describedby]="store.noLeidas() ? null : 'leidas-razon'" (click)="store.marcarTodasLeidas()"><app-icon name="check" [size]="16" /> Marcar como leídas</button>
      </ng-template>

      @if (store.notificationError()) {
        <div class="notice notice--warn" role="alert">{{ store.notificationError() }} <button class="btn btn--ghost btn--sm" type="button" (click)="store.refresh()">Reintentar</button></div>
      }
      @if (store.loading() && !store.misNotificaciones().length) { <p role="status">Cargando notificaciones…</p> }
      <app-subnav panel="notificaciones" [items]="vistas()" [active]="filtro()" (activeChange)="cambiarVista($any($event))" label="Bandeja" />
      <div appTabPanel="notificaciones" [tabActive]="filtro()">

      <div class="filters">
        <select class="select" [value]="tipo()" (change)="cambiarTipo($any($event.target).value)" aria-label="Tipo">
          <option value="">Todo tipo</option>
          @for (t of tipos; track t.codigo) {
            <option [value]="t.codigo">{{ t.etiqueta }}</option>
          }
        </select>
      </div>

      @if (lista().length) {
        <ul class="inbox">
          @for (n of lista(); track n.id) {
            <li [class.unread]="!n.leida">
              <span class="ico"><app-icon [name]="icono(n.tipo)" [size]="18" /></span>
              <button class="body" type="button" (click)="abrir(n)">
                <span class="label">{{ n.tipo }} / {{ n.fecha | fecha }}</span>
                <b>{{ n.titulo }}</b>
                <span class="small muted">{{ n.descripcion }}</span>
                @if (n.entidad) {<span class="ref id">{{ n.entidad.tipo === 'lote' ? 'Ver lote ' + store.nombreLote(n.entidad.id) + otrosLotes(n) : n.entidad.tipo === 'transferencia' ? 'Ver transferencia' : 'Ver detalle' }} <app-icon name="arrowRight" [size]="12" /></span>}
              </button>
              <button type="button" (click)="store.marcarLeida(n.id, !n.leida)" [attr.aria-label]="n.leida ? 'Marcar como no leída' : 'Marcar como leída'" [attr.data-tip]="n.leida ? 'Marcar como no leída' : 'Marcar como leída'" class="tip-right x">
                <span class="dotm" [class.on]="!n.leida"></span>
              </button>
            </li>
          }
        </ul>
        <app-pager [total]="store.notificacionesTotal()" [page]="page()" (pageChange)="cambiarPagina($event)" [size]="size()" (sizeChange)="cambiarTamano($event)" />
      } @else if (!store.notificationError() && !store.loading()) {
        <div class="empty"><app-icon name="inbox" [size]="28" /><strong>Bandeja al día</strong><span>No hay notificaciones para este filtro.</span></div>
      }
      </div>
    </div>
  `,
  styles: `
    .highlights { margin-bottom: 1.2rem; }
    .highlights > h2 { margin-bottom: .5rem; }
    .highlights .slide { text-align: left; cursor: pointer; font: inherit; }

    .inbox { list-style: none; margin: 0; padding: 0; background: var(--cream-50); border-top: 1px solid var(--hair-2); }
    li { display: grid; grid-template-columns: 2.5rem 1fr 2.5rem; gap: .6rem; align-items: start; padding: .85rem 1rem; border-bottom: 1px solid var(--hair); position: relative; }
    li.unread { background: #fffaf0; }
    li.unread::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 3px; background: var(--green-800); }
    .ico { display: grid; place-items: center; width: 2.3rem; height: 2.3rem; background: var(--canvas-2); color: var(--green-800); }
    li.unread .ico { background: var(--green-800); color: var(--cream); }
    .body { display: grid; gap: .15rem; text-align: left; border: 0; background: none; padding: 0; cursor: pointer; color: inherit; }
    .body b { font-size: .96rem; }
    li:not(.unread) .body b { font-weight: 600; }
    .ref { display: inline-flex; align-items: center; gap: .25rem; color: var(--green-600); margin-top: .2rem; }
    .dotm { width: .6rem; height: .6rem; border: 1.5px solid var(--green-800); border-radius: 50%; }
    .dotm.on { background: var(--green-800); }
  `,
})
export class NotificacionesPage {
  readonly store = inject(StoreService);
  private readonly router = inject(Router);
  private readonly ui = inject(UiService);
  readonly filtro = signal<'todas' | 'noleidas'>('todas');
  readonly tipo = signal('');
  /** Los filtros por tipo son los códigos del servicio; el texto lo pone el front. */
  readonly tipos: { codigo: CodigoNotificacion; etiqueta: string }[] =
    (Object.entries(TIPO_DE_NOTIFICACION) as [CodigoNotificacion, string][]).map(([codigo, etiqueta]) => ({ codigo, etiqueta }));
  readonly page = signal(0);
  readonly size = signal(6);
  readonly vistas = computed<SubnavItem[]>(() => [
    { id: 'todas', label: 'Todas', count: this.store.notificacionesTodas() ?? undefined },
    { id: 'noleidas', label: 'No leídas', count: this.store.unreadCount() },
  ]);
  /** La página que el servidor devolvió para el filtro actual. */
  readonly lista = computed(() => this.store.notificaciones());
  icono = (t: string) => ICONO[t] ?? 'bell';

  constructor() {
    // Al entrar se parte de «Todas», sin filtro y en la primera página.
    void this.store.consultarNotificaciones({ unread: false, type: '', page: 0 });
    // Marcar como leídas la última página de «No leídas» la deja vacía: se vuelve a la última que tenga elementos.
    effect(() => {
      const total = this.store.notificacionesTotal();
      const page = untracked(() => this.page());
      const last = Math.max(0, Math.ceil(total / this.size()) - 1);
      if (page > last) untracked(() => this.cambiarPagina(last));
    });
  }

  cambiarVista(vista: 'todas' | 'noleidas') { this.filtro.set(vista); this.page.set(0); this.consultar(); }
  cambiarTipo(codigo: string) { this.tipo.set(codigo); this.page.set(0); this.consultar(); }
  cambiarPagina(page: number) { this.page.set(page); this.consultar(); }
  cambiarTamano(size: number) { this.size.set(size); this.page.set(0); this.consultar(); }
  private consultar() {
    void this.store.consultarNotificaciones({ unread: this.filtro() === 'noleidas', type: this.tipo(), page: this.page(), size: this.size() });
  }

  /** Una alerta de falla derivada puede afectar a varios lotes de la empresa: se muestra el primero y cuántos más. */
  otrosLotes(n: Notificacion): string {
    const lotes = n.datos?.['batchIds'];
    return Array.isArray(lotes) && lotes.length > 1 ? ' y ' + (lotes.length - 1) + ' más' : '';
  }

  async abrir(n: Notificacion) {    if (!await this.store.marcarLeida(n.id)) return;
    const e = n.entidad;
    if (!e) return;
    if (e.tipo === 'lote') {
      try {
        await this.store.loadLineage(e.id);
        await this.router.navigate(['/lotes', this.store.codigoLote(e.id)]);
      } catch (error) { this.ui.toast(errorMessage(error), true); }
      return;
    }
    const rutas: Record<string, unknown[]> = {
      lote: ['/lotes', this.store.codigoLote(e.id)],
      proceso: ['/procesos', e.id],
      control: ['/calidad', e.id],
      falla: ['/fallas', e.id],
      transferencia: ['/transferencias'],
      empresa: ['/usuarios'],
      usuario: ['/usuarios'],
      solicitud: ['/usuarios'],
    };
    const queryParams = e.tipo === 'transferencia' ? { transferencia: e.id }
      : e.tipo === 'solicitud' ? { solicitud: e.id } : undefined;
    this.router.navigate(rutas[e.tipo], { queryParams });
  }
}
