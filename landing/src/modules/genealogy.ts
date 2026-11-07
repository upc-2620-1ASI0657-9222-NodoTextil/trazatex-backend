import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { $, $$, reduceMotion } from './env';
const SVG_NS = 'http://www.w3.org/2000/svg';
type Kind = 'block' | 'lote' | 'proceso' | 'control';
interface GNode {
    id: string;
    kind: Kind;
    title: string;
    facts: [
        string,
        string
    ][];
    fail?: boolean;
}
const NODES: Record<string, GNode> = {
    block: { id: 'block', kind: 'block', title: 'Bloque genealógico 1', facts: [['Origen', 'AL-0417, FA-0108']] },
    hi: {
        id: 'hi',
        kind: 'lote',
        title: 'Lote HI-1180',
        facts: [['Etapa', 'Hilo'], ['Empresa', 'Empresa A'], ['Cantidad', '820 kg'], ['Estado', 'Recibido']],
    },
    tejido: {
        id: 'tejido',
        kind: 'proceso',
        title: 'Proceso: Tejido',
        facts: [['Etapa', 'Hilo → Tela'], ['Empresa', 'Empresa A'], ['Cantidad', '790 kg'], ['Estado', 'Completado']],
    },
    te1: {
        id: 'te1',
        kind: 'lote',
        title: 'Lote TE-2034',
        facts: [['Etapa', 'Tela'], ['Cantidad', '410 kg'], ['Estado', 'Trasladado']],
    },
    te2: {
        id: 'te2',
        kind: 'lote',
        title: 'Lote TE-2035',
        facts: [['Etapa', 'Tela'], ['Cantidad', '380 kg'], ['Estado', 'Retenido']],
    },
    c1: { id: 'c1', kind: 'control', title: 'Control', facts: [['Resultado', 'Aprobado'], ['Lote', 'TE-2034']] },
    c2: { id: 'c2', kind: 'control', title: 'Control', facts: [['Resultado', 'Falla confirmada'], ['Lote', 'TE-2035']], fail: true },
    confeccion: {
        id: 'confeccion',
        kind: 'proceso',
        title: 'Proceso: Confección',
        facts: [['Etapa', 'Tela → Prenda'], ['Empresa', 'Empresa B'], ['Entrada', 'TE-2034'], ['Estado', 'Completado']],
    },
    pr1: {
        id: 'pr1',
        kind: 'lote',
        title: 'Lote PR-2291',
        facts: [['Etapa', 'Prenda'], ['Cantidad', '1 300 u'], ['Estado', 'Despachado']],
    },
    pr2: {
        id: 'pr2',
        kind: 'lote',
        title: 'Lote PR-2297',
        facts: [['Etapa', 'Prenda'], ['Cantidad', '420 u'], ['Estado', 'En planta']],
    },
};
const EDGES: [
    string,
    string
][] = [
    ['block', 'hi'],
    ['hi', 'tejido'],
    ['tejido', 'te1'],
    ['tejido', 'te2'],
    ['te1', 'c1'],
    ['te2', 'c2'],
    ['c1', 'confeccion'],
    ['confeccion', 'pr1'],
    ['confeccion', 'pr2'],
];
const COMPANIES = [
    { name: 'Empresa A', role: 'Tejeduría', cols: [['hi'], ['tejido'], ['te1', 'te2'], ['c1', 'c2']] },
    { name: 'Empresa B', role: 'Confección', cols: [['confeccion'], ['pr1', 'pr2']] },
];
const reach = (id: string, dir: 0 | 1, out = new Set<string>()) => {
    for (const e of EDGES)
        if (e[dir] === id && !out.has(e[1 - dir])) {
            out.add(e[1 - dir]);
            reach(e[1 - dir], dir, out);
        }
    return out;
};
function nodeHtml(n: GNode) {
    const facts = n.facts.map(([k, v]) => `<div><dt>${k}</dt><dd>${v}</dd></div>`).join('');
    const subject = n.kind === 'proceso' ? 'el proceso' : n.kind === 'control' ? 'el control' : n.kind === 'block' ? 'el bloque' : 'el lote';
    return `
    <article class="gnode gnode--${n.kind}${n.fail ? ' is-fail' : ''}" data-node="${n.id}">
      <h3 class="gnode__head">${n.title}</h3>
      <div class="gnode__body">
        <dl class="gnode__facts">${facts}</dl>
        <button class="gnode__open" type="button" data-open="${n.id}" aria-label="Ver ${subject}: ${n.title}">
          <svg viewBox="0 0 24 24" aria-hidden="true"><use href="/brand/icons.svg#search" /></svg>
        </button>
      </div>
    </article>`;
}
export function initGenealogy() {
    const track = $('[data-gflow-track]');
    const readout = $('[data-gen-readout]');
    if (!track)
        return;
    track.innerHTML = `
    <div class="gflow__block">${nodeHtml(NODES.block)}</div>
    ${COMPANIES.map((c) => `
      <section class="gcompany" aria-label="${c.name}">
        <h3 class="gcompany__head">${c.name} <span>${c.role}</span></h3>
        <div class="gcompany__body">
          ${c.cols.map((col) => `<div class="gcol${col.length > 1 ? ' gcol--split' : ''}">${col.map((id) => nodeHtml(NODES[id])).join('')}</div>`).join('')}
        </div>
      </section>`).join('')}`;
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.classList.add('gflow__lines');
    svg.setAttribute('aria-hidden', 'true');
    svg.innerHTML = `<defs><marker id="garrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M1 1 L9 5 L1 9" fill="none" stroke="context-stroke" stroke-width="1.6" /></marker></defs>`;
    track.append(svg);
    const paths = EDGES.map(([a, b]) => {
        const p = document.createElementNS(SVG_NS, 'path');
        p.setAttribute('marker-end', 'url(#garrow)');
        p.dataset.from = a;
        p.dataset.to = b;
        svg.append(p);
        return p;
    });
    const el = (id: string) => $(`[data-node="${id}"]`, track)!;
    const layout = () => {
        const box = track.getBoundingClientRect();
        const scale = box.width / track.scrollWidth || 1;
        svg.setAttribute('viewBox', `0 0 ${track.scrollWidth} ${track.scrollHeight}`);
        svg.style.width = `${track.scrollWidth}px`;
        svg.style.height = `${track.scrollHeight}px`;
        for (const p of paths) {
            const a = el(p.dataset.from!).getBoundingClientRect();
            const b = el(p.dataset.to!).getBoundingClientRect();
            const x1 = Math.round((a.right - box.left) / scale);
            const y1 = Math.round((a.top + a.height / 2 - box.top) / scale);
            const x2 = Math.round((b.left - box.left) / scale) - 2;
            const y2 = Math.round((b.top + b.height / 2 - box.top) / scale);
            const mid = Math.round(x1 + Math.min(22, (x2 - x1) / 2));
            p.setAttribute('d', Math.abs(y1 - y2) < 2 ? `M${x1} ${y2} H${x2}` : `M${x1} ${y1} H${mid} V${y2} H${x2}`);
        }
    };
    new ResizeObserver(layout).observe(track);
    layout();
    let selected = 'te1';
    const show = (id: string) => {
        selected = id;
        const up = reach(id, 1);
        const down = reach(id, 0);
        for (const n of $$('[data-node]', track)) {
            const nid = n.dataset.node!;
            n.classList.toggle('is-selected', nid === id);
            n.classList.toggle('is-lit', up.has(nid) || down.has(nid));
            n.classList.toggle('is-dim', nid !== id && !up.has(nid) && !down.has(nid));
        }
        for (const p of paths) {
            const a = p.dataset.from!;
            const b = p.dataset.to!;
            const lit = ((up.has(a) || a === id) && (up.has(b) || b === id)) || ((down.has(a) || a === id) && (down.has(b) || b === id));
            p.classList.toggle('is-lit', lit);
        }
        const n = NODES[id];
        const name = (x: string) => NODES[x].title;
        if (readout)
            readout.innerHTML = `
      <dt>Registro</dt><dd>${n.title}</dd>
      ${n.facts.map(([k, v]) => `<dt>${k}</dt><dd>${v}</dd>`).join('')}
      <dt>Viene de</dt><dd>${up.size ? [...up].map(name).join(', ') : 'Inicio del bloque'}</dd>
      <dt>Se convirtió en</dt><dd>${down.size ? [...down].map(name).join(', ') : 'Sin registros derivados todavía'}</dd>`;
    };
    track.addEventListener('click', (e) => {
        const btn = (e.target as HTMLElement).closest<HTMLElement>('[data-open]');
        if (btn)
            show(btn.dataset.open!);
    });
    show(selected);
    if (reduceMotion)
        return;
    const parts = $$('.gflow__block, .gcompany', track);
    gsap.set(parts, { opacity: 0, x: 24 });
    gsap.set(svg, { opacity: 0 });
    ScrollTrigger.create({
        trigger: track,
        start: 'top 80%',
        once: true,
        onEnter: () => {
            gsap.to(parts, { opacity: 1, x: 0, duration: 0.8, ease: 'expo.out', stagger: 0.14, onComplete: () => gsap.set(parts, { clearProps: 'transform' }) });
            gsap.to(svg, { opacity: 1, duration: 0.5, delay: 0.45, onStart: layout });
        },
    });
    void selected;
}
