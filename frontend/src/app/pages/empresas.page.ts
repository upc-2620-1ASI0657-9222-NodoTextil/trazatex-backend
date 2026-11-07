import { formDraft } from '../shared/form-draft.directive';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Empresa } from '../core/models';
import { StoreService } from '../core/store.service';
import { DENSITY, SubnavItem, paginar } from '../shared/density';
import { SHARED, UiService } from '../shared/ui';

@Component({
  selector: 'app-empresas',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--empresas">
      <ng-template appHeaderActions>
          <app-action cls="btn--primary" icon="plus" [tipRight]="true" [reason]="lleno() ? 'Se alcanzó el límite de empresas de la licencia.' : null" (run)="abrir()">Registrar empresa</app-action>
        </ng-template>

      <app-subnav panel="empresas" [items]="vistas()" [(active)]="vista" (activeChange)="page.set(0)" label="Estados de empresa" />
      <div appTabPanel="empresas" [tabActive]="vista()">

      <div class="filters">
        <label class="search"><app-icon name="search" [size]="16" /><input class="input" placeholder="Buscar razón social o RUC" [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" aria-label="Buscar empresas" /></label>
      </div>

      @if (lista().length) {
        <div class="rows">
          @for (e of pagina(); track e.id) {
            @let n = store.userSlotCount(e.id);
            <button type="button" class="row-card {{ e.estado === 'Activa' ? 'row-card--ok' : 'row-card--hist' }}" [class.is-dim]="e.estado === 'Inactiva'" [class.is-sel]="selId() === e.id" (click)="selId.set(e.id)">
              <span class="rc-id">RUC {{ e.ruc }}</span>
              <app-icon class="rc-figure" name="company" [size]="42" />
              <span class="rc-state">{{ e.estado }}</span>
              <span class="rc-title">{{ e.razonSocial }}</span>
              <span class="rc-meta">{{ e.actividades.join(' / ') }} / <span class="num">{{ n }}/{{ store.licencia().limiteUsuariosPorEmpresa }}</span> usuarios</span>
            </button>
          }
        </div>
        <app-pager [total]="lista().length" [(page)]="page" [(size)]="size" />
      } @else if (store.loading()) {
        <app-skeleton />
      } @else {
        <div class="empty"><strong>Sin empresas en esta vista</strong></div>
      }
      </div>
    </div>

    @if (sel(); as e) {
      @let n = store.userSlotCount(e.id);
      @let lim = store.licencia().limiteUsuariosPorEmpresa;
      <app-drawer [title]="e.razonSocial" [eyebrow]="'RUC ' + e.ruc" [sub]="e.actividades.join(' / ')" (closed)="selId.set(null)">
        <app-badge [text]="e.estado" [tone]="e.estado === 'Activa' ? 'ok' : 'hist'" />
        <section class="d-section">
          <h3>Capacidad de usuarios</h3>
          <div class="meter"><i [style.width.%]="(n / lim) * 100" [class.warn]="n / lim >= 0.8"></i></div>
          <p class="small"><b class="num">{{ n }}</b> de <b class="num">{{ lim }}</b> usuarios registrados</p>
        </section>
        <div footer class="d-actions">
          @if (e.estado === 'Activa') {
            <button class="btn btn--danger btn--sm" type="button" (click)="confirmar.set(e)"><app-icon name="power" [size]="14" /> Inactivar</button>
          } @else {
            <button class="btn btn--primary btn--sm" type="button" (click)="cambiar(e, 'Activa')"><app-icon name="power" [size]="14" /> Activar</button>
          }
        </div>
      </app-drawer>
    }

    @if (nueva()) {
      <app-dialog [appDraft]="draft" title="Registrar empresa" eyebrow="Licencia perpetua" (closed)="nueva.set(false)">
        <div class="form-grid">
          <label class="field span-all"><span>Razón social</span><input class="input" [(ngModel)]="f.razonSocial" /></label>
          <label class="field"><span>RUC</span><input class="input num" inputmode="numeric" maxlength="11" [(ngModel)]="f.ruc" placeholder="11 dígitos" /></label>
          <label class="field"><span>Correo del primer administrador</span><input class="input" type="email" [(ngModel)]="f.correo" /></label>
          <div class="field span-all">
            <span>Actividades textiles</span>
            <div class="acts">
              @for (a of actividades; track a) {
                <label class="check small"><input type="checkbox" [checked]="f.actividades.includes(a)" (change)="toggleAct(a)" /> {{ a }}</label>
              }
            </div>
          </div>
        </div>
        @if (error()) {<p class="err" role="alert" style="margin-top:.5rem">{{ error() }}</p>}
        <ng-container footer>
          <button class="btn btn--ghost" type="button" (click)="nueva.set(false)">Cancelar</button>
          <button class="btn btn--primary" type="button" (click)="guardar()">Registrar empresa</button>
        </ng-container>
      </app-dialog>
    }
    @if (confirmar(); as e) {
      <app-dialog [title]="'Inactivar ' + e.razonSocial" eyebrow="Empresa" (closed)="confirmar.set(null)">
        <p class="small">Sus usuarios no podrán operar mientras esté inactiva. La información histórica se conserva para trazabilidad.</p>
        <ng-container footer>
          <button class="btn btn--ghost" type="button" (click)="confirmar.set(null)">Cancelar</button>
          <button class="btn btn--danger" type="button" (click)="cambiar(e, 'Inactiva'); confirmar.set(null)">Inactivar</button>
        </ng-container>
      </app-dialog>
    }
  `,
  styles: `.acts { display: flex; flex-wrap: wrap; gap: .5rem 1.1rem; }`,
})
export class EmpresasPage {
  readonly store = inject(StoreService);
  private readonly ui = inject(UiService);
  readonly vista = signal('todas');
  readonly q = signal('');
  readonly page = signal(0);
  readonly size = signal(6);
  readonly nueva = signal(false);
  readonly selId = signal<string | null>(null);
  readonly sel = computed(() => this.store.empresa(this.selId()) ?? null);
  readonly confirmar = signal<Empresa | null>(null);
  readonly error = signal('');
  readonly actividades = ['Hilandería', 'Tejeduría', 'Tintorería', 'Acabados', 'Corte', 'Confección', 'Marca'];
  f = { razonSocial: '', ruc: '', correo: '', actividades: [] as string[] };

  readonly lleno = computed(() => this.store.empresas().length >= this.store.licencia().limiteEmpresas);
  private base = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.store.empresas().filter((e) => !q || e.razonSocial.toLowerCase().includes(q) || e.ruc.includes(q));
  });
  readonly vistas = computed<SubnavItem[]>(() => [
    { id: 'todas', label: 'Todas', count: this.base().length },
    { id: 'Activa', label: 'Activas', count: this.base().filter((e) => e.estado === 'Activa').length },
    { id: 'Inactiva', label: 'Inactivas', count: this.base().filter((e) => e.estado === 'Inactiva').length },
  ]);
  readonly lista = computed(() => this.base().filter((e) => this.vista() === 'todas' || e.estado === this.vista()));
  readonly pagina = computed(() => paginar(this.lista(), this.page(), this.size()));

  abrir() {
    this.f = { razonSocial: '', ruc: '', correo: '', actividades: [] };
    this.error.set('');
    this.nueva.set(true);
  }
  toggleAct(a: string) {
    this.f.actividades = this.f.actividades.includes(a) ? this.f.actividades.filter((x) => x !== a) : [...this.f.actividades, a];
  }
  readonly draft = formDraft('company/new', () => ({ ...this.f }), value => { this.f = value; });
  async guardar() {
    const r = await this.store.crearEmpresa({ razonSocial: this.f.razonSocial, ruc: this.f.ruc, actividades: this.f.actividades, correoPrimerAdmin: this.f.correo });
    if (!r.ok) return this.error.set(r.error);
    await this.draft.clear();
    this.nueva.set(false);
    this.ui.toast(`${this.f.razonSocial} registrada. La invitación quedó en la cola de correo.`);
  }
  async cambiar(e: Empresa, estado: Empresa['estado']) {
    if (estado === 'Activa' && !(await this.ui.confirm('¿Activar esta empresa para habilitar sus operaciones?'))) return;
    const result = await this.store.setEstadoEmpresa(e.id, estado);
    if (!result.ok) return;
    this.ui.toast(`${e.razonSocial} ${estado === 'Activa' ? 'activada' : 'inactivada'}`);
  }
}


