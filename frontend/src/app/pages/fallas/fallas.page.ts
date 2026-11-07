import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { DENSITY, SubnavItem, paginar } from '../../shared/density';
import { SHARED } from '../../shared/ui';

export const tonoFalla = (e: string) => (e === 'Resuelta' ? 'ok' : e === 'En reevaluación' ? 'warn' : 'fail');

type Vista = 'pendientes' | 'confirmadas' | 'resueltas' | 'derivadas';

@Component({
  selector: 'app-fallas',
  imports: [...SHARED, ...DENSITY, FormsModule],
  styles: '.embedded-failures { padding: 0; margin-top: 1rem; }',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page" [class.embedded-failures]="embedded()">
      @if (!embedded()) {
      <header class="page-head">
        <div>
          <div class="eyebrow">{{ session.esDueno() ? 'Cadena licenciada' : store.nombreEmpresa(session.empresaId()) }}</div>
          <h2 class="page-title">Fallas</h2>
          <p class="page-lead">Generadas por controles con pruebas Con falla. El Administrador decide reevaluar o descartar.</p>
        </div>
        @if (!session.esAdmin()) {
          <app-readonly />
        }
      </header>
      }

      <app-subnav panel="fallas" [items]="vistas()" [(active)]="vista" (activeChange)="page.set(0)" label="Vistas de fallas" />
      <div appTabPanel="fallas" [tabActive]="vista()">

      @if (vista() !== 'derivadas') {
        <div class="filters">
          <label class="search">
            <app-icon name="search" [size]="16" />
            <input class="input" placeholder="Buscar falla, lote o control" [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" aria-label="Buscar fallas" />
          </label>
          @if (session.esDueno()) {
            <select class="select" [ngModel]="fEmpresa()" (ngModelChange)="fEmpresa.set($event); page.set(0)" aria-label="Empresa">
              <option value="">Todas las empresas</option>
              @for (e of store.empresas(); track e.id) {
                <option [value]="e.id">{{ e.razonSocial }}</option>
              }
            </select>
          }
        </div>
        @if (lista().length) {
          <div class="rows">
            @for (f of pagina(); track f.id) {
              @let e = store.estadoFalla(f);
              <button type="button" class="row-card row-card--{{ e === 'Confirmada' ? 'hist' : tonoFalla(e) }}" [class.is-sel]="selId() === f.id" (click)="selId.set(f.id)">
                <span class="rc-id">{{ f.id }}</span>
                <span class="rc-state">{{ e }}</span>
                <span class="rc-title">@if (store.puedeVer('causaFalla', f.empresaId)) {{{ f.causa || 'Causa por registrar' }}} @else {Causa privada}</span>
                <span class="rc-meta">Lote {{ f.loteId }} / {{ f.controlId }} / <span class="num">{{ f.fecha | fecha: false }}</span></span>
              </button>
            }
          </div>
          <app-pager [total]="lista().length" [(page)]="page" [(size)]="size" />
        } @else if (store.loading()) {
          <app-skeleton />
        } @else {
          <div class="empty"><strong>Sin fallas en esta vista</strong><span>Nada pendiente aquí.</span></div>
        }
      } @else {
        @if (derivados().length) {
          <div class="rows">
            @for (l of paginaDer(); track l.id) {
              <button type="button" class="row-card row-card--warn" (click)="router.navigate(['/trazabilidad'], { queryParams: { lote: l.id } })">
                <span class="rc-id">{{ l.id }}</span>
                <span class="rc-state"><app-icon name="lock" [size]="11" [stroke]="2" /> Bloqueo preventivo</span>
                <span class="rc-title">{{ l.material }}</span>
                <span class="rc-meta">{{ store.nombreEmpresa(l.empresaId) }} / ver impacto en genealogía</span>
              </button>
            }
          </div>
          <app-pager [total]="derivados().length" [(page)]="page" [(size)]="size" />
        } @else if (store.loading()) {
          <app-skeleton />
        } @else {
          <div class="empty"><strong>Sin posibles fallas derivadas</strong><span>Ningún lote disponible está en bloqueo preventivo.</span></div>
        }
      }
      </div>
    </div>

    @if (sel(); as f) {
      @let e = store.estadoFalla(f);
      <app-drawer [title]="f.id" eyebrow="Falla" [sub]="'Lote ' + f.loteId + ' / ' + store.nombreEmpresa(f.empresaId)" (closed)="selId.set(null)">
        <app-badge [text]="e" [tone]="tonoFalla(e)" />
        <section class="d-section">
          <h3>Registro</h3>
          <dl class="dl">
            <dt>Causa</dt><dd>@if (store.puedeVer('causaFalla', f.empresaId)) {{{ f.causa || 'Por registrar' }}} @else {<app-priv />}</dd>
            <dt>Cantidad afectada</dt><dd>@if (store.puedeVer('cantidad', f.empresaId)) {<span class="num">{{ f.cantidadAfectadaKg | kg }}</span>} @else {<app-priv />}</dd>
            <dt>Control</dt><dd><a class="id" [routerLink]="['/calidad', f.controlId]">{{ f.controlId }}</a></dd>
            <dt>Generada</dt><dd class="num">{{ f.fecha | fecha }}</dd>
          </dl>
        </section>
        @if (e === 'Pendiente de decisión' && session.esAdmin()) {
          <div class="notice notice--fail"><app-icon name="failure" /><span>Esta falla espera tu decisión: reevaluar o descartar el lote.</span></div>
        }
        <ng-container footer>
          <a class="btn btn--ghost btn--sm" [routerLink]="['/trazabilidad']" [queryParams]="{ lote: f.loteId }"><app-icon name="trace" [size]="14" /> Impacto</a>
          <a class="btn btn--primary btn--sm" [routerLink]="['/fallas', f.id]">{{ e === 'Pendiente de decisión' && session.esAdmin() ? 'Decidir' : 'Abrir falla' }} <app-icon name="arrowRight" [size]="14" /></a>
        </ng-container>
      </app-drawer>
    }
  `,
})
export class FallasPage {
  readonly embedded = input(false);
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  readonly router = inject(Router);
  readonly tonoFalla = tonoFalla;
  readonly vista = signal<Vista>('pendientes');
  readonly q = signal('');
  readonly fEmpresa = signal('');
  readonly page = signal(0);
  readonly size = signal(6);
  readonly selId = signal<string | null>(null);
  readonly sel = computed(() => this.store.falla(this.selId()) ?? null);

  private base = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.store
      .misFallas()
      .filter((f) => (!q || [f.id, f.loteId, f.controlId].some((x) => x.toLowerCase().includes(q))) && (!this.fEmpresa() || f.empresaId === this.fEmpresa()))
      .sort((a, b) => b.fecha.localeCompare(a.fecha));
  });
  private en(v: Vista, estado: string) {
    if (v === 'pendientes') return estado === 'Pendiente de decisión' || estado === 'En reevaluación';
    if (v === 'confirmadas') return estado === 'Confirmada';
    return estado === 'Resuelta';
  }
  readonly derivados = computed(() => this.store.misLotes().filter((l) => l.calidad === 'Posible falla derivada'));
  readonly vistas = computed<SubnavItem[]>(() => {
    const n = (v: Vista) => this.base().filter((f) => this.en(v, this.store.estadoFalla(f))).length;
    return [
      { id: 'pendientes', label: 'Pendientes', count: n('pendientes') },
      { id: 'confirmadas', label: 'Confirmadas', count: n('confirmadas') },
      { id: 'resueltas', label: 'Resueltas', count: n('resueltas') },
      { id: 'derivadas', label: 'Posibles derivadas', count: this.derivados().length },
    ];
  });
  readonly lista = computed(() => this.base().filter((f) => this.en(this.vista(), this.store.estadoFalla(f))));
  readonly pagina = computed(() => paginar(this.lista(), this.page(), this.size()));
  readonly paginaDer = computed(() => paginar(this.derivados(), this.page(), this.size()));
}
