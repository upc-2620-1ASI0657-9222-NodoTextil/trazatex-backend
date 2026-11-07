import { formDraft } from '../../shared/form-draft.directive';
import { errorMessage } from '../../core/api.service';
import { DENSITY } from '../../shared/density';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ResultadoPrueba } from '../../core/models';
import { cancelReasonText } from '../../core/api-models';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { SHARED, UiService } from '../../shared/ui';
import { PASOS_CONTROL } from './calidad.page';

@Component({
  selector: 'app-control-detalle',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (control() && !store.lote(control()!.loteId)) {
      <div class="page"><p role="status">Cargando los datos autorizados del lote…</p><a routerLink="/calidad">Volver a Calidad</a></div>
    } @else if (control(); as c) {
      @let lote = store.lote(c.loteId)!;
      @let gestiona = session.esAdmin() && store.puedeVerPrivado(c.empresaId);
      @let valores = store.puedeVer('valoresControl', c.empresaId);
      @let obs = store.puedeVerPrivado(c.empresaId);
      @let r = resultado();
      <div class="page detail-page">
        <ng-template appHeaderActions>
          @if (c.fin) { <button class="btn btn--ghost" type="button" (click)="pdf()"><app-icon name="download" [size]="16" /> Informe PDF</button> }
          @if (!gestiona) { <app-readonly /> }
        </ng-template>

        @if (gestiona && !c.fin && c.status === 'IN_PROGRESS' && !c.finishPending) {
          <app-stepper [steps]="pasos" [current]="c.pruebas.length ? 2 : 1" />
        }

        @if (c.status === 'STARTING' || c.finishPending) {
          <div class="notice notice--warn" role="status"><span>Esperando confirmación de Producción. El sistema reintentará automáticamente.</span><button type="button" class="btn btn--ghost btn--sm" (click)="store.refresh()" [disabled]="store.loading()">Actualizar estado</button></div>
        }
        @if (pdfError()) {
          <div class="notice notice--warn" role="alert" data-testid="pdf-error">
            <span>No se pudo descargar el informe PDF: {{ pdfError() }}</span>
            <button type="button" class="btn btn--ghost btn--sm" (click)="pdf()" [disabled]="pdfBusy()">Reintentar</button>
          </div>
        }
        @if (c.status === 'CANCELLED') {
          <div class="notice notice--warn" role="alert">No se pudo iniciar este control: {{ cancelReason(c.cancelReason) }}. Regresa a Calidad para revisar el lote.</div>
        }
        <div class="split">
          <div class="folds">
          <app-fold title="Resultado" [summary]="r ?? 'Sin pruebas registradas'" [open]="true">
          <div class="result" [class.ok]="r === 'Conforme'" [class.fail]="r === 'Con falla'">
            <span class="stamp"><app-icon [name]="r === 'Con falla' ? 'x' : r === 'Conforme' ? 'check' : 'clock'" [size]="22" [stroke]="2.2" /></span>
            <div>
              <span class="label">Resultado derivado de las pruebas</span>
              <b>{{ r ?? 'Sin pruebas registradas' }}</b>
              <p class="small">
                @if (!c.fin) {
                  Vista previa: al finalizar, el lote pasará a <b>{{ r ?? '—' }}</b>{{ r === 'Con falla' ? ' y se generará una falla automáticamente' : '' }}.
                } @else if (r === 'Con falla') {
                  Se generó la falla
                  @for (f of store.fallasDe(c.loteId); track f.id) {<a class="id" [routerLink]="['/fallas', f.id]">{{ f.id }}</a> }
                } @else {
                  El lote quedó Conforme.
                }
              </p>
            </div>
          </div>
          </app-fold>
          <app-fold title="Pruebas" [summary]="c.pruebas.length + ' registrada(s)'">
            @if (!valores) {
              <p class="row" style="margin-bottom:.75rem"><app-priv /> <span class="small muted">Los valores detallados son privados para {{ store.nombreEmpresa(c.empresaId) }}. Solo se comparten los resultados.</span></p>
            }
            <div class="mini-cards">
              @for (p of c.pruebas; track p.id) {
                <div class="mini {{ p.resultado === 'Conforme' ? 'mini--ok' : 'mini--fail' }}">
                  <div class="mini-top">
                    <b>{{ p.criterio }}</b>
                    <span class="row" style="gap:.3rem">
                      <app-badge [text]="p.resultado" [tone]="p.resultado === 'Conforme' ? 'ok' : 'fail'" />
                    </span>
                  </div>
                  <dl class="facts">
                    <div><dt>Esperado</dt><dd class="num">@if (valores) {{{ p.valorEsperado }} {{ p.unidad }}} @else {<app-priv />}</dd></div>
                    <div><dt>Obtenido</dt><dd class="num">@if (valores) {{{ p.valorObtenido }} {{ p.unidad }}} @else {<app-priv />}</dd></div>
                    <div class="full"><dt>Observaciones</dt><dd class="small">@if (obs) {{{ p.observaciones || '—' }}} @else {<app-priv [always]="true" />}</dd></div>
                  </dl>
                </div>
              } @empty {
                <p class="muted small">Aún no hay pruebas registradas.</p>
              }
            </div>

            @if (gestiona && !c.fin && c.status === 'IN_PROGRESS' && !c.finishPending) {
              <div class="test-form" [appDraft]="draft">
              <h3 class="label" style="margin-bottom:.6rem">Registrar prueba</h3>
              <datalist id="quality-criteria">@for (value of criterios(); track value) { <option [value]="value"></option> }</datalist>
              <datalist id="quality-units">@for (value of unidades(); track value) { <option [value]="value"></option> }</datalist>
              <div class="form-grid">
                <label class="field"><span>Criterio</span><input class="input" [(ngModel)]="nueva.criterio" list="quality-criteria" placeholder="p. ej. Gramaje" /></label>
                <label class="field"><span>Valor esperado</span><input class="input" [(ngModel)]="nueva.valorEsperado" placeholder="180 ± 5" /></label>
                <label class="field"><span>Valor obtenido</span><input class="input" [(ngModel)]="nueva.valorObtenido" /></label>
                <label class="field"><span>Unidad</span><input class="input" [(ngModel)]="nueva.unidad" list="quality-units" placeholder="g/m²" /></label>
                <div class="field">
                  <span>Resultado</span>
                  <div class="seg">
                    <button type="button" [attr.aria-pressed]="nueva.resultado === 'Conforme'" (click)="nueva.resultado = 'Conforme'">Conforme</button>
                    <button type="button" [attr.aria-pressed]="nueva.resultado === 'Con falla'" (click)="nueva.resultado = 'Con falla'">Con falla</button>
                  </div>
                </div>
                <label class="field span-all"><span>Observaciones <small class="muted">(internas)</small></span><input class="input" [(ngModel)]="nueva.observaciones" /></label>
              </div>
              @if (error()) {<p class="err" role="alert" style="margin-top:.5rem">{{ error() }}</p>}
              <button class="btn btn--ghost" type="button" style="margin-top:.75rem" (click)="agregar(c.id)"><app-icon name="plus" [size]="16" /> Agregar prueba</button>
              </div>
            }
            @if (gestiona && !c.fin && c.status === 'IN_PROGRESS' && !c.finishPending) {
              <app-action icon="check" cls="btn--primary" [tipRight]="true" [reason]="c.pruebas.length ? null : 'Registra al menos una prueba antes de finalizar.'" (run)="confirmar.set(true)">Finalizar control</app-action>
            }
          </app-fold>

          </div>

          <div class="folds">
            <app-fold title="Datos generales" [summary]="c.tipo + ' / ' + store.nombreUsuario(c.administradorId)" [open]="true">
              <dl class="dl">
                <dt>Identificador</dt><dd class="id">{{ c.id }}</dd>
                <dt>Lote</dt><dd><app-lot-link [id]="c.loteId" /></dd>
                <dt>Estado del lote</dt><dd><app-lot-status [lote]="lote" [showAvail]="false" /></dd>
                <dt>Tipo</dt><dd>{{ c.tipo }}</dd>
                <dt>Responsable</dt><dd>{{ store.nombreUsuario(c.administradorId) }}</dd>
                <dt>Inicio</dt><dd class="num">{{ c.inicio | fecha }}</dd>
                <dt>Fin</dt><dd class="num">{{ c.fin | fecha }}</dd>
                <dt>Duración</dt><dd class="num">{{ store.duracionH(c.inicio, c.fin) | horas }}</dd>
              </dl>
            </app-fold>
            <app-fold title="Evidencias" [summary]="c.evidencias.length + ' foto(s)'" [open]="true">
              <app-evidence ownerType="control" [ownerId]="c.id" [items]="c.evidencias" [canAdd]="gestiona" (added)="addEv($event)" />
            </app-fold>
          </div>
        </div>
      </div>

      @if (confirmar()) {
        <app-dialog title="Finalizar control" [eyebrow]="store.referencia(c.id)" (closed)="confirmar.set(false)">
          <p>Resultado derivado: <b>{{ r }}</b> ({{ c.pruebas.length }} pruebas).</p>
          <ul class="small" style="margin:.6rem 0 0;padding-left:1.1rem">
            <li>El lote {{ c.loteId }} pasará a calidad <b>{{ r }}</b> y volverá a fase Disponible.</li>
            @if (r === 'Con falla') {
              @if (store.fallaAbiertaDe(c.loteId)) {
                <li>La falla existente sigue pendiente de una nueva decisión administrativa.</li>
              } @else {
                <li>Se generará una <b>falla</b> automáticamente y el lote quedará bloqueado.</li>
              }
            } @else if (store.fallaAbiertaDe(c.loteId)) {
              <li>La falla asociada pasará a <b>Resuelta</b> y se levantará el bloqueo.</li>
            }
          </ul>
          <ng-container footer>
            <button class="btn btn--ghost" type="button" (click)="confirmar.set(false)">Cancelar</button>
            <button class="btn" [class.btn--primary]="r === 'Conforme'" [class.btn--danger]="r === 'Con falla'" type="button" (click)="finalizar(c.id)">Finalizar como {{ r }}</button>
          </ng-container>
        </app-dialog>
      }
    } @else {
      <div class="page"><div class="empty"><strong>Control no encontrado</strong><a routerLink="/calidad">Volver</a></div></div>
    }
  `,
  styles: `
    .test-form { background: var(--canvas-2); padding: 1rem; margin-top: 1rem; }
    .mini-cards { gap: .75rem; }
    .result { display: flex; gap: 1rem; align-items: center; padding: 0.9rem 1.1rem; background: var(--canvas-2); border-left: 4px solid var(--kraft-2); }
    .result b { display: block; font-size: 1.4rem; font-weight: 800; }
    .result .stamp { display: grid; place-items: center; width: 2.6rem; height: 2.6rem; background: var(--kraft); color: var(--green-950); flex: none; }
    .result.ok { border-left-color: var(--green-800); }
    .result.ok .stamp { background: var(--green-800); color: var(--cream); }
    .result.fail { border-left-color: var(--carmine); background: var(--carmine-paper); }
    .result.fail .stamp { background: var(--carmine); color: var(--cream-50); }
  `,
})
export class ControlDetallePage {
  /** Motivo de cancelación en lenguaje de persona (FE-M7). */
  readonly cancelReason = cancelReasonText;
  readonly criterios = computed(() => [...new Set(this.store.misControles().flatMap(control => control.pruebas.map(test => test.criterio)))].sort());
  readonly unidades = computed(() => [...new Set(this.store.misControles().flatMap(control => control.pruebas.map(test => test.unidad)))].sort());
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  private readonly ui = inject(UiService);
  private readonly router = inject(Router);
  readonly pasos = PASOS_CONTROL;
  readonly id = input.required<string>();
  readonly control = computed(() => this.store.control(this.id()));
  readonly resultado = computed(() => (this.control() ? this.store.resultadoControl(this.control()!) : undefined));
  readonly confirmar = signal(false);
  readonly error = signal('');
  nueva = this.vacia();
  constructor() {
    effect(() => {
      const control = this.control();
      if (control) {
        this.store.requestEvidence('control', control.id);
        this.store.ensureQualityBatch(control.loteId);
      }
    });
  }

  private vacia() {
    return { criterio: '', valorEsperado: '', valorObtenido: '', unidad: '', resultado: 'Conforme' as ResultadoPrueba, observaciones: '' };
  }
  readonly draft = formDraft(() => 'quality/test/' + this.id(), () => ({ ...this.nueva }), value => { this.nueva = value; });
  async agregar(cid: string) {
    const n = this.nueva;
    if (!n.criterio.trim() || !n.valorEsperado.trim() || !n.valorObtenido.trim() || !n.unidad.trim())
      return this.error.set('Completa criterio, valores y unidad.');
    const result = await this.store.agregarPrueba(cid, { ...n, observaciones: n.observaciones || undefined });
    if (!result.ok) return this.error.set(result.error);
    await this.draft.clear();
    this.ui.toast('Prueba registrada');
    this.nueva = this.vacia();
    this.error.set('');
  }
  async finalizar(cid: string) {
    const c = this.control()!;
    const r = await this.store.finalizarControl(cid);
    this.confirmar.set(false);
    if (!r.ok) return this.ui.toast(r.error, true);
    if (this.store.control(cid)?.finishPending) { this.ui.toast('Finalización solicitada. Esperando confirmación de Producción.'); return; }
    this.ui.toast(r.id ? 'Control finalizado. Revisa la falla generada.' : 'Control finalizado. Consulta el resultado y descarga su reporte.');
    if (r.id) this.router.navigate(['/fallas', r.id]);
  }
  async addEv(f: File) {
    const r = await this.store.agregarEvidencia('control', this.id(), f);
    if (r.ok) this.ui.toast('Evidencia registrada. Consulta su estado de subida.');
  }

                                                                           
  readonly pdfError = signal('');
  readonly pdfBusy = signal(false);
  async pdf() {
    if (this.pdfBusy()) return;
    this.pdfBusy.set(true);
    this.pdfError.set('');
    try {
      await this.store.descargarReporte(this.id());
      this.ui.toast('Reporte PDF descargado');
    } catch (error) {
      // FE-M8: el fallo queda a la vista con un botón «Reintentar» (no solo en un aviso que se cierra).
      this.pdfError.set(errorMessage(error));
    } finally {
      this.pdfBusy.set(false);
    }
  }
}
