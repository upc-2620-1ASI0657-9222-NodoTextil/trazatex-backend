import { formDraft } from '../../shared/form-draft.directive';
import { DENSITY } from '../../shared/density';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { SHARED, UiService } from '../../shared/ui';
import { tonoFalla } from './fallas.page';

@Component({
  selector: 'app-falla-detalle',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (falla() && !store.lote(falla()!.loteId)) {
      <div class="page"><p role="status">Cargando los datos autorizados del lote…</p><a routerLink="/fallas">Volver a Fallas</a></div>
    } @else if (falla(); as f) {
      @let lote = store.lote(f.loteId)!;
      @let ctl = store.control(f.controlId)!;
      @let estado = store.estadoFalla(f);
      @let gestiona = session.esAdmin() && store.puedeVerPrivado(f.empresaId);
      @let pendiente = estado === 'Pendiente de decisión' && !f.discardPending;
      <div class="page detail-page">
        <header class="page-head">
          <div>
            <a class="eyebrow" routerLink="/fallas" style="text-decoration:none">Fallas</a>
            <h2 class="page-title">Falla <span class="id">{{ f.id }}</span></h2>
            <p class="page-lead">
              Lote <app-lot-link [id]="f.loteId" /> / {{ lote?.material || 'Lote histórico' }} / {{ store.nombreEmpresa(f.empresaId) }} /
              <app-badge [text]="estado" [tone]="tonoFalla(estado)" />
            </p>
          </div>
          <div class="actions">
            <a class="btn btn--ghost" [routerLink]="['/trazabilidad']" [queryParams]="{ lote: f.loteId }"><app-icon name="trace" [size]="16" /> Impacto en genealogía</a>
            @if (gestiona) {
              <app-action icon="quality" [reason]="pendiente ? null : razonCerrada(estado)" (run)="reevaluar()">Enviar a reevaluación</app-action>
              <app-action icon="x" cls="btn--danger" [tipRight]="true" [reason]="pendiente ? null : razonCerrada(estado)" (run)="confirmarDescarte.set(true)">Descartar lote</app-action>
            } @else {
              <app-readonly />
            }
          </div>
        </header>

        @if (estado === 'En reevaluación') {
          <div class="notice notice--warn" style="margin-bottom:1rem">
            <app-icon name="lock" />
            <span>El lote está <b>Por reevaluar</b> y sigue bloqueado. Una Reevaluación Conforme resolverá la falla; si vuelve a fallar, la decisión queda pendiente otra vez.
              @if (gestiona) { <a routerLink="/calidad">Ir a Calidad</a>. }
            </span>
          </div>
        }

        <div class="split">
          <div class="folds">
            <app-fold n="01" title="Pruebas que originaron la falla" [summary]="fallidas().length + ' con falla / ' + ctl.id" [open]="true">
              <div class="mini-cards">
                @for (p of fallidas(); track p.id) {
                  <div class="mini mini--fail">
                    <div class="mini-top"><b>{{ p.criterio }}</b><app-badge [text]="p.resultado" tone="fail" /></div>
                    <dl class="facts">
                      <div><dt>Esperado</dt><dd class="num">@if (store.puedeVer('valoresControl', f.empresaId)) {{{ p.valorEsperado }} {{ p.unidad }}} @else {<app-priv />}</dd></div>
                      <div><dt>Obtenido</dt><dd class="num">@if (store.puedeVer('valoresControl', f.empresaId)) {{{ p.valorObtenido }} {{ p.unidad }}} @else {<app-priv />}</dd></div>
                    </dl>
                  </div>
                }
              </div>
            </app-fold>

            <app-fold n="02" title="Registro y decisión" [summary]="estado">
              @if (gestiona && !f.estado) {
                <div class="form-grid" [appDraft]="draft">
                  <label class="field span-all"><span>Causa</span><input class="input" [(ngModel)]="causa" placeholder="p. ej. tensión excesiva en tejido" /></label>
                  <label class="field"><span>Cantidad afectada (kg)</span><input class="input num" type="number" min="0" [max]="lote.cantidadKg" [(ngModel)]="cantidad" /></label>
                  <label class="field span-all"><span>Observaciones <small class="muted">(internas, siempre privadas)</small></span><textarea class="textarea" [(ngModel)]="observaciones"></textarea></label>
                </div>
                @if (error()) {<p class="err" role="alert" style="margin-top:.5rem">{{ error() }}</p>}
                <button class="btn btn--ghost" type="button" style="margin-top:.75rem" (click)="guardar()"><app-icon name="check" [size]="16" /> Guardar registro</button>
              } @else {
                <dl class="dl">
                  <dt>Causa</dt>
                  <dd>@if (store.puedeVer('causaFalla', f.empresaId)) {{{ f.causa || 'Sin registrar' }}} @else {<app-priv />}</dd>
                  <dt>Cantidad afectada</dt>
                  <dd>@if (store.puedeVer('cantidad', f.empresaId)) {<span class="num">{{ f.cantidadAfectadaKg | kg }}</span>} @else {<app-priv />}</dd>
                  <dt>Observaciones</dt>
                  <dd>@if (store.puedeVerPrivado(f.empresaId)) {{{ f.observaciones || '—' }}} @else {<app-priv [always]="true" />}</dd>
                </dl>
              }
            </app-fold>

            <app-fold n="03" title="Impacto en la genealogía" [summary]="familia().length + ' lote(s) relacionados'">
              <div class="grid-2">
                <div>
                  <h3 class="label">Trazado hacia atrás</h3>
                  <ul class="rel">
                    @for (a of ancestros(); track a) {
                      <li><app-lot-link [id]="a" /> <span class="small muted">{{ store.lote(a)?.material }}</span></li>
                    } @empty {
                      <li class="small muted">Lote de recepción, sin ancestros.</li>
                    }
                  </ul>
                </div>
                <div>
                  <h3 class="label">Lotes relacionados</h3>
                  <ul class="rel">
                    @for (r of familia(); track r) {
                      @let rl = store.lote(r)!;
                      <li [class.hit]="rl.calidad === 'Posible falla derivada'">
                        <app-lot-link [id]="r" />
                        <app-lot-status [lote]="rl" [showAvail]="false" />
                      </li>
                    } @empty {
                      <li class="small muted">Sin ramas relacionadas.</li>
                    }
                  </ul>
                </div>
              </div>
              @if (pendiente && candidatos().length) {
                <div class="notice notice--warn" style="margin-top:.9rem">
                  <app-icon name="failure" />
                  <span>Si se descarta, {{ candidatos().length }} lote(s) Disponible(s) pasarán a <b>Posible falla derivada</b>: {{ candidatos().join(', ') }}.</span>
                </div>
              }
              <p class="small muted" style="margin-top:.6rem">Solo los lotes que siguen Disponibles pueden marcarse; los históricos sirven para reconstruir la relación.</p>
            </app-fold>
          </div>

          <div class="folds">
            <app-fold title="Lote afectado" [summary]="f.loteId" [open]="true">
              <dl class="dl">
                <dt>Lote</dt><dd><app-lot-link [id]="lote.id" /></dd>
                <dt>Material</dt><dd>{{ lote?.material || 'Lote histórico' }}</dd>
                <dt>Estado</dt><dd><app-lot-status [lote]="lote" /></dd>
                <dt>Control</dt><dd><a class="id" [routerLink]="['/calidad', ctl.id]">{{ ctl.id }}</a> {{ ctl.tipo }}</dd>
                <dt>Generada</dt><dd class="num">{{ f.fecha | fecha }}</dd>
                <dt>Resolución</dt><dd><app-badge [text]="estado" [tone]="tonoFalla(estado)" /></dd>
              </dl>
            </app-fold>
            <app-fold title="Evidencias" [summary]="(f.evidencias.length + ctl.evidencias.length) + ' foto(s)'" [open]="true">
              <app-evidence ownerType="falla" [ownerId]="f.id" [items]="f.evidencias" [canAdd]="gestiona" />
              @if (ctl.evidencias.length) { <p class="small muted">Fotografías del control de origen</p><app-evidence [items]="ctl.evidencias" [canAdd]="false" /> }
            </app-fold>
          </div>
        </div>
      </div>

      @if (confirmarDescarte()) {
        <app-dialog [title]="'Descartar ' + f.loteId" eyebrow="Decisión sobre la falla" (closed)="confirmarDescarte.set(false)">
          <ul class="small" style="padding-left:1.1rem;margin:0">
            <li>{{ f.loteId }} pasará a fase <b>Descartado</b> y quedará como lote histórico.</li>
            <li>{{ f.id }} pasará a <b>Confirmada</b>.</li>
            @if (candidatos().length) {
              <li>{{ candidatos().join(', ') }} quedarán en <b>bloqueo preventivo</b> (Posible falla derivada).</li>
            }
          </ul>
          <p class="small muted" style="margin-top:.6rem">Esta acción no se puede deshacer.</p>
          <ng-container footer>
            <button class="btn btn--ghost" type="button" (click)="confirmarDescarte.set(false)">Cancelar</button>
            <button class="btn btn--danger" type="button" (click)="descartar()">Descartar lote</button>
          </ng-container>
        </app-dialog>
      }
    } @else {
      <div class="page"><div class="empty"><strong>Falla no encontrada</strong><a routerLink="/fallas">Volver</a></div></div>
    }
  `,
  styles: `
    .rel { list-style: none; margin: .4rem 0 0; padding: 0; display: grid; gap: .4rem; }
    .rel li { display: flex; flex-wrap: wrap; gap: .5rem; align-items: center; padding-bottom: .4rem; border-bottom: 1px dashed var(--hair); }
    .rel li.hit { background: var(--carmine-paper); padding: .35rem .4rem; }
  `,
})
export class FallaDetallePage {
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  private readonly ui = inject(UiService);
  readonly tonoFalla = tonoFalla;
  readonly id = input.required<string>();
  readonly falla = computed(() => this.store.falla(this.id()));
  readonly fallidas = computed(() => this.store.control(this.falla()?.controlId)?.pruebas.filter((p) => p.resultado === 'Con falla') ?? []);
  readonly ancestros = computed(() => [...this.store.ancestros(this.falla()!.loteId)].sort());
  readonly familia = computed(() => this.store.familiaFalla(this.falla()!.loteId));
  readonly candidatos = computed(() => this.store.relacionadosDerivados(this.falla()!.loteId));
  readonly confirmarDescarte = signal(false);
  readonly error = signal('');
  causa = '';
  cantidad: number | null = null;
  observaciones = '';
  private loadedId = '';
  constructor() {
    effect(() => {
      const failure = this.falla();
      if (!failure) return;
      this.store.ensureQualityBatch(failure.loteId);
      this.store.requestEvidence('falla', failure.id);
      this.store.requestEvidence('control', failure.controlId);
      if (this.loadedId === failure.id) return;
      this.loadedId = failure.id;
      this.causa = failure.causa ?? '';
      this.cantidad = failure.cantidadAfectadaKg ?? null;
      this.observaciones = failure.observaciones ?? '';
    });
  }

  ngOnInit() {
    const f = this.falla();
    if (f) {
      this.causa = f.causa ?? '';
      this.cantidad = f.cantidadAfectadaKg ?? null;
      this.observaciones = f.observaciones ?? '';
    }
  }
  razonCerrada(e: string) {
    return e === 'En reevaluación'
      ? 'El lote ya fue enviado a reevaluación. Finaliza la Reevaluación en Calidad.'
      : `La falla está ${e}: no admite nuevas decisiones.`;
  }
  readonly draft = formDraft(() => 'quality/failure/' + this.id(), () => ({ causa: this.causa, cantidad: this.cantidad, observaciones: this.observaciones }), value => {
    this.causa = value.causa; this.cantidad = value.cantidad; this.observaciones = value.observaciones;
  });
  async guardar() {
    if (!this.causa.trim() || this.cantidad === null || this.cantidad <= 0) {
      this.error.set('Registra la causa y una cantidad afectada mayor que cero.');
      return false;
    }
    const lote = this.store.lote(this.falla()!.loteId)!;
    if (this.cantidad !== null && (this.cantidad <= 0 || this.cantidad > lote.cantidadKg))
      { this.error.set(`La cantidad afectada debe estar entre 0 y ${lote.cantidadKg} kg.`); return false; }
    this.error.set('');
    const result = await this.store.actualizarFalla(this.id(), { causa: this.causa || undefined, cantidadAfectadaKg: this.cantidad ?? undefined, observaciones: this.observaciones || undefined });
    if (!result.ok) return false;
    await this.draft.clear();
    this.ui.toast('Registro de la falla guardado');
    return true;
  }
  /**
   * US26 esc. 2 y `api.md`: reevaluar NO exige registrar nada (el servidor no pide información). Solo si el registro está completo se guarda
   * antes (con sus validaciones); si hay datos a medias, se avisa de que no se guardarán en vez de impedir la reevaluación (FE-M12).
   */
  async reevaluar() {
    const algo = !!this.causa.trim() || this.cantidad !== null || !!this.observaciones.trim();
    const completo = !!this.causa.trim() && this.cantidad !== null && this.cantidad > 0;
    const mensaje = algo && !completo
      ? 'Hay datos del registro sin guardar (para guardarlos hacen falta la causa y una cantidad mayor que cero). Reevaluar no los exige. ¿Enviar el lote a reevaluación sin guardarlos? Seguirá bloqueado hasta finalizar el nuevo control.'
      : '¿Enviar el lote a reevaluación? Seguirá bloqueado hasta finalizar el nuevo control.';
    if (!(await this.ui.confirm(mensaje))) return;
    if (completo && !await this.guardar()) return;
    const result = await this.store.enviarAReevaluacion(this.id());
    if (!result.ok) return;
    this.ui.toast(`${this.falla()!.loteId} enviado a reevaluación / sigue bloqueado`);
  }
  async descartar() {
    if (!await this.guardar()) return;
    const result = await this.store.descartarLote(this.id());
    if (!result.ok) return;
    this.confirmarDescarte.set(false);
    this.ui.toast(this.falla()?.discardPending ? 'Descarte solicitado. Esperando confirmación de Producción.' : 'Lote descartado. El sistema analizará las posibles fallas derivadas.');
  }
}
