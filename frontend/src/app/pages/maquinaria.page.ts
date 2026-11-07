import { formDraft } from '../shared/form-draft.directive';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EstadoMaquinaria, Maquinaria } from '../core/models';
import { SessionService } from '../core/session.service';
import { StoreService } from '../core/store.service';
import { DENSITY, SubnavItem, paginar } from '../shared/density';
import { SHARED, UiService } from '../shared/ui';

@Component({
  selector: 'app-maquinaria',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--maquinaria">
      <ng-template appHeaderActions>
          @if (session.esAdmin()) {
            <button class="btn btn--primary" type="button" (click)="editar()"><app-icon name="plus" [size]="16" /> Registrar maquinaria</button>
          } @else {
            <app-readonly />
          }
        </ng-template>

      <app-subnav panel="maquinaria" [items]="vistas()" [(active)]="vista" (activeChange)="page.set(0)" label="Estados de maquinaria" />
      <div appTabPanel="maquinaria" [tabActive]="vista()">

      <div class="filters">
        <label class="search">
          <app-icon name="search" [size]="16" />
          <input class="input" placeholder="Buscar código, nombre o tipo" [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" aria-label="Buscar maquinaria" />
        </label>
      </div>

      @if (lista().length) {
        <div class="rows">
          @for (m of pagina(); track m.codigo) {
            <button type="button" class="row-card {{ m.estado === 'Activa' ? 'row-card--ok' : m.estado === 'Fuera de servicio' ? 'row-card--warn' : 'row-card--hist' }}" [class.is-sel]="selCod() === m.codigo" (click)="selCod.set(m.codigo)">
              <span class="rc-id">{{ m.codigo }}</span>
              <app-icon class="rc-figure" name="machine" [size]="44" />
              <span class="rc-state">{{ m.estado }}</span>
              <span class="rc-title">{{ m.nombre }}</span>
              <span class="rc-meta">{{ m.tipo }} / {{ usos(m.codigo) }} proceso(s)</span>
            </button>
          }
        </div>
        <app-pager [total]="lista().length" [(page)]="page" [(size)]="size" />
      } @else if (store.loading()) {
        <app-skeleton />
      } @else {
        <div class="empty"><strong>Sin maquinaria en este estado</strong></div>
      }
      </div>
    </div>

    @if (sel(); as m) {
      <app-drawer [title]="m.nombre" [eyebrow]="m.codigo" [sub]="m.tipo" (closed)="selCod.set(null)">
        <app-badge [text]="m.estado" [tone]="m.estado === 'Activa' ? 'ok' : m.estado === 'Fuera de servicio' ? 'warn' : 'hist'" />
        <section class="d-section">
          <h3>Ficha</h3>
          <dl class="dl">
            <dt>Código interno</dt><dd class="id">{{ m.codigo }}</dd>
            <dt>Nombre</dt><dd>{{ m.nombre }}</dd>
            <dt>Tipo</dt><dd>{{ m.tipo }}</dd>
            <dt>Procesos</dt><dd class="num">{{ usos(m.codigo) }}</dd>
          </dl>
        </section>
        @if (session.esAdmin()) {
          <section class="d-section">
            <h3>Estado operativo</h3>
            <div class="seg" role="group" aria-label="Estado">
              @for (e of estados; track e) {
                <button type="button" [attr.aria-pressed]="m.estado === e" (click)="cambiarEstado(m, e)">{{ e }}</button>
              }
            </div>
          </section>
        }
      </app-drawer>
    }

    @if (form(); as f) {
      <app-dialog [appDraft]="draft" title="Registrar maquinaria" eyebrow="Maquinaria" (closed)="form.set(null)">
        <div class="form-grid">
          <label class="field"><span>Código interno</span><input class="input" [(ngModel)]="f.codigo" placeholder="MQ-001" /></label>
          <label class="field"><span>Nombre</span><input class="input" [(ngModel)]="f.nombre" /></label>
          <label class="field"><span>Tipo</span><input class="input" list="tipos-maquina" [(ngModel)]="f.tipo" placeholder="Selecciona o escribe" />
            <datalist id="tipos-maquina">
              @for (tipo of tiposConocidos(); track tipo) { <option [value]="tipo"></option> }
            </datalist>
          </label>
        </div>
        @if (error()) {<p class="err" role="alert" style="margin-top:.6rem">{{ error() }}</p>}
        <ng-container footer>
          <button class="btn btn--ghost" type="button" (click)="form.set(null)">Cancelar</button>
          <button class="btn btn--primary" type="button" (click)="guardar(f)">Guardar</button>
        </ng-container>
      </app-dialog>
    }
  `,
})
export class MaquinariaPage {
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  private readonly ui = inject(UiService);
  readonly tiposConocidos = computed(() => [...new Set([
    ...this.store.maquinarias().map((m) => m.tipo), 'Hiladora', 'Tejedora', 'Tinturadora', 'Cortadora', 'Máquina de coser',
  ])].sort());
  readonly estados: EstadoMaquinaria[] = ['Activa', 'Fuera de servicio', 'Inactiva'];
  readonly vista = signal<string>('Activa');
  readonly q = signal('');
  readonly page = signal(0);
  readonly size = signal(6);
  readonly selCod = signal<string | null>(null);
  readonly sel = computed(() => (this.selCod() ? this.store.maquina(this.selCod()!) : undefined) ?? null);
  readonly form = signal<Maquinaria | null>(null);
  readonly error = signal('');
  original = '';

  private base = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.store
      .maquinarias()
      .filter((m) => m.empresaId === this.session.empresaId() && (!q || [m.codigo, m.nombre, m.tipo].some((x) => x.toLowerCase().includes(q))));
  });
  readonly vistas = computed<SubnavItem[]>(() => [
    ...this.estados.map((e) => ({ id: e, label: e === 'Activa' ? 'Activas' : e === 'Inactiva' ? 'Inactivas' : e, count: this.base().filter((m) => m.estado === e).length })),
    { id: 'todas', label: 'Todas', count: this.base().length },
  ]);
  readonly lista = computed(() => this.base().filter((m) => this.vista() === 'todas' || m.estado === this.vista()));
  readonly pagina = computed(() => paginar(this.lista(), this.page(), this.size()));
  usos = (c: string) => this.store.procesos().filter((p) => p.maquinariaId === c).length;

  editar(m?: Maquinaria) {
    this.original = m?.codigo ?? '';
    this.error.set('');
    const usados = new Set(this.store.maquinarias().map((x) => x.codigo));
    let numero = this.store.maquinarias().length + 1;
    let codigo = `MQ-${String(numero).padStart(3, '0')}`;
    while (usados.has(codigo)) codigo = `MQ-${String(++numero).padStart(3, '0')}`;
    this.form.set({ codigo, nombre: '', tipo: '', estado: 'Activa', empresaId: this.session.empresaId()! });
  }
  readonly draft = formDraft('machine/new', () => this.form()!, value => { this.form.set(value); });
  async guardar(f: Maquinaria) {
    const r = await this.store.guardarMaquinaria(f, this.original || undefined);
    if (!r.ok) return this.error.set(r.error);
    await this.draft.clear();
    this.ui.toast(`Maquinaria ${f.codigo} guardada`);
    if (this.selCod() === this.original) this.selCod.set(f.codigo);
    this.form.set(null);
  }
  async cambiarEstado(m: Maquinaria, e: EstadoMaquinaria) {
    const result = await this.store.setEstadoMaquinaria(m.codigo, e);
    if (!result.ok) return;
    this.ui.toast(`${m.codigo} / ${e}`);
  }
}

