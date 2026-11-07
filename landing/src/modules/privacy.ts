import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { $, $$, reduceMotion } from './env';
type Viewer = 'a' | 'b' | 'c';
const FIELDS: {
    label: string;
    value: string;
    visible: Viewer[];
}[] = [
    { label: 'Código', value: 'TE-2034', visible: ['a', 'b', 'c'] },
    { label: 'Composición', value: '85% algodón Pima, 15% alpaca', visible: ['a', 'b', 'c'] },
    { label: 'Cantidad', value: '410 kg', visible: ['a', 'b', 'c'] },
    { label: 'Lote de origen', value: 'HI-1180', visible: ['a', 'b', 'c'] },
    { label: 'Proveedor de fibra', value: 'Proveedor A, Ica', visible: ['a', 'c'] },
    { label: 'Costo por kg', value: 'S/ 31,40', visible: ['a'] },
    { label: 'Notas de calidad', value: 'Variación leve de tono en el rollo 7', visible: ['a', 'b'] },
];
const INTERACTIONS = [
    { when: '07 oct, 08:12', what: 'Transferencia iniciada', who: 'Empresa A → Empresa B' },
    { when: '07 oct, 10:40', what: 'Transferencia aceptada', who: 'Empresa B' },
    { when: '08 oct, 09:05', what: 'Transferencia enviada', who: 'Empresa B → Empresa C' },
    { when: '08 oct, 13:20', what: 'Transferencia recibida', who: 'Empresa C' },
    { when: '08 oct, 14:31', what: 'Transferencia registrada', who: 'Empresa C' },
];
export function initPrivacy() {
    const fields = $('[data-ficha-fields]');
    const audit = $('[data-audit]');
    const viewer = $('[data-viewer]');
    if (!fields || !viewer)
        return;
    fields.innerHTML = FIELDS.map((f) => `<div class="ficha__row">
      <dt>${f.label}</dt>
      <dd><span class="ficha__value">${f.value}</span><span class="weave"><span>Privado</span></span></dd>
    </div>`).join('');
    const rows = $$('.ficha__row', fields);
    if (audit)
        audit.innerHTML = INTERACTIONS.map((a) => `<li><time>${a.when}</time>${a.what}<span class="audit__who">${a.who}</span></li>`).join('');
    let timers: number[] = [];
    function apply(v: Viewer, stagger: boolean) {
        timers.forEach(clearTimeout);
        timers = [];
        let k = 0;
        rows.forEach((row, i) => {
            const open = FIELDS[i].visible.includes(v);
            const dd = row.querySelector('.ficha__value')!;
            const weave = row.querySelector('.weave')!;
            dd.setAttribute('aria-hidden', String(!open));
            weave.setAttribute('aria-hidden', String(open));
            if (row.classList.contains('is-open') === open)
                return;
            const delay = stagger && !reduceMotion ? k++ * 70 : 0;
            timers.push(window.setTimeout(() => row.classList.toggle('is-open', open), delay));
        });
    }
    viewer.addEventListener('change', (e) => {
        const v = (e.target as HTMLInputElement).value as Viewer;
        apply(v, true);
    });
    if (reduceMotion) {
        apply('b', false);
        return;
    }
    ScrollTrigger.create({
        trigger: fields,
        start: 'top 70%',
        once: true,
        onEnter: () => apply(($<HTMLInputElement>('input:checked', viewer)?.value as Viewer) ?? 'b', true),
    });
    if (audit) {
        const items = $$('li', audit);
        gsap.set(audit, { '--seam': 0 });
        gsap.set(items, { opacity: 0, x: -10 });
        ScrollTrigger.create({
            trigger: audit,
            start: 'top 75%',
            once: true,
            onEnter: () => {
                gsap.to(audit, { '--seam': 1, duration: 1.1, ease: 'power2.inOut' });
                gsap.to(items, { opacity: 1, x: 0, duration: 0.6, ease: 'expo.out', stagger: 0.12, delay: 0.15 });
            },
        });
    }
}
