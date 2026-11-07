import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  Directive,
  inject,
  input,
  model,
  output,
  viewChild,
} from '@angular/core';
import { IconComponent } from './icon.component';
import { DialogFocusDirective } from './dialog-focus.directive';

                             
export function paginar<T>(list: T[], page: number, size: number): T[] {
  const pages = Math.max(1, Math.ceil(list.length / size));
  const p = Math.min(Math.max(0, page), pages - 1);
  return list.slice(p * size, (p + 1) * size);
}

export interface SubnavItem {
  id: string;
  label: string;
  count?: number;
}

                                                                       
/** Identificadores del patrón de pestañas: el mismo cálculo para la pestaña, su `aria-controls` y el `aria-labelledby` del panel. */
export const tabId = (panel: string, id: string) => 'tab-' + panel + '-' + id.replace(/[^A-Za-z0-9_-]/g, '-');
export const panelId = (panel: string) => 'panel-' + panel;

/**
 * Pestañas ARIA completas: cada pestaña tiene `id`, `aria-selected` y `aria-controls` (el panel `panelId(panel)`), solo la activa está en el
 * orden de tabulación y las flechas, Inicio y Fin mueven la selección. El panel lo marca {@link TabPanelDirective} con el mismo `panel`.
 */
@Component({
  selector: 'app-subnav',
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nav class="subnav" role="tablist" [attr.aria-label]="label()" (keydown)="navegar($event)">
    @for (i of items(); track i.id) {
      <button type="button" role="tab" [id]="tabId(panel(), i.id)" [attr.aria-selected]="active() === i.id"
        [attr.aria-controls]="panelId(panel())" [attr.tabindex]="active() === i.id ? 0 : -1" (click)="active.set(i.id)">
        {{ i.label }}
        @if (i.count !== undefined) {
          <span class="n">{{ i.count }}</span>
        }
      </button>
    }
  </nav>`,
})
export class SubnavComponent {
  readonly items = input.required<SubnavItem[]>();
  readonly active = model.required<string>();
  readonly label = input('Subnavegación');
  /** Nombre del panel que controlan estas pestañas; debe coincidir con `appTabPanel` del contenido. */
  readonly panel = input.required<string>();
  protected readonly tabId = tabId;
  protected readonly panelId = panelId;
  private readonly host = inject(ElementRef<HTMLElement>);

  navegar(event: KeyboardEvent) {
    const items = this.items();
    if (!items.length) return;
    const current = Math.max(0, items.findIndex(item => item.id === this.active()));
    const target = event.key === 'ArrowRight' ? (current + 1) % items.length
      : event.key === 'ArrowLeft' ? (current - 1 + items.length) % items.length
      : event.key === 'Home' ? 0
      : event.key === 'End' ? items.length - 1
      : -1;
    if (target < 0) return;
    event.preventDefault();
    this.active.set(items[target].id);
    (this.host.nativeElement as HTMLElement).querySelector<HTMLElement>('#' + tabId(this.panel(), items[target].id))?.focus();
  }
}

/** El contenido que gobiernan las pestañas de `app-subnav`: `role="tabpanel"` etiquetado por la pestaña activa. */
@Directive({
  selector: '[appTabPanel]',
  host: {
    role: 'tabpanel',
    class: 'tabpanel',
    '[id]': 'panelId(appTabPanel())',
    '[attr.aria-labelledby]': 'tabId(appTabPanel(), tabActive())',
  },
})
export class TabPanelDirective {
  readonly appTabPanel = input.required<string>();
  readonly tabActive = input.required<string>();
  protected readonly tabId = tabId;
  protected readonly panelId = panelId;
}

                                                       
@Component({
  selector: 'app-pager',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="pagerbar">
    <span class="range">{{ desde() }}–{{ hasta() }} <span class="muted">de {{ total() }}</span></span>
    @if (sizes().length > 1) {
      <label class="ps">
        <span>Mostrar</span>
        <select class="select" (change)="setSize(+$any($event.target).value)" aria-label="Elementos por página">
          @for (s of sizes(); track s) {
            <option [value]="s" [selected]="s === size()">{{ s }}</option>
          }
        </select>
      </label>
    }
    <span class="spacer"></span>
    <button class="btn btn--ghost btn--sm" type="button" [disabled]="actual() === 0" (click)="page.set(actual() - 1)" aria-label="Página anterior">
      <app-icon name="chevronLeft" [size]="14" />
    </button>
    <span class="pg">Página <b>{{ actual() + 1 }}</b> / {{ paginas() }}</span>
    <button class="btn btn--ghost btn--sm" type="button" [disabled]="actual() >= paginas() - 1" (click)="page.set(actual() + 1)" aria-label="Página siguiente">
      <app-icon name="chevronRight" [size]="14" />
    </button>
  </div>`,
})
export class PagerComponent {
  readonly total = input.required<number>();
  readonly page = model(0);
  readonly size = model(6);
  readonly sizes = input<number[]>([6, 10, 20]);
  readonly paginas = computed(() => Math.max(1, Math.ceil(this.total() / this.size())));
  readonly actual = computed(() => Math.min(this.page(), this.paginas() - 1));
  readonly desde = computed(() => (this.total() ? this.actual() * this.size() + 1 : 0));
  readonly hasta = computed(() => Math.min(this.total(), (this.actual() + 1) * this.size()));
  setSize(s: number) {
    this.size.set(s);
    this.page.set(0);
  }
}

                                                                          
@Component({
  selector: 'app-fold',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="fold" [class.open]="open()">
    <button class="fold-head" type="button" (click)="open.set(!open())" [attr.aria-expanded]="open()">
      @if (n()) {<span class="fold-n">{{ n() }}</span>}
      <span class="fold-t">{{ title() }}</span>
      @if (summary()) {<span class="fold-s">{{ summary() }}</span>}
      <app-icon class="fold-i" name="chevronDown" [size]="16" />
    </button>
    @if (open()) {
      <div class="fold-body"><ng-content /></div>
    }
  </section>`,
})
export class FoldComponent {
  readonly title = input.required<string>();
  readonly n = input<string>();
  readonly summary = input<string>();
  readonly open = model(false);
}

                                                                    
@Component({
  selector: 'app-drawer',
  imports: [IconComponent, DialogFocusDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (modal()) {<div class="backdrop" (click)="closed.emit()"></div>}
    <aside class="drawer" [class.drawer--float]="!modal()" role="dialog" [attr.aria-modal]="modal()" [attr.aria-label]="title()" appDialogFocus (dialogEscape)="closed.emit()">
      <header>
        <div>
          @if (eyebrow()) {<span class="label">{{ eyebrow() }}</span>}
          <h2>{{ title() }}</h2>
          @if (sub()) {<p>{{ sub() }}</p>}
        </div>
        <button class="x" type="button" (click)="closed.emit()" aria-label="Cerrar"><app-icon name="x" /></button>
      </header>
      <div class="body"><ng-content /></div>
      <footer><ng-content select="[footer]" /></footer>
    </aside>`,
})
export class DrawerComponent {
  readonly title = input.required<string>();
  readonly eyebrow = input<string>();
  readonly sub = input<string>();
  readonly modal = input(true);
  readonly closed = output<void>();
}

                                                            
@Component({
  selector: 'app-stepper',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ol class="stepper">
    @for (s of steps(); track s; let i = $index) {
      <li [class.done]="i < current()" [class.on]="i === current()">
        <span class="k">@if (i < current()) {<app-icon name="check" [size]="12" [stroke]="2.4" />} @else {{{ i + 1 }}}</span>
        <span class="t">{{ s }}</span>
      </li>
    }
  </ol>`,
})
export class StepperComponent {
  readonly steps = input.required<string[]>();
  readonly current = input.required<number>();
}

                                                                                           
@Component({
  selector: 'app-carousel',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="carousel">
    <div class="carousel-track" #track><ng-content /></div>
    <div class="carousel-nav">
      <button class="btn btn--ghost btn--sm" type="button" (click)="mover(-1)" aria-label="Anterior"><app-icon name="chevronLeft" [size]="14" /></button>
      <button class="btn btn--ghost btn--sm" type="button" (click)="mover(1)" aria-label="Siguiente"><app-icon name="chevronRight" [size]="14" /></button>
    </div>
  </div>`,
})
export class CarouselComponent {
  private readonly track = viewChild.required<ElementRef<HTMLElement>>('track');
  mover(d: number) {
    const t = this.track().nativeElement;
    t.scrollBy({ left: d * t.clientWidth * 0.85, behavior: 'smooth' });
  }
}

                                                                            
@Component({
  selector: 'app-more-filters',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button class="btn btn--ghost more-f" type="button" (click)="open.emit()">
    <app-icon name="filter" [size]="15" /> Más filtros
    @if (activos()) {<span class="n">{{ activos() }}</span>}
  </button>`,
})
export class MoreFiltersComponent {
  readonly activos = input(0);
  readonly open = output<void>();
}

/** Marcador de posición mientras llegan los datos de una lista, para no mostrar un falso «Sin … en esta vista» (FE-M11). */
@Component({
  selector: 'app-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="skeleton" role="status" aria-busy="true" data-testid="skeleton">
    <span class="visually-hidden">{{ text() }}</span>
    @for (row of rows(); track row) { <div class="skeleton-row" aria-hidden="true"></div> }
  </div>`,
})
export class SkeletonComponent {
  readonly count = input(4);
  readonly text = input('Cargando…');
  protected readonly rows = computed(() => Array.from({ length: this.count() }, (_, i) => i));
}

export const DENSITY = [
  SkeletonComponent,
  SubnavComponent,
  PagerComponent,
  FoldComponent,
  DrawerComponent,
  StepperComponent,
  CarouselComponent,
  MoreFiltersComponent,
  TabPanelDirective,
] as const;
