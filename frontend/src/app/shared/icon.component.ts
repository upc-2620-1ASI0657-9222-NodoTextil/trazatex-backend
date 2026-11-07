import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { inject } from '@angular/core';

                                                                            
const PATHS: Record<string, string> = {
  trace: '<path d="M12 8A3 3 0 1 1 12 2A3 3 0 0 1 12 8V12H5V16A3 3 0 1 0 5 22A3 3 0 0 0 5 16V12H19V16A3 3 0 1 1 19 22A3 3 0 0 1 19 16"/>',
  lot: '<path d="M5 5Q3 5 3 7V18Q3 20 5 20H19Q21 20 21 18V7Q21 5 19 5H5V9H16Q18 9 18 11Q18 13 16 13H8Q6 13 6 15Q6 17 8 17H19V20"/>',
  process: '<path d="M9 12H15L12 9L15 12L12 15L15 12V8Q15 6 17 6H20Q22 6 22 8V16Q22 18 20 18H17Q15 18 15 16V12H9V16Q9 18 7 18H4Q2 18 2 16V8Q2 6 4 6H7Q9 6 9 8V12"/>',
  quality: '<path d="M12 3L20 6V12Q20 18 12 21Q4 18 4 12V6L12 3L12 7L8 12L11 15L17 9L20 6"/>',
  failure: '<path d="M12 3Q13 3 14 5L21 18Q22 20 20 20H4Q2 20 3 18L10 5Q11 3 12 3V12L10 14H14L12 18V20"/>',
  transfer: '<path d="M4 7H20L16 3L20 7L16 11L20 7Q22 7 22 10V14Q22 17 19 17H4L8 13L4 17L8 21L4 17Q2 17 2 14V10Q2 7 4 7"/>',
  machine: '<path d="M6 20H4Q3 20 3 18V8Q3 6 5 6H19Q21 6 21 8V18Q21 20 19 20H6V22V20H18V22V20H12V6H7V3H17V6H12V13H9A3 3 0 1 1 6 10A3 3 0 0 1 9 13H12H18V16H12"/>',
  analytics: '<path d="M3 3V20Q3 21 4 21H21H18V5Q18 3 16 3Q14 3 14 5V21H11V10Q11 8 9 8Q7 8 7 10V21H4"/>',
  company: '<path d="M3 20V11L9 7V11L15 7V11H21V20H3H9V15H15V20H21V4H17V11"/>',
  users: '<path d="M2 21Q2 15 8 15Q14 15 14 21H2L8 15V12A4 4 0 1 1 8 4A4 4 0 0 1 8 12V15Q11 13 15 13V11A3.5 3.5 0 1 0 15 4V11L15 13Q22 13 22 20H17"/>',
  privacy: '<path d="M8 10V7A4 4 0 0 1 16 7V10H19Q20 10 20 11V20Q20 21 19 21H5Q4 21 4 20V11Q4 10 5 10H8H12V15A1.5 1.5 0 1 1 12 18V15"/>',
  license: '<path d="M15 17H5Q3 17 3 15V5Q3 3 5 3H19Q21 3 21 5V15Q21 17 19 17H15A3 3 0 1 1 18 14A3 3 0 0 1 15 17V22L18 20L21 22V17L18 14V11H7V7H17"/>',
  bell: '<path d="M10 20Q12 23 14 20H20L18 17V10Q18 5 13 4V3Q13 2 12 2Q11 2 11 3V4Q6 5 6 10V17L4 20H10"/>',
  user: '<path d="M4 21Q4 15 12 15Q20 15 20 21H4L12 15V12A4.5 4.5 0 1 1 12 3A4.5 4.5 0 0 1 12 12V15"/>',
  logout: '<path d="M14 5H6Q4 5 4 7V17Q4 19 6 19H14V12H22L18 8L22 12L18 16"/>',
  search: '<path d="M15 15L21 21L15 15A7 7 0 1 1 5 5A7 7 0 0 1 15 15"/>',
  plus: '<path d="M12 4V20V12H4H20"/>',
  x: '<path d="M5 5L19 19L12 12L19 5L5 19"/>',
  chevronRight: '<path d="M9 5L15 11Q16 12 15 13L9 19"/>',
  chevronLeft: '<path d="M15 5L9 11Q8 12 9 13L15 19"/>',
  chevronDown: '<path d="M5 9L11 15Q12 16 13 15L19 9"/>',
  arrowLeft: '<path d="M20 12H4L10 6L4 12L10 18"/>',
  arrowRight: '<path d="M4 12H20L14 6L20 12L14 18"/>',
  split: '<path d="M3 12H8Q10 12 12 9L14 6H21L18 3L21 6L18 9L21 6H14L12 9Q10 12 8 12Q10 12 12 15L14 18H21L18 15L21 18L18 21"/>',
  qr: '<path d="M9 3H3V9H9V3H15H21V9H15V3L12 12H21V15H17V21H21V18H12L9 15H3V21H9V15L12 12L9 9"/>',
  lock: '<path d="M8 11V7A4 4 0 0 1 16 7V11H18Q20 11 20 13V19Q20 21 18 21H6Q4 21 4 19V13Q4 11 6 11H8H12V16A1.5 1.5 0 1 1 12 19V16"/>',
  camera: '<path d="M8 7L10 4H14L16 7H20Q22 7 22 9V19Q22 21 20 21H4Q2 21 2 19V9Q2 7 4 7H8L12 9A4 4 0 1 1 8 13A4 4 0 0 1 12 9"/>',
  check: '<path d="M4 12L8 16Q9 17 10 16L20 6"/>',
  filter: '<path d="M3 4H21L14 13V19L10 21V13L3 4"/>',
  menu: '<path d="M3 6H21Q23 6 23 9Q23 12 21 12H3Q1 12 1 15Q1 18 3 18H21"/>',
  download: '<path d="M12 3V15L7 10L12 15L17 10L12 15V20H4V17V20H20V17"/>',
  eye: '<path d="M2 12Q6 5 12 5Q18 5 22 12Q18 19 12 19Q6 19 2 12L9 12A3 3 0 1 1 12 15A3 3 0 0 1 9 12"/>',
  eyeOff: '<path d="M3 3L21 21L12 12A3 3 0 1 0 9 12H2Q6 5 12 5Q18 5 22 12Q18 19 12 19Q6 19 2 12"/>',
  layers: '<path d="M3 8L12 3L21 8L12 13L3 8V13L12 18L21 13V17L12 22L3 17"/>',
  stamp: '<path d="M9 3H15V8Q15 10 18 12V15H6V12Q9 10 9 8V3L12 3V19H20V22H4V19H12"/>',
  clock: '<path d="M12 3A9 9 0 1 1 3 12A9 9 0 0 1 12 3V12L16 15"/>',
  edit: '<path d="M4 20L5 15L16 4Q17 3 18 4L20 6Q21 7 20 8L9 19L4 20L5 15L9 19L17 11L13 7"/>',
  power: '<path d="M12 3V12L12 3Q4 3 4 12A8 8 0 1 0 20 12Q20 6 16 5"/>',
  inbox: '<path d="M3 13L6 4H18L21 13V20H3V13H8L10 16H14L16 13H21"/>',
  route: '<path d="M5 8A2 2 0 1 1 5 4A2 2 0 0 1 5 8V6H16Q20 6 20 10Q20 13 16 13H9Q5 13 5 17Q5 20 9 20H19A2 2 0 1 1 21 18A2 2 0 0 1 19 20"/>',
  scissors: '<path d="M8 8A3 3 0 1 1 5 3A3 3 0 0 1 8 8L20 19L12 12L8 16A3 3 0 1 1 5 15A3 3 0 0 1 8 16L20 5"/>',
};

