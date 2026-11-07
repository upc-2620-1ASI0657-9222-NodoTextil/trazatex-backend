import { formDraft } from '../../shared/form-draft.directive';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  Componente,
  MATERIALES,
  Proceso,
  TIPOS_TRANSFORMACION,
  TipoMaterial,
} from '../../core/models';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { DENSITY } from '../../shared/density';
import { SHARED, UiService } from '../../shared/ui';

const PICK_STYLES = `
  .pick { list-style: none; margin: 0; padding: 0; max-height: 17rem; overflow-y: auto; border-top: 1px solid var(--hair-2); }
  .pick li { border-bottom: 1px dashed var(--hair); }
  .pick label { display: flex; gap: .55rem; align-items: center; padding: .55rem .25rem; width: 100%; }
  .pick .num { margin-left: auto; }
  .pick li.off { opacity: .55; }
`;

@Component({
  selector: 'app-nuevo-proceso',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog [appDraft]="draft"
      title="Iniciar transformación"
      eyebrow="Proceso"
      [wide]="true"
      (closed)="closed.emit()"
    >
      <app-stepper [steps]="pasos" [current]="paso()" />
      <div class="step-body">
        @switch (paso()) {
          @case (0) {
            <p class="small muted">
              Selecciona lotes <b>completos</b>. Si solo necesitas una parte, divide el lote
              primero.
            </p>
            <ul class="pick">
              @for (l of candidatos(); track l.id) {
                @let d = store.disponibilidad(l);
                <li [class.off]="d.bloqueado">
                  <label class="check" [attr.data-tip]="d.bloqueado ? d.motivo : ''">
                    <input
                      type="checkbox"
                      [disabled]="d.bloqueado"
                      [checked]="sel().includes(l.id)"
                      (change)="toggle(l.id)"
                    />
                    <b class="id">{{ l.id }}</b>
                    <span class="small">{{ l.material }}</span>
                    <span class="num muted">{{ l.cantidadKg | kg }}</span>
                    @if (d.bloqueado) {
                      <app-icon name="lock" [size]="12" />
                    }
                  </label>
                </li>
              }
            </ul>
            @if (sel().length > 1) {
              <div class="notice">
                <app-icon name="layers" /><span
                  >Más de un lote de entrada: se registra automáticamente como <b>mezcla</b>.</span
                >
              </div>
            }
          }
          @case (1) {
            <div class="form-grid">
              <label class="field">
                <span>Tipo de transformación</span>
                <select class="select" [(ngModel)]="tipo">
                  <option value="" disabled>Selecciona…</option>
                  @for (t of tipos; track t) {
                    <option [value]="t">{{ t }}</option>
                  }
                </select>
              </label>
              <label class="field">
                <span>Maquinaria activa</span>
                <select class="select" [(ngModel)]="maquina">
                  <option value="" disabled>Selecciona…</option>
                  @for (m of maquinas(); track m.codigo) {
                    <option [value]="m.codigo" [disabled]="m.estado !== 'Activa'">
                      {{ m.codigo }} / {{ m.nombre
                      }}{{ m.estado !== 'Activa' ? ' — ' + m.estado : '' }}
                    </option>
                  }
                </select>
                <span class="hint">Solo la maquinaria Activa puede seleccionarse.</span>
              </label>
            </div>
          }
          @case (2) {
            <div class="review">
              <span class="label">Revisión</span>
              <dl class="dl">
                <dt>Entradas</dt>
                <dd>
                  <div class="chips">
                    @for (s of sel(); track s) {
                      <span class="chip-id">{{ s }}</span>
                    }
                  </div>
                </dd>
                <dt>Entrada total</dt>
                <dd class="num">{{ entrada() | kg }}</dd>
                <dt>Tipo</dt>
                <dd>{{ tipo }}{{ sel().length > 1 ? ' / mezcla' : '' }}</dd>
                <dt>Maquinaria</dt>
                <dd>{{ maquina }} / {{ store.maquina(maquina)?.nombre }}</dd>
                <dt>Inicio</dt>
                <dd>Automático al confirmar</dd>
              </dl>
            </div>
          }
        }
      </div>
      @if (error()) {
        <p class="err" role="alert" style="margin-top:.75rem">{{ error() }}</p>
      }
      <ng-container footer>
        @if (paso() > 0) {
          <button
            class="btn btn--ghost"
            type="button"
            (click)="paso.set(paso() - 1); error.set('')"
          >
            <app-icon name="arrowLeft" [size]="14" /> Atrás
          </button>
        } @else {
          <button class="btn btn--ghost" type="button" (click)="closed.emit()">Cancelar</button>
        }
        @if (paso() < 2) {
          <button class="btn btn--primary" type="button" (click)="siguiente()">
            Continuar <app-icon name="arrowRight" [size]="14" />
          </button>
        } @else {
          <button class="btn btn--primary" type="button" (click)="iniciar()">
            <app-icon name="process" [size]="16" /> Iniciar transformación
          </button>
        }
      </ng-container>
    </app-dialog>
  `,
  styles: PICK_STYLES,
})
export class NuevoProcesoDialog {
  readonly store = inject(StoreService);
  private readonly session = inject(SessionService);
  private readonly ui = inject(UiService);
  private readonly router = inject(Router);
  readonly preselect = input<string>();
  readonly closed = output<void>();
  readonly tipos = TIPOS_TRANSFORMACION;
  readonly pasos = ['Lotes de entrada', 'Transformación y maquinaria', 'Revisar y confirmar'];
  readonly paso = signal(0);
  tipo = '';
  maquina = '';
  readonly sel = signal<string[]>([]);
  readonly error = signal('');

  readonly candidatos = computed(() =>
    this.store
      .lotes()
      .filter((l) => l.empresaId === this.session.empresaId() && !this.store.esHistorico(l))
      .sort(
        (a, b) =>
          Number(this.store.disponibilidad(a).bloqueado) -
            Number(this.store.disponibilidad(b).bloqueado) || a.id.localeCompare(b.id),
      ),
  );
  readonly maquinas = computed(() =>
    this.store.maquinarias().filter((m) => m.empresaId === this.session.empresaId()),
  );
  readonly entrada = computed(() =>
    this.sel().reduce((s, id) => s + (this.store.lote(id)?.cantidadKg ?? 0), 0),
  );

  ngOnInit() {
    const p = this.preselect();
    if (p && this.store.elegible(this.store.lote(p)!)) this.sel.set([p]);
  }
  toggle(id: string) {
    this.sel.update((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  }
  siguiente() {
    let e = '';
    if (this.paso() === 0 && !this.sel().length) e = 'Selecciona al menos un lote de entrada.';
    if (this.paso() === 1 && !this.tipo) e = 'Indica el tipo de transformación.';
    if (this.paso() === 1 && !e && this.store.maquina(this.maquina)?.estado !== 'Activa')
      e = 'Selecciona maquinaria Activa.';
    this.error.set(e);
    if (!e) this.paso.update((p) => p + 1);
  }
  readonly draft = formDraft('transformation/new', () => ({ sel: this.sel(), tipo: this.tipo, maquina: this.maquina, paso: this.paso() }), value => {
    this.sel.set(value.sel); this.tipo = value.tipo; this.maquina = value.maquina; this.paso.set(value.paso);
  });
  async iniciar() {
    const r = await this.store.iniciarProceso({
      entradas: this.sel(),
      tipo: this.tipo,
      maquinariaId: this.maquina,
    });
    if (!r.ok) return this.error.set(r.error);
    await this.draft.clear();
    this.ui.toast(`Transformación ${r.id} iniciada`);
    this.closed.emit();
    this.router.navigate(['/procesos', r.id]);
  }
}

interface Salida {
  material: TipoMaterial;
  cantidadKg: number | null;
  nombre: string;
  productoFinal: boolean;
  distribuidor: string;
  precioVenta: number | null;
  moneda: 'PEN' | 'USD';
  fechaSalida: string;
  referencia: string;
}

@Component({
  selector: 'app-finalizar-proceso',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog [appDraft]="draft"
      title="Finalizar transformación"
      [eyebrow]="proceso().tipo"
      [wide]="true"
      (closed)="closed.emit()"
    >
      <app-stepper [steps]="pasos" [current]="paso()" />
      <div class="step-body">
        @switch (paso()) {
          @case (0) {
            <div class="review input-summary">
              <span class="label">Entradas utilizadas</span>
              @for (input of proceso().entradas; track input.loteId) { <p class="small"><b>{{ store.nombreLote(input.loteId) }}</b> · {{ input.cantidadKg | kg }}</p> }
            </div>
            <div class="stack" style="gap:.5rem">
              @for (s of salidas(); track $index; let i = $index) {
                <div class="out">
                  <label class="field"><span>Tipo de material</span><select
                    class="select"
                    [ngModel]="s.material"
                    (ngModelChange)="patch(i, { material: $event })"
                    [attr.aria-label]="'Material salida ' + (i + 1)"
                  >
                    @for (m of materiales(); track m) {
                      <option [value]="m">{{ m }}</option>
                    }
                  </select></label>
                  <label class="field"><span>Peso (kg)</span><input
                    class="input num"
                    type="number"
                    min="0"
                    step="0.1"
                    placeholder="kg"
                    [ngModel]="s.cantidadKg"
                    (ngModelChange)="patch(i, { cantidadKg: $event })"
                    [attr.aria-label]="'Cantidad salida ' + (i + 1)" data-correction-field
                  /></label>
                  <label class="field"><span>Nombre del lote</span><input
                    class="input" maxlength="20"
                    placeholder="p. ej. Hilo Pima peinado"
                    aria-label="Nombre del lote de salida"
                    title="Escribe un nombre descriptivo. TrazaTex genera el código y el QR automáticamente."
                    [ngModel]="s.nombre"
                    (ngModelChange)="patch(i, { nombre: $event })"
                  /></label>
                  <button
                    class="x"
                    type="button"
                    [disabled]="salidas().length === 1"
                    (click)="quitar(i)"
                    aria-label="Eliminar este lote de salida completo" title="Eliminar este lote de salida completo"
                  >
                    <app-icon name="x" [size]="16" />
                  </button>
                </div>
                <label class="check small"
                  ><input
                    type="checkbox"
                    [ngModel]="s.productoFinal"
                    (ngModelChange)="patch(i, { productoFinal: $event })"
                  />
                  Es producto final</label
                >
                @if (s.productoFinal) {
                  <div class="form-grid">
                    <label class="field"
                      ><span>Distribuidor</span
                      ><input
                        class="input"
                        [ngModel]="s.distribuidor"
                        (ngModelChange)="patch(i, { distribuidor: $event })"
                    /></label>
                    <label class="field"
                      ><span>Precio de venta</span
                      ><input
                        class="input num"
                        type="number"
                        min="0"
                        [ngModel]="s.precioVenta"
                        (ngModelChange)="
                          patch(i, {
                            precioVenta: $event === '' || $event === null ? null : +$event,
                          })
                        "
                    /></label>
                    <label class="field"
                      ><span>Moneda</span
                      ><select
                        class="select"
                        [ngModel]="s.moneda"
                        (ngModelChange)="patch(i, { moneda: $event })"
                      >
                        <option value="PEN">PEN</option>
                        <option value="USD">USD</option>
                      </select></label
                    >
                    <label class="field"
                      ><span>Fecha de salida</span
                      ><input
                        class="input"
                        type="date"
                        [ngModel]="s.fechaSalida"
                        (ngModelChange)="patch(i, { fechaSalida: $event })"
                    /></label>
                    <label class="field span-all"
                      ><span>Referencia comercial</span
                      ><input
                        class="input"
                        [ngModel]="s.referencia"
                        (ngModelChange)="patch(i, { referencia: $event })"
                    /></label>
                  </div>
                }
              }
              <button
                class="btn btn--bare btn--sm"
                type="button"
                style="justify-self:start"
                (click)="agregar()"
              >
                <app-icon name="plus" [size]="14" /> Agregar lote de salida
              </button>
            </div>
          }
          @case (1) {
            <div class="field">
              <span>Composición resultante</span>
              <app-composition [comp]="comp()" />
              <span class="hint"
                >Se calcula con la composición y cantidad de los lotes de entrada.</span
              >
            </div>
          }
          @case (2) {
            <div class="form-grid">
              <label class="field"
                ><span>Desperdicio (kg) <small class="muted">si aplica</small></span
                ><input
                  class="input num"
                  type="number"
                  min="0"
                  step="0.1"
                  [ngModel]="desperdicio()"
                  (ngModelChange)="desperdicio.set(+$event || 0)"
              /></label>
              <label class="field"
                ><span>Motivo del desperdicio</span
                ><input
                  class="input"
                  [(ngModel)]="motivo"
                  [disabled]="!desperdicio()"
                  placeholder="p. ej. retazos de corte"
              /></label>
            </div>
            <div class="balance" [class.bad]="merma() < 0">
              <div>
                <span class="label">Entrada</span><b class="num">{{ entrada() | kg }}</b>
              </div>
              <span class="op">=</span>
              <div>
                <span class="label">Salida</span><b class="num">{{ salidaTotal() | kg }}</b>
              </div>
              <span class="op">+</span>
              <div>
                <span class="label">Merma</span><b class="num">{{ merma() | kg }}</b>
              </div>
              <span class="op">+</span>
              <div>
                <span class="label">Desperdicio</span><b class="num">{{ desperdicio() | kg }}</b>
              </div>
            </div>
            @if (merma() < 0) {
              <p class="err" role="alert">
                Salida + desperdicio supera la entrada en {{ -merma() | kg }}. No se puede
                finalizar.
              </p>
            } @else {
              <p class="small muted">
                Merma = Entrada − Salida − Desperdicio.
              </p>
            }
          }
          @case (3) {
            <div class="review">
              <span class="label">Revisión</span>
              <dl class="dl">
                <dt>Salidas</dt>
                <dd>
                  @for (s of salidas(); track $index) {
                    <span class="chip-id"
                      >{{ s.nombre }} / {{ s.material }} / {{ s.cantidadKg | kg
                      }}{{ s.productoFinal ? ' / producto final' : '' }}</span
                    >
                  }
                </dd>
                <dt>Composición</dt>
                <dd><app-composition [comp]="comp()" /></dd>
                <dt>Desperdicio</dt>
                <dd class="num">
                  {{ desperdicio() | kg }}{{ desperdicio() ? ' / ' + motivo : '' }}
                </dd>
                <dt>Merma</dt>
                <dd class="num">{{ merma() | kg }}</dd>
                <dt>Fin</dt>
                <dd>Automático al confirmar</dd>
              </dl>
            </div>
            <div class="notice">
              <app-icon name="layers" /><span
                >Las entradas pasarán a <b>Procesado</b>. Las salidas se crean
                <b>Disponibles / No revisadas</b> con su propio Traceability ID y QR.</span
              >
            </div>
          }
        }
      </div>
      @if (error()) {
        <p class="err" role="alert" style="margin-top:.6rem">{{ error() }}</p><button type="button" class="btn btn--ghost btn--sm" (click)="corregir()">Corregir pesos de salida</button>
      }
      <ng-container footer>
        @if (paso() > 0) {
          <button
            class="btn btn--ghost"
            type="button"
            (click)="paso.set(paso() - 1); error.set('')"
          >
            <app-icon name="arrowLeft" [size]="14" /> Atrás
          </button>
        } @else {
          <button class="btn btn--ghost" type="button" (click)="closed.emit()">Cancelar</button>
        }
        @if (paso() < 3) {
          <button class="btn btn--primary" type="button" (click)="siguiente()">
            Continuar <app-icon name="arrowRight" [size]="14" />
          </button>
        } @else {
          <button class="btn btn--primary" type="button" (click)="finalizar()">
            <app-icon name="check" [size]="16" /> Finalizar transformación
          </button>
        }
      </ng-container>
    </app-dialog>
  `,
  styles: `
    .out {
      display: grid;
      grid-template-columns: 12rem 7rem 1fr 2rem;
      gap: 0.4rem;
      align-items: center;
    }
    @media (max-width: 760px) {
      .out {
        grid-template-columns: 1fr 6rem 2rem;
      }
      .out input:nth-child(3) {
        grid-column: 1 / -1;
        grid-row: 2;
      }
    }
    .balance {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1rem;
      align-items: center;
      padding: 0.8rem 1rem;
      background: var(--canvas);
    }
    .balance > div {
      display: grid;
    }
    .balance b {
      font-size: 1.05rem;
    }
    .op {
      font: 700 1.1rem var(--f-body);
      color: var(--brown-ink);
    }
    .balance.bad {
      background: var(--carmine-paper);
      color: var(--carmine);
    }
  `,
})
export class FinalizarProcesoDialog {
  corregir() {
    this.paso.set(0);
    setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('[data-correction-field]')?.focus(), 0);
  }
  readonly store = inject(StoreService);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly router = inject(Router);
  private readonly ui = inject(UiService);
  readonly proceso = input.required<Proceso>();
  readonly closed = output<void>();
  readonly materiales = computed(() => [...new Set([...MATERIALES, ...this.store.misLotes().map(lote => lote.material)])].sort());
  readonly pasos = [
    'Lotes de salida',
    'Composición',
    'Desperdicio y balance',
    'Revisar y confirmar',
  ];
  readonly paso = signal(0);
  readonly salidas = signal<Salida[]>([]);
  readonly comp = computed<Componente[]>(() => {
    const inputs = this.proceso().entradas;
    if (inputs.some(input => !this.store.lote(input.loteId)?.composicion.length)) return [];
    const entries = new Map<string, { nombre: string; numerator: bigint; cents: number; remainder: bigint }>();
    let total = 0n;
    for (const input of inputs) {
      const grams = BigInt(Math.round(input.cantidadKg * 1000));
      total += grams;
      for (const component of this.store.lote(input.loteId)!.composicion) {
        const key = component.nombre.trim().toLowerCase();
        const row = entries.get(key) ?? { nombre: component.nombre, numerator: 0n, cents: 0, remainder: 0n };
        row.numerator += grams * BigInt(Math.round(component.porcentaje * 100));
        entries.set(key, row);
      }
    }
    if (total <= 0n) return [];
    const rows = [...entries.values()];
    for (const row of rows) { row.cents = Number(row.numerator / total); row.remainder = row.numerator % total; }
    const remaining = 10000 - rows.reduce((sum, row) => sum + row.cents, 0);
    const sorted = [...rows].sort((a, b) => a.remainder === b.remainder ? 0 : a.remainder > b.remainder ? -1 : 1);
    for (let i = 0; i < remaining; i++) sorted[i].cents++;
    return rows.filter(row => row.cents > 0).map(row => ({ nombre: row.nombre, porcentaje: row.cents / 100 }));
  });
  readonly desperdicio = signal(0);
  motivo = '';
  readonly error = signal('');

  readonly entrada = computed(() => this.store.totalEntrada(this.proceso()));
  readonly salidaTotal = computed(() =>
    this.salidas().reduce((s, x) => s + (+(x.cantidadKg ?? 0) || 0), 0),
  );
  readonly merma = computed(
    () => Math.round((this.entrada() - this.salidaTotal() - this.desperdicio()) * 10) / 10,
  );

  ngOnInit() {
    const p = this.proceso();
    const mat: Record<string, TipoMaterial> = {
      'Hilado peinado': 'Hilo peinado',
      'Hilado de mezcla': 'Hilo mezcla',
      'Tejido de punto jersey': 'Tela jersey',
      'Tejido de punto piqué': 'Tela piqué',
      'Corte y confección': 'Prenda final',
      Hilado: 'Hilo peinado',
      Tejido: 'Tela jersey',
      Teñido: 'Tela jersey',
    };
    const first = this.store.lote(p.entradas[0].loteId)!;
    this.salidas.set([
      {
        material: mat[p.tipo] ?? first.material,
        nombre: (mat[p.tipo] ?? first.material).slice(0, 20),
        cantidadKg: null,
        productoFinal: false,
        distribuidor: '',
        precioVenta: null,
        moneda: 'PEN',
        fechaSalida: '',
        referencia: '',
      },
    ]);
  }
  patch(i: number, p: Partial<Salida>) {
    this.salidas.update((s) => s.map((x, j) => (j === i ? { ...x, ...p } : x)));
  }
  agregar() {
    this.salidas.update((s) => [
      ...s,
      {
        ...s[0],
        nombre: s[0].material.slice(0, 20),
        cantidadKg: null,
        productoFinal: false,
        distribuidor: '',
        precioVenta: null,
        fechaSalida: '',
        referencia: '',
      },
    ]);
  }
  quitar(i: number) {
    this.salidas.update((s) => s.filter((_, j) => j !== i));
  }
  siguiente() {
    let e = '';
    if (
      this.paso() === 0 &&
      this.salidas().some((s) => !s.nombre.trim() || s.nombre.trim().length > 20 || !(Number(s.cantidadKg) > 0))
    )
      e = 'Cada salida necesita un nombre de hasta 20 caracteres y un peso mayor a 0 kg.';
    if (this.paso() === 1 && !this.comp().length) e = 'Cargando la composición de las entradas. Espera un momento.';
    if (this.paso() === 2) {
      if (this.merma() < 0) e = 'Salida + desperdicio no puede superar la entrada.';
      else if (this.salidaTotal() / this.entrada() < (this.proceso().tipo === 'Hilado' ? .6 : .8)) e = 'El peso de salida no alcanza el rendimiento mínimo: ' + (this.proceso().tipo === 'Hilado' ? '60 %' : '80 %') + '. Revisa los pesos.';
      else if (this.desperdicio() > 0 && !this.motivo.trim())
        e = 'Indica el motivo del desperdicio.';
    }
    this.error.set(e);
    if (!e) this.paso.update((p) => p + 1);
  }
  readonly draft = formDraft(() => 'transformation/complete/' + this.proceso().id, () => ({ salidas: this.salidas(), desperdicio: this.desperdicio(), motivo: this.motivo, paso: this.paso() }), value => {
    this.salidas.set(value.salidas); this.desperdicio.set(value.desperdicio); this.motivo = value.motivo; this.paso.set(value.paso);
  });
  async finalizar() {
    const r = await this.store.finalizarProceso(
      this.proceso().id,
      this.salidas().map((s) => ({
        nombre: s.nombre,
        material: s.material,
        cantidadKg: Number(s.cantidadKg),
        productoFinal: s.productoFinal,
        comercial: s.productoFinal
          ? {
              distribuidor: s.distribuidor,
              precioVenta: s.precioVenta,
              moneda: s.moneda,
              fechaSalida: s.fechaSalida,
              referencia: s.referencia,
            }
          : undefined,
      })),
      this.comp(),
      this.desperdicio(),
      this.motivo,
    );
    if (!r.ok) return this.error.set(r.error);
    await this.draft.clear();
    this.ui.toast(`Transformación finalizada: ${this.salidas().map(s => s.nombre).join(', ')}.`);
    this.closed.emit();
    if (this.salidas().length > 1) void this.router.navigate(['/lotes']);
  }
}
