import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { $$, reduceMotion } from './env';
type Tag = {
    code: string;
    x: number;
    y: number;
    out?: boolean;
};
const TAG_W = 78;
const TAG_H = 30;
const DIAGRAMS: Record<string, {
    tags: Tag[];
    links: [
        number,
        number
    ][];
}> = {
    transformar: {
        tags: [
            { code: 'AL-0417', x: 0, y: 45 },
            { code: 'HI-1182', x: 242, y: 45, out: true },
        ],
        links: [[0, 1]],
    },
    dividir: {
        tags: [
            { code: 'HI-1180', x: 0, y: 45 },
            { code: 'TE-2034', x: 242, y: 4, out: true },
            { code: 'TE-2035', x: 242, y: 86, out: true },
        ],
        links: [[0, 1], [0, 2]],
    },
    mezclar: {
        tags: [
            { code: 'AL-0417', x: 0, y: 4 },
            { code: 'FA-0108', x: 0, y: 86 },
            { code: 'HI-1180', x: 242, y: 45, out: true },
        ],
        links: [[0, 2], [1, 2]],
    },
};
const tagShape = (x: number, y: number) => {
    const n = 6;
    return `M${x + n} ${y}H${x + TAG_W - n}L${x + TAG_W} ${y + n}V${y + TAG_H}H${x}V${y + n}Z`;
};
export function initOps() {
    for (const host of $$('[data-op-diagram]')) {
        const spec = DIAGRAMS[host.dataset.opDiagram ?? ''];
        if (!spec)
            continue;
        const links = spec.links
            .map(([a, b]) => {
            const A = spec.tags[a];
            const B = spec.tags[b];
            const x1 = A.x + TAG_W;
            const y1 = A.y + TAG_H / 2;
            const x2 = B.x;
            const y2 = B.y + TAG_H / 2;
            const dx = (x2 - x1) * 0.5;
            return `<path class="flow" d="M${x1} ${y1}C${x1 + dx} ${y1} ${x2 - dx} ${y2} ${x2} ${y2}"/>`;
        })
            .join('');
        const tags = spec.tags
            .map((t) => `<g class="op-tag${t.out ? ' op-tag--out' : ''}">
          <path d="${tagShape(t.x, t.y)}" class="op-tag__shape"/>
          <rect x="${t.x}" y="${t.y}" width="${TAG_W}" height="${TAG_H}" opacity="0"/>
          <text x="${t.x + TAG_W / 2}" y="${t.y + TAG_H / 2 + 4}" text-anchor="middle">${t.code}</text>
        </g>`)
            .join('');
        host.innerHTML = `<svg viewBox="-2 0 324 120" role="presentation">${links}${tags}</svg>`;
    }
    if (reduceMotion)
        return;
    for (const row of $$('.op')) {
        const paths = $$<SVGPathElement>('path.flow', row);
        const tags = $$<SVGGElement>('.op-tag', row);
        const verb = row.querySelector('.op__verb');
        const text = row.querySelector('.op__text');
        paths.forEach((p) => {
            const len = p.getTotalLength();
            gsap.set(p, { strokeDasharray: len, strokeDashoffset: len });
        });
        gsap.set(tags, { opacity: 0, y: 6 });
        gsap.set([verb, text], { opacity: 0, y: 24 });
        ScrollTrigger.create({
            trigger: row,
            start: 'top 78%',
            once: true,
            onEnter: () => {
                const tl = gsap.timeline({ defaults: { ease: 'expo.out' } });
                tl.to(verb, { opacity: 1, y: 0, duration: 0.9 })
                    .to(tags.filter((t) => !t.classList.contains('op-tag--out')), { opacity: 1, y: 0, duration: 0.5, stagger: 0.06 }, 0.15)
                    .to(paths, { strokeDashoffset: 0, duration: 0.7, ease: 'power2.inOut', stagger: 0.08 }, 0.35)
                    .to(tags.filter((t) => t.classList.contains('op-tag--out')), { opacity: 1, y: 0, duration: 0.5, stagger: 0.06 }, 0.8)
                    .to(text, { opacity: 1, y: 0, duration: 0.8 }, 0.3)
                    .add(() => gsap.set(paths, { clearProps: 'strokeDasharray,strokeDashoffset' }));
            },
        });
    }
}