const LOGIN_PATHS: Record<string, string> = {
  process: '<rect x="3" y="9" width="6" height="6"/><rect x="15" y="9" width="6" height="6"/><path d="M9 12h6M12.5 9.5 15 12l-2.5 2.5"/><path d="M6 9V5h12v4" stroke-dasharray="2 2"/>',
  company: '<path d="M3 21V9l6 3V9l6 3V4h6v17z"/><path d="M7 16.5h2M12 16.5h2M17 8h1M17 12h1M17 16.5h1"/>',
  license: '<rect x="3" y="4" width="18" height="13"/><path d="M7 8.5h10M7 12h6"/><circle cx="16.5" cy="17.5" r="2.5"/><path d="m15 19.5-1 2.5M18 19.5l1 2.5"/>',
};

@Component({
  selector: 'app-icon',
  template: `<svg
    [attr.width]="size()"
    [attr.height]="size()"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    [attr.stroke-width]="stroke()"
    [attr.stroke-linecap]="legacy() ? 'square' : 'round'"
    [attr.stroke-linejoin]="legacy() ? 'miter' : 'round'"
    aria-hidden="true"
    [innerHTML]="svg()"
  ></svg>`,
  styles: `:host{display:inline-flex;line-height:0}`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconComponent {
  private readonly san = inject(DomSanitizer);
  readonly name = input.required<string>();
  readonly size = input(18);
  readonly stroke = input(1.6);
  readonly legacy = input(false);
  readonly svg = computed(() => this.san.bypassSecurityTrustHtml((this.legacy() ? LOGIN_PATHS : PATHS)[this.name()] ?? ''));
}
