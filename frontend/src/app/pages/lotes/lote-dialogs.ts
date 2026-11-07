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
import { Componente, Evidencia, Lote, MATERIALES, TipoMaterial } from '../../core/models';
import { StoreService, ahora } from '../../core/store.service';
import { DENSITY } from '../../shared/density';
import { SHARED, UiService } from '../../shared/ui';

@Component({
  selector: 'app-composition-editor',
  imports: [...SHARED, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ce">
      @for (c of rows(); track $index; let i = $index) {
        <div class="ce-row">
          <input
            class="input"
            list="composiciones-conocidas" maxlength="80"
            [ngModel]="c.nombre"
            (ngModelChange)="set(i, 'nombre', $event)"
            placeholder="Componente (p. ej. Algodón Pima)"
            [attr.aria-label]="'Componente ' + (i + 1)"
          />
          <div class="pct">
            <input
              class="input num"
              type="number"
              min="0"
              max="100"
              step="0.1"
              [ngModel]="c.porcentaje"
              (ngModelChange)="set(i, 'porcentaje', +$event)"
              [attr.aria-label]="'Porcentaje ' + (i + 1)"
            />
            <span>%</span>
          </div>
          <button
            class="x"
            type="button"
            (click)="remove(i)"
            [disabled]="rows().length === 1"
            aria-label="Quitar componente"
          >
            <app-icon name="x" [size]="16" />
          </button>
        </div>
      }
      <datalist id="composiciones-conocidas">
        @for (nombre of sugerencias(); track nombre) {
          <option [value]="nombre"></option>
        }
      </datalist>
      <div class="sum-line" [class.ok]="total() === 100" [class.bad]="total() !== 100">
        <button class="btn btn--bare btn--sm" type="button" (click)="add()">
          <app-icon name="plus" [size]="14" /> Agregar componente
        </button>
        <span
          >Total <b class="num">{{ total() }} %</b>
          {{ total() === 100 ? '/ correcto' : '/ debe ser 100 %' }}</span
        >
      </div>
    </div>
  `,
  styles: `
    .ce {
      display: grid;
      gap: 0.4rem;
    }
    .ce-row {
      display: grid;
      grid-template-columns: 1fr 7rem 2rem;
      gap: 0.4rem;
      align-items: center;
    }
    .pct {
      display: flex;
      align-items: center;
      gap: 0.3rem;
    }
  `,
})
export class CompositionEditorComponent {
  private readonly store = inject(StoreService);
  readonly sugerencias = computed(() =>
    [
      ...new Set([
        'Algodón Pima',
        'Algodón',
        'Alpaca',
        'Lana',
        'Poliéster',
        'Viscosa',
        ...this.store.lotes().flatMap((l) => l.composicion.map((c) => c.nombre)),
      ]),
    ].sort(),
  );
  readonly rows = input.required<Componente[]>();
  readonly rowsChange = output<Componente[]>();
  readonly total = computed(
    () => Math.round(this.rows().reduce((s, c) => s + (+c.porcentaje || 0), 0) * 10) / 10,
  );
  set(i: number, k: keyof Componente, v: string | number) {
    const rows = this.rows().map(row => ({ ...row }));
    if (k === 'nombre') rows[i].nombre = String(v);
    else {
      const value = Math.round(Math.min(100, Math.max(0, Number(v) || 0)) * 100);
      rows[i].porcentaje = value / 100;
      const others = rows.map((_, index) => index).filter(index => index !== i);
      const total = others.reduce((sum, index) => sum + (Number(rows[index].porcentaje) || 0), 0);
      let remainder = 10000 - value;
      others.forEach((index, position) => {
        const amount = position === others.length - 1 ? remainder : Math.min(remainder, Math.round((10000 - value) * (total > 0 ? (Number(rows[index].porcentaje) || 0) / total : 1 / others.length)));
        rows[index].porcentaje = amount / 100;
        remainder -= amount;
      });
    }
    this.rowsChange.emit(rows);
  }
  add() {
    this.rowsChange.emit([
      ...this.rows(),
      { nombre: '', porcentaje: 0 },
    ]);
  }
  remove(i: number) {
    const rows = this.rows().filter((_, j) => j !== i).map(row => ({ ...row }));
    const total = rows.reduce((sum, row) => sum + row.porcentaje, 0);
    if (rows.length) rows[0].porcentaje = Math.round((rows[0].porcentaje + 100 - total) * 100) / 100;
    this.rowsChange.emit(rows);
  }
}

@Component({
  selector: 'app-nuevo-lote',
  imports: [...SHARED, ...DENSITY, FormsModule, CompositionEditorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .suggested-name {
      display: flex;
      gap: 0.5rem;
      align-items: stretch;
    }
    .suggested-name .input {
      flex: 1;
      min-width: 0;
    }
    @media (max-width: 520px) {
      .suggested-name {
        flex-wrap: wrap;
      }
      .suggested-name .btn {
        width: 100%;
      }
    }
  `,
  template: `
    <app-dialog [appDraft]="draft"
      title="Registrar lote"
      eyebrow="Recepción de material"
      [wide]="true"
      (closed)="closed.emit()"
    >
      <app-stepper [steps]="pasos" [current]="paso()" />
      <div class="step-body">
        @switch (paso()) {
          @case (0) {
            <div class="form-grid">
              <label class="field span-all"
                ><span>Nombre del lote</span>
                <div class="suggested-name">
                  <input
                    class="input"
                    [ngModel]="nombre || nombreSugerido()"
                    (ngModelChange)="nombre = $event"
                    name="nombre"
                    maxlength="20"
                    [placeholder]="nombreSugerido()"
                    aria-label="Nombre del lote"
                  />
                  <button
                    class="btn btn--ghost btn--sm"
                    type="button"
                    (click)="nombre = nombreSugerido()"
                    title="Usar nombre sugerido"
                  >
                    <app-icon name="check" [size]="15" /> Sugerir
                  </button>
                </div>
                <span class="hint"
                  >El Traceability ID y el QR se generan al guardar.</span
                >
              </label>
              <label class="field">
                <span>Tipo de material</span>
                <select
                  class="select"
                  [ngModel]="material"
                  (ngModelChange)="cambiarMaterial($event)"
                  name="material"
                >
                  @for (m of materiales(); track m) {
                    <option [value]="m">{{ m }}</option>
                  }
                </select>
              </label>
              <label class="field">
                <span>Cantidad (kg)</span>
                <input
                  class="input num"
                  type="number"
                  min="0"
                  step="0.1"
                  [(ngModel)]="cantidad"
                  name="cantidad"
                />
              </label>
              <label class="field">
                <span>Proveedor <small class="muted">(opcional)</small></span>
                <input
                  class="input"
                  list="proveedores-conocidos"
                  [(ngModel)]="proveedor"
                  name="proveedor"
                  placeholder="Buscar o escribir proveedor"
                />
                <datalist id="proveedores-conocidos">
                  @for (valor of proveedoresConocidos(); track valor) {
                    <option [value]="valor"></option>
                  }
                </datalist>
              </label>
              <label class="field">
                <span>Origen geográfico</span>
                <input
                  class="input"
                  list="origenes-conocidos"
                  [(ngModel)]="origen"
                  name="origen"
                  placeholder="Distrito, región"
                />
                <datalist id="origenes-conocidos">
                  @for (valor of origenesConocidos(); track valor) {
                    <option [value]="valor"></option>
                  }
                </datalist>
              </label>
              <label class="field span-all">
                <span>Características técnicas de recepción</span>
                <textarea
                  class="textarea"
                  [(ngModel)]="caracteristicas"
                  name="car"
                  placeholder="p. ej. longitud de fibra, micronaire, humedad"
                ></textarea>
              </label>
            </div>
          }
          @case (1) {
            <div class="field">
              <span>Composición (debe sumar 100 %)</span>
              <app-composition-editor [rows]="comp()" (rowsChange)="comp.set($event)" />
            </div>
            <div class="field">
              <span>Evidencia fotográfica <small class="muted">(opcional)</small></span>
              <app-evidence [items]="evidencias()" [pending]="true" [canAdd]="true" (added)="addEv($event)" (removed)="removeEv($event)" (renamed)="renameEv($event)" />
            </div>
          }
          @case (2) {
            <div class="review">
              <span class="label">Revisión</span>
              <dl class="dl">
                <dt>Nombre</dt>
                <dd>{{ nombre || nombreSugerido() }}</dd>
                <dt>Material</dt>
                <dd>{{ material }}</dd>
                <dt>Cantidad</dt>
                <dd class="num">{{ cantidad | kg }}</dd>
                <dt>Proveedor</dt>
                <dd>{{ proveedor || 'Desconocido' }}</dd>
                <dt>Origen</dt>
                <dd>{{ origen }}</dd>
                <dt>Composición</dt>
                <dd><app-composition [comp]="comp()" /></dd>
                <dt>Evidencias</dt>
                <dd class="num">{{ evidencias().length }}</dd>
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
          <button class="btn btn--ghost" type="button" (click)="atras()">
            <app-icon name="arrowLeft" [size]="14" /> Atrás
          </button>
        } @else {
          <button class="btn btn--ghost" type="button" (click)="closed.emit()">Cancelar</button>
        }
        @if (paso() < pasos.length - 1) {
          <button class="btn btn--primary" type="button" (click)="siguiente()">
            Continuar <app-icon name="arrowRight" [size]="14" />
          </button>
        } @else {
          <button class="btn btn--primary" type="button" [disabled]="store.saving()" (click)="guardar()">
            <app-icon name="check" [size]="16" /> {{ store.saving() ? 'Registrando…' : 'Registrar lote' }}
          </button>
        }
      </ng-container>
    </app-dialog>
  `,
})
export class NuevoLoteDialog {
  readonly store = inject(StoreService);
  private readonly ui = inject(UiService);
  private readonly router = inject(Router);
  readonly closed = output<void>();
  readonly pasos = ['Material', 'Composición y evidencia', 'Revisar y confirmar'];
  readonly paso = signal(0);
  readonly materiales = computed(() => [...new Set([...MATERIALES, ...this.store.misLotes().map(lote => lote.material)])].filter(material => material.startsWith('Fibra') || material.startsWith('Hilo')));
  readonly proveedoresConocidos = computed(() =>
    [
      ...new Set(
        this.store
          .misLotes()
          .map((l) => l.proveedor)
          .filter((x): x is string => !!x),
      ),
    ].sort(),
  );
  readonly origenesConocidos = computed(() =>
    [
      ...new Set(
        this.store
          .misLotes()
          .map((l) => l.origen)
          .filter(Boolean),
      ),
    ].sort(),
  );
  nombre = '';
  material: TipoMaterial = 'Fibra de algodón Pima';
  nombreSugerido = () => this.material.replace(/^Fibra de /, '').replace(/^Hilo de /, '').slice(0, 20);
  cambiarMaterial(v: TipoMaterial) {
    this.material = v;
    if (this.comp().length === 1 && v.startsWith('Fibra de ')) {
      const nombre = v.replace(/^Fibra de /, '');
      this.comp.set([{ nombre, porcentaje: 100 }]);
    }
  }
  cantidad: number | null = null;
  proveedor = '';
  origen = '';
  caracteristicas = '';
  readonly comp = signal<Componente[]>([{ nombre: 'Algodón Pima', porcentaje: 100 }]);
  readonly evidencias = signal<Evidencia[]>([]);
  readonly error = signal('');

  private validar(p: number): string {
    if (p === 0) {
      if (!(this.nombre.trim() || this.nombreSugerido().trim()))
        return 'Indica el nombre del lote.';
      if ((this.nombre.trim() || this.nombreSugerido()).length > 20) return 'El nombre admite hasta 20 caracteres.';
      if (!(Number(this.cantidad) > 0)) return 'La cantidad debe ser mayor a 0 kg.';
      if (!this.origen.trim()) return 'Indica el origen geográfico.';
      if (!this.caracteristicas.trim())
        return 'Registra las características técnicas de recepción.';
    }
    if (p === 1) {
      if (this.evidencias().some(e => !e.nombre.trim())) return 'Indica un nombre descriptivo para cada fotografía.';
      if (this.comp().some((c) => !c.nombre.trim())) return 'Cada componente necesita un nombre.';
      if (this.comp().some(c => !(c.porcentaje > 0))) return 'Cada componente debe superar 0 %. Quita las fibras que no participen en la composición.';
      const t = this.comp().reduce((s, c) => s + (+c.porcentaje || 0), 0);
      if (Math.abs(t - 100) > 0.001) return 'La composición debe sumar exactamente 100 %.';
    }
    return '';
  }
  siguiente() {
    const e = this.validar(this.paso());
    this.error.set(e);
    if (!e) this.paso.update((p) => p + 1);
  }
  atras() {
    this.error.set('');
    this.paso.update((p) => p - 1);
  }
  private readonly pendingFiles = new Map<string, File>();
  removeEv(id: string) {
    const item = this.evidencias().find(e => e.id === id);
    if (item) URL.revokeObjectURL(item.url);
    this.pendingFiles.delete(id);
    this.evidencias.update(items => items.filter(e => e.id !== id));
  }
  renameEv(event: {id: string; name: string}) { this.evidencias.update(items => items.map(e => e.id === event.id ? {...e, nombre: event.name} : e)); }
  ngOnDestroy() { this.evidencias().forEach(e => URL.revokeObjectURL(e.url)); }
  addEv(f: File) {
    const id = crypto.randomUUID();
    this.pendingFiles.set(id, f);
    this.evidencias.update((e) => [
      ...e,
      { id, nombre: f.name, url: URL.createObjectURL(f), fecha: ahora() },
    ]);
  }
  readonly draft = formDraft('batch/new', () => ({ nombre: this.nombre, material: this.material, cantidad: this.cantidad,
    proveedor: this.proveedor, origen: this.origen, caracteristicas: this.caracteristicas, comp: this.comp(), paso: this.paso(),
    evidencias: this.evidencias(), files: this.pendingFiles }), value => {
    this.nombre = value.nombre; this.material = value.material; this.cantidad = value.cantidad; this.proveedor = value.proveedor;
    this.origen = value.origen; this.caracteristicas = value.caracteristicas; this.comp.set(value.comp); this.paso.set(value.paso);
    for (const [id, file] of value.files) this.pendingFiles.set(id, file);
    this.evidencias.set(value.evidencias.map(item => ({ ...item, url: URL.createObjectURL(value.files.get(item.id)!) })));
  });
  async guardar() {
    const r = await this.store.crearLote({
      nombre: this.nombre.trim() || this.nombreSugerido(),
      material: this.material,
      cantidadKg: Number(this.cantidad),
      proveedor: this.proveedor,
      origen: this.origen,
      caracteristicasRecepcion: this.caracteristicas,
      composicion: this.comp(),
      evidencias: this.evidencias(),
    });
    if (!r.ok) return this.error.set(r.error);
    let allAttached = true;
    for (const [id, source] of this.pendingFiles) {
      const file = new File([source], this.evidencias().find(e => e.id === id)?.nombre.trim() || source.name, {type: source.type});
      const upload = await this.store.agregarEvidencia('lote', r.id!, file);
      if (!upload.ok) {
        allAttached = false;
        this.ui.toast(
          'El lote se registró, pero falta adjuntar una fotografía. Conservamos el formulario para reintentar sin duplicar el lote.',
          true,
        );
      }
    }
    if (!allAttached) return this.error.set('El lote ya está registrado. Vuelve a intentar para adjuntar las fotografías pendientes.');
    await this.draft.clear();
    this.evidencias().forEach((e) => URL.revokeObjectURL(e.url));
    this.ui.toast(`Lote ${this.nombre.trim() || this.nombreSugerido()} registrado. Disponible y pendiente de revisión.`);
    this.closed.emit();
    this.router.navigate(['/lotes', r.id]);
  }
}

@Component({
  selector: 'app-dividir-lote',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog [appDraft]="draft"
      [title]="'Dividir ' + lote().nombre"
      eyebrow="División de lote"
      (closed)="closed.emit()"
    >
      <app-stepper [steps]="pasos" [current]="paso()" />
      <div class="step-body">
        @switch (paso()) {
          @case (0) {
            <div class="review">
              <span class="label">Lote padre</span>
              <dl class="dl">
                <dt>Traceability ID</dt>
                <dd class="id">{{ lote().id }}</dd>
                <dt>Material</dt>
                <dd>{{ lote().material }}</dd>
                <dt>Cantidad</dt>
                <dd class="num">{{ lote().cantidadKg | kg }}</dd>
                <dt>Composición</dt>
                <dd><app-composition [comp]="lote().composicion" /></dd>
              </dl>
            </div>
            <p class="muted small">
              Los hijos heredan esta composición y reciben un nuevo Traceability ID y QR.
            </p>
          }
          @case (1) {
            <div class="stack" style="gap:.45rem">
              @for (q of hijos(); track $index; let i = $index) {
                <div class="child">
                  <span class="label">Hijo {{ i + 1 }}</span>
                  <input
                    class="input"
                    [ngModel]="nombres()[i]"
                    (ngModelChange)="setNombre(i, $event)"
                    [attr.aria-label]="'Nombre hijo ' + (i + 1)"
                    placeholder="Nombre del lote hijo" maxlength="20"
                  />
                  <input
                    class="input num"
                    type="number"
                    min="0"
                    step="0.1"
                    [ngModel]="q"
                    (ngModelChange)="setQ(i, $event)"
                    [attr.aria-label]="'Cantidad hijo ' + (i + 1)" data-correction-field
                  />
                  <span class="small">kg</span>
                  <button
                    class="x"
                    type="button"
                    (click)="remove(i)"
                    [disabled]="hijos().length <= 2"
                    aria-label="Quitar hijo"
                  >
                    <app-icon name="x" [size]="16" />
                  </button>
                </div>
              }
            </div>
            <button
              class="btn btn--bare btn--sm"
              type="button"
              style="justify-self:start"
              (click)="add()"
            >
              <app-icon name="plus" [size]="14" /> Agregar hijo
            </button>
          }
          @case (2) {
            <div class="sum-line" [class.ok]="cuadra()" [class.bad]="!cuadra()">
              <span
                >Σ hijos <b class="num">{{ suma() | kg }}</b></span
              >
              <span>{{ cuadra() ? '=' : '≠' }}</span>
              <span
                >padre <b class="num">{{ lote().cantidadKg | kg }}</b></span
              >
            </div>
            @if (cuadra()) {
              <p class="small">
                Cantidad total validada. Se crearán <b>{{ hijos().length }}</b> lotes hijos con la
                composición del padre.
              </p>
            } @else {
              <p class="err" role="alert">
                La suma de los hijos debe ser exactamente igual al lote padre y cada hijo debe ser
                mayor a 0.
                <button type="button" class="btn btn--ghost btn--sm" (click)="corregir()">Corregir cantidades</button>
              </p>
            }
          }
          @case (3) {
            <div class="review">
              <span class="label">Confirmación</span>
              <p>
                <b>{{ lote().nombre }}</b> pasará a <b>Dividido</b>. Hijos
                <b>Disponibles</b>:
              </p>
              <div class="chips">
                @for (q of hijos(); track $index; let i = $index) {
                  <span class="chip-id">{{ nombres()[i] }} / {{ q | kg }}</span>
                }
              </div>
            </div>
          }
        }
      </div>
      @if (error()) {
        <p class="err" role="alert" style="margin-top:.6rem">{{ error() }}</p>
      }
      <ng-container footer>
        @if (paso() > 0) {
          <button class="btn btn--ghost" type="button" (click)="paso.set(paso() - 1)">
            <app-icon name="arrowLeft" [size]="14" /> Atrás
          </button>
        } @else {
          <button class="btn btn--ghost" type="button" (click)="closed.emit()">Cancelar</button>
        }
        @if (paso() < 3) {
          @if (paso() === 2 && !cuadra()) { <small class="action-reason" id="dividir-razon">Los kilos de los hijos deben sumar el total del lote.</small> }
          <button
            class="btn btn--primary"
            type="button"
            [disabled]="paso() === 2 && !cuadra()"
            [attr.aria-describedby]="paso() === 2 && !cuadra() ? 'dividir-razon' : null"
            (click)="paso.set(paso() + 1)"
          >
            Continuar <app-icon name="arrowRight" [size]="14" />
          </button>
        } @else {
          <button class="btn btn--primary" type="button" (click)="dividir()">
            <app-icon name="split" [size]="16" /> Dividir lote
          </button>
        }
      </ng-container>
    </app-dialog>
  `,
  styles: `
    .child {
      display: grid;
      grid-template-columns: 4rem minmax(8rem, 1fr) 7rem auto 2rem;
      gap: 0.5rem;
      align-items: center;
      flex-wrap: wrap;
    }
    @media (max-width: 640px) {
      .child {
        grid-template-columns: 1fr 1fr 2rem;
      }
      .child > .label {
        grid-column: 1/-1;
      }
      .child > input:first-of-type {
        grid-column: 1;
      }
    }
  `,
})
export class DividirLoteDialog {
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  corregir() {
    this.paso.set(1);
    setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('[data-correction-field]')?.focus(), 0);
  }
  private readonly router = inject(Router);
  private readonly store = inject(StoreService);
  private readonly ui = inject(UiService);
  readonly lote = input.required<Lote>();
  readonly closed = output<void>();
  readonly pasos = ['Lote padre', 'Cantidades', 'Validación', 'Confirmación'];
  readonly paso = signal(0);
  readonly hijos = signal<number[]>([0, 0]);
  readonly nombres = signal<string[]>(['Hijo 1', 'Hijo 2']);
  readonly error = signal('');
  readonly suma = computed(
    () => Math.round(this.hijos().reduce((a, b) => a + (+b || 0), 0) * 1000) / 1000,
  );
  readonly cuadra = computed(
    () =>
      this.suma() === this.lote().cantidadKg &&
      this.hijos().every((h) => +h > 0) &&
      this.nombres().every((n) => !!n.trim() && n.trim().length <= 20),
  );

  ngOnInit() {
    this.distribuir(2);
  }
  setNombre(i: number, v: string) {
    this.nombres.update((ns) => ns.map((n, j) => (j === i ? v : n)));
  }
  setQ(i: number, v: number) {
    this.hijos.update((h) => h.map((x, j) => (j === i ? +v : x)));
  }
  private distribuir(count: number) {
    const total = Math.round(this.lote().cantidadKg * 1000);
    const each = Math.floor(total / count);
    this.hijos.set(Array.from({ length: count }, (_, index) => (each + (index < total % count ? 1 : 0)) / 1000));
  }
  add() {
    this.nombres.update(ns => [...ns, `Hijo ${ns.length + 1}`]);
    this.distribuir(this.hijos().length + 1);
  }
  remove(i: number) {
    this.nombres.update((ns) => ns.filter((_, j) => j !== i));
    this.distribuir(this.hijos().length - 1);
  }
  readonly draft = formDraft(() => 'batch/split/' + this.lote().id, () => ({ hijos: this.hijos(), nombres: this.nombres(), paso: this.paso() }), value => {
    this.hijos.set(value.hijos); this.nombres.set(value.nombres); this.paso.set(value.paso);
  });
  async dividir() {
    const r = await this.store.dividirLote(
      this.lote().id,
      this.hijos().map((cantidadKg, i) => ({ nombre: this.nombres()[i], cantidadKg })),
    );
    if (!r.ok) return this.error.set(r.error);
    await this.draft.clear();
    this.ui.toast(`${this.lote().nombre}: se crearon ${this.hijos().length} lotes hijos.`);
    this.closed.emit();
    void this.router.navigate(['/lotes']);
  }
}

@Component({
  selector: 'app-producto-final',
  imports: [...SHARED, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog [appDraft]="draft"
      [title]="'Datos comerciales / ' + lote().id"
      eyebrow="Producto final"
      (closed)="closed.emit()"
    >
      <p class="muted small" style="margin-bottom:1rem">
        Los datos comerciales del producto final son <b>siempre privados</b> entre empresas. Puedes
        actualizarlos sin modificar su estado.
      </p>
      <div class="form-grid">
        <label class="field span-all"
          ><span>Distribuidor o comprador</span><input class="input" [(ngModel)]="distribuidor"
        /></label>
        <label class="field"
          ><span>Precio de venta</span
          ><input class="input num" type="number" min="0" [(ngModel)]="precio"
        /></label>
        <label class="field">
          <span>Moneda</span>
          <select class="select" [(ngModel)]="moneda">
            <option value="PEN">PEN</option>
            <option value="USD">USD</option>
          </select>
        </label>
        <label class="field"
          ><span>Fecha de salida comercial</span
          ><input class="input" type="date" [(ngModel)]="fecha"
        /></label>
        <label class="field"
          ><span>Referencia comercial</span><input class="input" [(ngModel)]="referencia"
        /></label>
      </div>
      <ng-container footer>
        <button class="btn btn--ghost" type="button" (click)="closed.emit()">Cancelar</button>
        <button class="btn btn--primary" type="button" (click)="guardar()">
          <app-icon name="check" [size]="16" /> Guardar datos comerciales
        </button>
      </ng-container>
    </app-dialog>
  `,
})
export class ProductoFinalDialog {
  private readonly store = inject(StoreService);
  private readonly ui = inject(UiService);
  readonly lote = input.required<Lote>();
  readonly closed = output<void>();
  distribuidor = '';
  precio: number | null = null;
  moneda: 'PEN' | 'USD' = 'PEN';
  fecha = '';
  referencia = '';
  ngOnInit() {
    const c = this.lote().comercial;
    if (!c) return;
    this.distribuidor = c.distribuidor;
    this.precio = c.precioVenta;
    this.moneda = c.moneda;
    this.fecha = c.fechaSalida;
    this.referencia = c.referencia;
  }
  readonly draft = formDraft(() => 'batch/commercial/' + this.lote().id, () => ({ distribuidor: this.distribuidor, precio: this.precio,
    moneda: this.moneda, fecha: this.fecha, referencia: this.referencia }), value => {
    this.distribuidor = value.distribuidor; this.precio = value.precio; this.moneda = value.moneda; this.fecha = value.fecha; this.referencia = value.referencia;
  });
  async guardar() {
    const hay = this.distribuidor || this.precio !== null || this.fecha || this.referencia;
    const result = await this.store.actualizarDatosComerciales(
      this.lote().id,
      hay
        ? {
            distribuidor: this.distribuidor,
            precioVenta: this.precio === null ? null : Number(this.precio),
            moneda: this.moneda,
            fechaSalida: this.fecha,
            referencia: this.referencia,
          }
        : undefined,
    );
    if (!result.ok) return;
    await this.draft.clear();
    this.ui.toast(`Datos comerciales de ${this.lote().id} guardados`);
    this.closed.emit();
  }
}
