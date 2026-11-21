import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { CodigoNotificacion, Notificacion, TIPO_DE_NOTIFICACION } from '../core/models';
import { StoreService } from '../core/store.service';
import { DENSITY, SubnavItem } from '../shared/density';
import { FechaPipe, SHARED, UiService } from '../shared/ui';
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
            @let r = resumen(n);
            <li [class.unread]="!n.leida">
              <span class="ico" aria-hidden="true">{{ iniciales(r.actor) }}<span class="kind"><app-icon [name]="icono(n.tipo)" [size]="11" [stroke]="2" /></span></span>
              <button class="body" type="button" (click)="abrir(n)" [attr.aria-label]="r.actor + ' ' + r.accion + (r.objeto ? ' ' + r.objeto : '') + '. ' + hace(n.fecha)">
                <span class="post"><b class="actor">{{ r.actor }}</b> {{ r.accion }}@if (r.objeto) { <b>{{ r.objeto }}</b>}</span>
                <span class="small muted">{{ n.descripcion }}</span>
                <span class="label"><time [attr.datetime]="n.fecha" [attr.title]="n.fecha | fecha">{{ hace(n.fecha) }}</time> / {{ n.tipo }}</span>
                @if (n.entidad) {<span class="ref">{{ r.enlace }} <app-icon name="arrowRight" [size]="12" /></span>}
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
    .ico { position: relative; display: grid; place-items: center; width: 2.3rem; height: 2.3rem; border-radius: 50%; background: var(--canvas-2); color: var(--green-800); font-weight: 700; font-size: .8rem; letter-spacing: .02em; }
    li.unread .ico { background: var(--green-800); color: var(--cream); }
    .ico .kind { position: absolute; right: -.25rem; bottom: -.2rem; display: grid; place-items: center; width: 1.15rem; height: 1.15rem; border-radius: 50%; background: var(--cream-50); color: var(--green-800); border: 1px solid var(--hair-2); }
    .body { display: grid; gap: .15rem; text-align: left; border: 0; background: none; padding: 0; cursor: pointer; color: inherit; min-width: 0; }
    .post { font-size: .94rem; line-height: 1.35; }
    .post b { font-weight: 700; }
    li:not(.unread) .post b { font-weight: 600; }
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
  readonly tipos: { codigo: CodigoNotificacion; etiqueta: string }[] =
    (Object.entries(TIPO_DE_NOTIFICACION) as [CodigoNotificacion, string][]).map(([codigo, etiqueta]) => ({ codigo, etiqueta }));
  readonly page = signal(0);
  readonly size = signal(6);
  readonly vistas = computed<SubnavItem[]>(() => [
    { id: 'todas', label: 'Todas', count: this.store.notificacionesTodas() ?? undefined },
    { id: 'noleidas', label: 'No leídas', count: this.store.unreadCount() },
  ]);
  readonly lista = computed(() => this.store.notificaciones());
  icono = (t: string) => ICONO[t] ?? 'bell';

  constructor() {
    void this.store.consultarNotificaciones({ unread: false, type: '', page: 0 });
    effect(() => {
      for (const n of this.lista()) {
        const id = n.datos?.['transferId'];
        if (typeof id === 'string' && n.tipo.startsWith('Transferencia')) untracked(() => this.store.requestTransferDetail(id));
      }
    });
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

  otrosLotes(n: Notificacion): string {
    const lotes = n.datos?.['batchIds'];
    return Array.isArray(lotes) && lotes.length > 1 ? ' y ' + (lotes.length - 1) + ' más' : '';
  }

  resumen(n: Notificacion): { actor: string; accion: string; objeto: string; enlace: string } {
    const d = n.datos ?? {};
    const texto = (k: string) => (typeof d[k] === 'string' ? (d[k] as string) : '');
    const solicitud = this.store.solicitudes().find((s) => s.id === texto('requestId'));
    switch (n.tipo) {
      case 'Transferencia entrante':
        return { actor: this.store.nombreEmpresa(texto('fromCompanyId') || undefined), accion: 'envió a tu empresa una transferencia con', objeto: this.lotesDeTransferencia(n), enlace: 'Revisar y decidir la transferencia' };
      case 'Transferencia aceptada':
        return { actor: this.store.nombreEmpresa(texto('toCompanyId') || undefined), accion: 'aceptó tu transferencia con', objeto: this.lotesDeTransferencia(n), enlace: 'Ver la transferencia' };
      case 'Transferencia rechazada':
        return { actor: this.store.nombreEmpresa(texto('toCompanyId') || undefined), accion: 'rechazó tu transferencia con', objeto: this.lotesDeTransferencia(n), enlace: 'Ver la transferencia y el motivo' };
      case 'Solicitud de administrador':
        return {
          actor: solicitud?.nombre || this.store.nombreEmpresa(texto('companyId') || undefined),
          accion: 'solicitó incorporarse como administrador' + (solicitud ? ' de' : ''),
          objeto: solicitud ? this.store.nombreEmpresa(solicitud.empresaId) : '',
          enlace: 'Revisar la solicitud',
        };
      case 'Solicitud revisada':
        return {
          actor: 'El Dueño de Licencia',
          accion: (texto('decision') === 'APPROVED' ? 'aprobó' : texto('decision') === 'REJECTED' ? 'rechazó' : 'revisó') + ' la solicitud de administrador' + (solicitud ? ' de' : ''),
          objeto: solicitud?.nombre ?? '',
          enlace: 'Ver usuarios',
        };
      case 'Posible falla derivada': {
        const lotes = d['batchIds'];
        const varios = Array.isArray(lotes) && lotes.length > 1;
        return {
          actor: 'Trazabilidad',
          accion: varios
            ? 'detectó que una falla confirmada en la genealogía podría afectar a los lotes'
            : 'detectó que una falla confirmada en la genealogía podría afectar al lote',
          objeto: n.entidad?.tipo === 'lote' ? this.store.nombreLote(n.entidad.id) + this.otrosLotes(n) : '',
          enlace: 'Revisar el lote',
        };
      }
      default:
        return { actor: 'TrazaTex', accion: n.titulo, objeto: '', enlace: 'Ver detalle' };
    }
  }

  private lotesDeTransferencia(n: Notificacion): string {
    const id = typeof n.datos?.['transferId'] === 'string' ? (n.datos['transferId'] as string) : n.entidad?.id;
    const ids = this.store.transferencia(id)?.lotesIds
      ?? (Array.isArray(n.datos?.['batchIds']) ? (n.datos['batchIds'] as string[]).map((b) => this.store.codigoLote(b)) : []);
    if (!ids.length) return 'lotes';
    const nombres = ids.slice(0, 2).map((code) => this.store.nombreEnTransferencia(code));
    return (ids.length === 1 ? '1 lote: ' : ids.length + ' lotes: ') + nombres.join(', ') + (ids.length > 2 ? ' y ' + (ids.length - 2) + ' más' : '');
  }

  iniciales(actor: string): string {
    const palabras = actor.replace(/^(El|La)\s+/i, '').split(/\s+/).filter((p) => /^[\p{L}\p{N}]/u.test(p));
    return (palabras.length > 1 ? palabras[0][0] + palabras[1][0] : (palabras[0] ?? 'T').slice(0, 2)).toUpperCase();
  }

  hace(fecha: string): string {
    const ms = Date.now() - Date.parse(fecha);
    if (!Number.isFinite(ms)) return '—';
    const min = Math.floor(ms / 60000);
    if (min < 1) return 'hace un momento';
    if (min < 60) return 'hace ' + min + ' min';
    const h = Math.floor(min / 60);
    if (h < 24) return 'hace ' + h + ' h';
    const dias = Math.floor(h / 24);
    if (dias < 7) return dias === 1 ? 'ayer' : 'hace ' + dias + ' días';
    return this.fecha.transform(fecha, false);
  }
  private readonly fecha = new FechaPipe();

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
