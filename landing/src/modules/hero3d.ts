import { BufferAttribute, BufferGeometry, CanvasTexture, CatmullRomCurve3, Color, DirectionalLight, DoubleSide, Group, HemisphereLight, LatheGeometry, Material, Mesh, MeshPhysicalMaterial, MeshStandardMaterial, MirroredRepeatWrapping, NeutralToneMapping, PerspectiveCamera, PlaneGeometry, Quaternion, RepeatWrapping, Scene, SRGBColorSpace, Texture, TextureLoader, TubeGeometry, Vector2, Vector3, WebGLRenderer, } from 'three';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
const FOREST = new Color('#1d4a33');
const FRINGE = new Color('#5f8467');
const CHARCOAL = '#25292a';
const KRAFT_EDGE = new Color('#c8a979');
const AXIS = new Vector3(1, 0.82, 0.22).normalize();
const NOISE = `
  float h31(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
  float vnoise(vec3 x) {
    vec3 i = floor(x); vec3 f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1,0,0)), f.x), mix(h31(i + vec3(0,1,0)), h31(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(h31(i + vec3(0,0,1)), h31(i + vec3(1,0,1)), f.x), mix(h31(i + vec3(0,1,1)), h31(i + vec3(1,1,1)), f.x), f.y), f.z);
  }
  float fibre(vec3 p) {
    vec3 q = vec3(p.x * 3.4, p.y * 26.0, p.z * 3.4);
    return vnoise(q) * 0.6 + vnoise(q * 2.3 + 7.1) * 0.4;
  }
`;
interface Dissolve {
    front: {
        value: number;
    };
    incoming: {
        value: number;
    };
    fringe: {
        value: Color;
    };
    soft: {
        value: number;
    };
}
const SWEEP = new Vector3(0.55, 1, 0.3).normalize();
function dissolvable(mat: MeshStandardMaterial, key: string): Dissolve {
    const d: Dissolve = { front: { value: 1 }, incoming: { value: 1 }, fringe: { value: FRINGE }, soft: { value: 0.028 } };
    mat.onBeforeCompile = (s) => {
        s.uniforms.uFront = d.front;
        s.uniforms.uIncoming = d.incoming;
        s.uniforms.uFringe = d.fringe;
        s.uniforms.uSoft = d.soft;
        s.uniforms.uSweep = { value: SWEEP };
        s.vertexShader = s.vertexShader
            .replace('#include <common>', '#include <common>\nvarying vec3 vRel;')
            .replace('#include <worldpos_vertex>', '#include <worldpos_vertex>\nvRel = ((modelMatrix * vec4(transformed, 1.0)).xyz - modelMatrix[3].xyz) / length(modelMatrix[0].xyz);');
        s.fragmentShader = s.fragmentShader
            .replace('#include <common>', `#include <common>
        varying vec3 vRel;
        uniform float uFront; uniform float uIncoming; uniform vec3 uFringe; uniform float uSoft; uniform vec3 uSweep;
        ${NOISE}`)
            .replace('#include <clipping_planes_fragment>', `#include <clipping_planes_fragment>
        vec3 rel = vRel;
        float lane = clamp(0.5 + dot(rel, uSweep) * 0.3, 0.0, 1.0);
        float n = lane * 0.72 + fibre(rel) * 0.28;
        float th = uFront * 1.16 - 0.08;
        float gap = uIncoming > 0.5 ? th - n : n - th;
        if (gap < 0.0) discard;
        float fringe = 1.0 - smoothstep(0.0, uSoft, gap);
        fringe *= step(0.002, uFront) * step(uFront, 0.998);`)
            .replace('#include <color_fragment>', '#include <color_fragment>\ndiffuseColor.rgb = mix(diffuseColor.rgb, uFringe, fringe * 0.6);');
    };
    mat.customProgramCacheKey = () => `tt-dissolve-${key}`;
    return d;
}
function matte(opts: ConstructorParameters<typeof MeshStandardMaterial>[0]) {
    return new MeshStandardMaterial({ roughness: 1, metalness: 0, ...opts });
}
function knit(opts: ConstructorParameters<typeof MeshPhysicalMaterial>[0]) {
    return new MeshPhysicalMaterial({
        roughness: 0.92,
        metalness: 0,
        sheen: 1,
        sheenRoughness: 0.5,
        sheenColor: new Color('#5f625e'),
        ...opts,
    });
}
function woundThreadTexture() {
    const w = 512;
    const h = 1024;
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    const g = c.getContext('2d')!;
    g.fillStyle = '#2b2f2d';
    g.fillRect(0, 0, w, h);
    let seed = 7;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    for (let y = -8; y < h + 8; y += 2.6) {
        const v = 34 + rnd() * 34;
        g.strokeStyle = `rgb(${v},${v + 3},${v + 1})`;
        g.lineWidth = 1.6 + rnd() * 1.2;
        g.beginPath();
        g.moveTo(0, y);
        g.lineTo(w, y + 5.2);
        g.stroke();
    }
    g.globalAlpha = 0.3;
    for (let y = -8; y < h + 8; y += 7.8) {
        g.strokeStyle = '#c8a979';
        g.lineWidth = 1;
        g.beginPath();
        g.moveTo(0, y + 5.2);
        g.lineTo(w, y);
        g.stroke();
    }
    const t = new CanvasTexture(c);
    t.colorSpace = SRGBColorSpace;
    t.wrapS = RepeatWrapping;
    t.repeat.set(3, 1);
    return t;
}
function medallion(g: CanvasRenderingContext2D, x: number, y: number, R: number) {
    g.save();
    g.translate(x, y);
    g.strokeStyle = 'rgba(200,169,121,0.4)';
    g.fillStyle = 'rgba(200,169,121,0.34)';
    g.lineWidth = 2;
    for (const r of [R, R * 0.94]) {
        g.beginPath();
        g.arc(0, 0, r, 0, Math.PI * 2);
        g.stroke();
    }
    for (let i = 0; i < 48; i++) {
        const a = (i / 48) * Math.PI * 2;
        g.beginPath();
        g.arc(Math.cos(a) * R * 0.86, Math.sin(a) * R * 0.86, 2.2, 0, Math.PI * 2);
        g.fill();
    }
    for (let i = 0; i < 16; i++) {
        g.save();
        g.rotate((i / 16) * Math.PI * 2);
        g.beginPath();
        g.ellipse(0, -R * 0.6, R * 0.075, R * 0.2, 0, 0, Math.PI * 2);
        g.stroke();
        g.restore();
    }
    g.fillStyle = 'rgba(78,112,88,0.4)';
    for (let i = 0; i < 8; i++) {
        g.save();
        g.rotate((i / 8) * Math.PI * 2 + Math.PI / 8);
        g.beginPath();
        g.ellipse(0, -R * 0.3, R * 0.07, R * 0.15, 0, 0, Math.PI * 2);
        g.fill();
        g.restore();
    }
    g.beginPath();
    g.arc(0, 0, R * 0.12, 0, Math.PI * 2);
    g.stroke();
    g.fillStyle = 'rgba(200,169,121,0.4)';
    g.beginPath();
    g.arc(0, 0, R * 0.05, 0, Math.PI * 2);
    g.fill();
    g.restore();
}
function ornamentCanvas() {
    const S = 1024;
    const c = document.createElement('canvas');
    c.width = c.height = S;
    const g = c.getContext('2d')!;
    g.fillStyle = '#1b1f1d';
    g.fillRect(0, 0, S, S);
    let seed = 19;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    for (let x = 0; x < S; x += 4) {
        const v = 30 + rnd() * 12;
        g.fillStyle = `rgb(${v},${v + 4},${v + 2})`;
        g.fillRect(x, 0, 2.4, S);
    }
    g.fillStyle = 'rgba(0,0,0,0.28)';
    for (let y = 0; y < S; y += 4)
        g.fillRect(0, y, S, 1);
    for (const [x, y] of [[0, 0], [S, 0], [0, S], [S, S], [S / 2, S / 2]])
        medallion(g, x, y, S * 0.15);
    for (const [x, y] of [[S / 2, 0], [S / 2, S], [0, S / 2], [S, S / 2]]) {
        g.save();
        g.translate(x, y);
        g.fillStyle = 'rgba(200,169,121,0.3)';
        for (let i = 0; i < 6; i++) {
            g.rotate(Math.PI / 3);
            g.beginPath();
            g.ellipse(0, -S * 0.028, S * 0.01, S * 0.022, 0, 0, Math.PI * 2);
            g.fill();
        }
        g.restore();
    }
    return c;
}
interface Form {
    group: Group;
    dissolves: Dissolve[];
}
function makeForm(meshes: Array<[
    BufferGeometry,
    MeshStandardMaterial
]>, key: string): Form {
    const group = new Group();
    const dissolves: Dissolve[] = [];
    meshes.forEach(([geo, mat], i) => {
        dissolves.push(dissolvable(mat, `${key}-${i}`));
        group.add(new Mesh(geo, mat));
    });
    return { group, dissolves };
}
function spool(): Form {
    const thread = woundThreadTexture();
    const H = 1.62;
    const R = 0.58;
    const body: Vector2[] = [];
    for (let i = 0; i <= 40; i++) {
        const t = i / 40;
        body.push(new Vector2(R + Math.sin(t * Math.PI) * 0.05, -H / 2 + 0.1 + t * (H - 0.2)));
    }
    const bodyGeo = new LatheGeometry(body, 128);
    const bodyMat = knit({ color: '#ffffff', map: thread, bumpMap: thread, bumpScale: 2.6 });
    const flange = (y: number, dir: 1 | -1) => {
        const t = 0.1;
        const pts = [
            [0.15, y + dir * t],
            [0.15, y],
            [0.8, y],
            [0.84, y + dir * 0.03],
            [0.84, y + dir * (t - 0.03)],
            [0.8, y + dir * t],
            [0.15, y + dir * t],
        ].map(([x, yy]) => new Vector2(x, yy));
        if (dir === 1)
            pts.reverse();
        return new LatheGeometry(pts, 96);
    };
    const flangeGeo = mergeGeometries([flange(-H / 2 - 0.02, 1), flange(H / 2 + 0.02, -1)])!;
    const flangeMat = matte({ color: '#5b4129', roughness: 0.62 });
    const tail = new CatmullRomCurve3([
        new Vector3(R + 0.04, 0.32, 0.0),
        new Vector3(R + 0.22, 0.1, 0.18),
        new Vector3(R + 0.32, -0.35, 0.08),
        new Vector3(R + 0.18, -0.8, 0.3),
        new Vector3(R + 0.42, -1.18, 0.22),
    ]);
    const tailGeo = new TubeGeometry(tail, 80, 0.014, 6, false);
    const tailMat = matte({ color: CHARCOAL });
    const form = makeForm([
        [bodyGeo, bodyMat],
        [flangeGeo, flangeMat],
        [tailGeo, tailMat],
    ], 'spool');
    form.group.scale.setScalar(1.18);
    return form;
}
function fibre(palette = ['#1d4a33', '#24563c', '#173f2b', '#2b6044', '#1a432e'], key = 'fibre'): Form {
    const core = new CatmullRomCurve3([
        new Vector3(-1.45, -0.95, 0.05),
        new Vector3(-0.9, -0.35, 0.35),
        new Vector3(-0.35, 0.45, 0.12),
        new Vector3(0.35, 0.92, -0.22),
        new Vector3(1.02, 0.6, -0.05),
        new Vector3(1.0, -0.05, 0.3),
        new Vector3(0.4, -0.18, 0.28),
        new Vector3(0.28, -0.72, -0.08),
        new Vector3(0.92, -1.05, -0.12),
        new Vector3(1.5, -0.98, 0.05),
    ], false, 'centripetal');
    const SAMPLES = 260;
    const frames = core.computeFrenetFrames(SAMPLES, false);
    const pts = core.getSpacedPoints(SAMPLES);
    let seed = 11;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    const tones = palette.map((c) => new Color(c));
    const strands: BufferGeometry[] = [];
    const STRANDS = 46;
    for (let s = 0; s < STRANDS; s++) {
        const a0 = rnd() * Math.PI * 2;
        const r0 = Math.sqrt(rnd()) * 0.075;
        const fray = 0.6 + rnd() * 0.8;
        const wob = rnd() * 10;
        const path: Vector3[] = [];
        for (let i = 0; i <= SAMPLES; i++) {
            const t = i / SAMPLES;
            const ends = Math.pow(Math.max(0, 1 - t / 0.16), 2) + Math.pow(Math.max(0, (t - 0.84) / 0.16), 2);
            const r = r0 * (1 + ends * 7 * fray);
            const a = a0 + t * Math.PI * 14 + Math.sin(t * 9 + wob) * 0.3;
            const n = frames.normals[i];
            const b = frames.binormals[i];
            path.push(pts[i]
                .clone()
                .addScaledVector(n, Math.cos(a) * r)
                .addScaledVector(b, Math.sin(a) * r));
        }
        const geo = new TubeGeometry(new CatmullRomCurve3(path), 220, 0.0075 + rnd() * 0.004, 5, false);
        const tone = tones[s % tones.length];
        const col = new Float32Array(geo.attributes.position.count * 3);
        for (let i = 0; i < col.length; i += 3) {
            col[i] = tone.r;
            col[i + 1] = tone.g;
            col[i + 2] = tone.b;
        }
        geo.setAttribute('color', new BufferAttribute(col, 3));
        strands.push(geo);
    }
    const geo = mergeGeometries(strands)!;
    const mat = matte({ vertexColors: true });
    const form = makeForm([[geo, mat]], key);
    form.group.scale.setScalar(1.05);
    return form;
}
function cloth(fabric: Texture, opts: {
    repeat?: number;
    fringe?: string;
    key?: string;
    jersey?: boolean;
} = {}): Form {
    const W = 2.3;
    const H = 2.5;
    const geo = new PlaneGeometry(W, H, 150, 160);
    const p = geo.attributes.position;
    const fold = (x: number, y: number) => 0.17 * Math.sin(1.7 * x + 0.5 * y + 0.4) +
        0.08 * Math.sin(3.1 * y - 0.6 * x) +
        0.05 * Math.sin(5.3 * x + 2.2 * y) +
        0.55 * Math.pow(Math.max(0, (x / W + 0.5) * (0.5 - y / H) - 0.25), 2) * 6;
    for (let i = 0; i < p.count; i++) {
        const x = p.getX(i);
        const y = p.getY(i);
        p.setZ(i, fold(x, y));
    }
    geo.computeVertexNormals();
    const map = fabric.clone();
    map.wrapS = map.wrapT = MirroredRepeatWrapping;
    map.repeat.set(opts.repeat ?? 1.4, (opts.repeat ?? 1.4) * 1.07);
    map.needsUpdate = true;
    const mat = opts.jersey
        ? knit({ color: '#e2ebe8', map, bumpMap: map, bumpScale: 0.9, side: DoubleSide })
        : matte({ color: '#f1f4ef', map, bumpMap: map, bumpScale: 1.6, side: DoubleSide });
    let seed = 3;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    const threads: BufferGeometry[] = [];
    for (let i = 0; i < 38; i++) {
        const x = -W / 2 + 0.04 + (i / 37) * (W - 0.08) + (rnd() - 0.5) * 0.02;
        const y = -H / 2;
        const len = 0.12 + rnd() * 0.16;
        const z0 = fold(x, y);
        const c = new CatmullRomCurve3([
            new Vector3(x, y + 0.01, z0),
            new Vector3(x + (rnd() - 0.5) * 0.04, y - len * 0.5, z0 + 0.02),
            new Vector3(x + (rnd() - 0.5) * 0.08, y - len, z0 + (rnd() - 0.3) * 0.08),
        ]);
        threads.push(new TubeGeometry(c, 10, 0.008, 4, false));
    }
    const fringeGeo = mergeGeometries(threads)!;
    const fringeMat = matte({ color: opts.fringe ?? FOREST });
    const form = makeForm([
        [geo, mat],
        [fringeGeo, fringeMat],
    ], opts.key ?? 'cloth');
    form.group.scale.setScalar(0.98);
    return form;
}
function scarf(fabric: Texture): Form {
    const W = 0.78;
    const L = 3.3;
    const NU = 24;
    const NT = 240;
    const centre = (t: number) => new Vector3(0.55 * Math.sin(Math.PI * t * 1.25 - 0.7), (0.5 - t) * L * 0.84, 0.28 * Math.sin(Math.PI * 2 * t));
    const across = (t: number) => {
        const phi = 1.05 * Math.sin(Math.PI * t * 1.4 + 0.2);
        return new Vector3(Math.cos(phi), 0.08 * Math.sin(phi), Math.sin(phi));
    };
    const pos: number[] = [];
    const uv: number[] = [];
    for (let j = 0; j <= NT; j++) {
        const t = j / NT;
        const c = centre(t);
        const a = across(t);
        for (let i = 0; i <= NU; i++) {
            const u = i / NU - 0.5;
            const ripple = 0.025 * Math.sin(t * 38 + u * 3) * (0.4 + Math.abs(u));
            const p = c.clone().addScaledVector(a, u * W);
            p.z += ripple;
            pos.push(p.x, p.y, p.z);
            uv.push(i / NU, 1 - t);
        }
    }
    const index: number[] = [];
    for (let j = 0; j < NT; j++)
        for (let i = 0; i < NU; i++) {
            const a = j * (NU + 1) + i;
            const b = a + 1;
            const c = a + NU + 1;
            const d = c + 1;
            index.push(a, c, b, b, c, d);
        }
    const geo = new BufferGeometry();
    geo.setAttribute('position', new BufferAttribute(new Float32Array(pos), 3));
    geo.setAttribute('uv', new BufferAttribute(new Float32Array(uv), 2));
    geo.setIndex(index);
    geo.computeVertexNormals();
    const map = fabric.clone();
    map.wrapS = map.wrapT = MirroredRepeatWrapping;
    map.repeat.set(0.5, (0.5 * L) / W);
    map.needsUpdate = true;
    const mat = matte({ color: '#ffffff', map, bumpMap: map, bumpScale: 1.4, side: DoubleSide });
    let seed = 5;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    const threads: BufferGeometry[] = [];
    for (const end of [0, 1]) {
        const c = centre(end);
        const a = across(end);
        const dir = centre(end === 0 ? 0 : 1)
            .sub(centre(end === 0 ? 0.02 : 0.98))
            .normalize();
        for (let i = 0; i < 16; i++) {
            const u = (i / 15 - 0.5) * W * 0.94;
            const base = c.clone().addScaledVector(a, u);
            const len = 0.16 + rnd() * 0.12;
            const tip = base.clone().addScaledVector(dir, len);
            tip.y -= 0.08;
            tip.x += (rnd() - 0.5) * 0.06;
            const mid = base.clone().lerp(tip, 0.5);
            mid.z += (rnd() - 0.5) * 0.04;
            threads.push(new TubeGeometry(new CatmullRomCurve3([base, mid, tip]), 10, 0.011, 4, false));
        }
    }
    const fringeGeo = mergeGeometries(threads)!;
    const fringeMat = matte({ color: '#6e4d31' });
    const form = makeForm([
        [geo, mat],
        [fringeGeo, fringeMat],
    ], 'scarf');
    form.group.scale.setScalar(1.02);
    return form;
}
function teeOutline(): Vector2[] {
    const half: Vector2[] = [];
    const q = (a: Vector2, c: Vector2, b: Vector2, n: number) => {
        for (let i = 1; i <= n; i++) {
            const t = i / n;
            const u = 1 - t;
            half.push(new Vector2(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y));
        }
    };
    const l = (b: Vector2, n: number) => {
        const a = half[half.length - 1];
        for (let i = 1; i <= n; i++)
            half.push(a.clone().lerp(b, i / n));
    };
    half.push(new Vector2(0, 0.9));
    q(new Vector2(0, 0.9), new Vector2(0.25, 0.9), new Vector2(0.3, 1.13), 24);
    q(new Vector2(0.3, 1.13), new Vector2(0.56, 1.1), new Vector2(0.76, 1.03), 16);
    q(new Vector2(0.76, 1.03), new Vector2(1.02, 0.86), new Vector2(1.22, 0.56), 18);
    l(new Vector2(0.98, 0.34), 10);
    q(new Vector2(0.98, 0.34), new Vector2(0.8, 0.5), new Vector2(0.71, 0.5), 10);
    q(new Vector2(0.71, 0.5), new Vector2(0.68, -0.3), new Vector2(0.75, -1.12), 30);
    q(new Vector2(0.75, -1.12), new Vector2(0.4, -1.18), new Vector2(0, -1.17), 18);
    const left = half
        .slice(1, -1)
        .reverse()
        .map((v) => new Vector2(-v.x, v.y));
    return [...half, ...left];
}
function inside(poly: Vector2[], x: number, y: number) {
    let c = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
        const a = poly[i];
        const b = poly[j];
        if (a.y > y !== b.y > y && x < ((b.x - a.x) * (y - a.y)) / (b.y - a.y) + a.x)
            c = !c;
    }
    return c;
}
function closest(poly: Vector2[], x: number, y: number) {
    let best = Infinity;
    let bx = 0;
    let by = 0;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
        const a = poly[j];
        const b = poly[i];
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const t = Math.max(0, Math.min(1, ((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy || 1)));
        const px = a.x + dx * t;
        const py = a.y + dy * t;
        const d = (px - x) ** 2 + (py - y) ** 2;
        if (d < best) {
            best = d;
            bx = px;
            by = py;
        }
    }
    return { x: bx, y: by, d: Math.sqrt(best) };
}
function garmentTexture(fabricImg: CanvasImageSource, outline: Vector2[], box: {
    x0: number;
    y0: number;
    w: number;
    h: number;
}) {
    const S = 1024;
    const c = document.createElement('canvas');
    c.width = c.height = S;
    const g = c.getContext('2d')!;
    for (let i = 0; i < 2; i++)
        for (let j = 0; j < 2; j++) {
            g.save();
            g.translate(i * (S / 2) + (i ? S / 2 : 0), j * (S / 2) + (j ? S / 2 : 0));
            g.scale(i ? -1 : 1, j ? -1 : 1);
            g.drawImage(fabricImg, 0, 0, S / 2, S / 2);
            g.restore();
        }
    const px = (v: Vector2) => [((v.x - box.x0) / box.w) * S, (1 - (v.y - box.y0) / box.h) * S] as const;
    const seam = (pts: Vector2[], dash = true) => {
        g.beginPath();
        pts.forEach((v, i) => (i ? g.lineTo(...px(v)) : g.moveTo(...px(v))));
        g.setLineDash(dash ? [7, 5] : []);
        g.stroke();
    };
    g.lineCap = 'round';
    const stitch = (pts: Vector2[], off: Vector2) => {
        g.strokeStyle = 'rgba(0,0,0,0.6)';
        g.lineWidth = 3;
        seam(pts, false);
        g.strokeStyle = 'rgba(200,169,121,0.8)';
        g.lineWidth = 1.6;
        seam(pts.map((v) => v.clone().add(off)));
        seam(pts.map((v) => v.clone().add(off.clone().multiplyScalar(2.1))));
    };
    const hemY = -1.1;
    stitch([new Vector2(-0.76, hemY), new Vector2(0.76, hemY)], new Vector2(0, 0.022));
    for (const s of [1, -1]) {
        stitch([new Vector2(s * 1.17, 0.52), new Vector2(s * 0.94, 0.31)].map((v) => v.clone()), new Vector2(-s * 0.018, 0.02));
        stitch([new Vector2(s * 0.3, 1.11), new Vector2(s * 0.74, 1.0)], new Vector2(0, -0.02));
        stitch([new Vector2(s * 0.76, 1.01), new Vector2(s * 0.74, 0.52)], new Vector2(-s * 0.018, 0));
    }
    const neck = outline.filter((v) => v.y > 0.88 && Math.abs(v.x) <= 0.31 && v.y < 1.14);
    neck.sort((a, b) => a.x - b.x);
    g.strokeStyle = 'rgba(0,0,0,0.55)';
    g.lineWidth = 18;
    g.setLineDash([]);
    g.beginPath();
    neck.forEach((v, i) => {
        const [x, y] = px(new Vector2(v.x * 1.04, v.y - 0.04));
        i ? g.lineTo(x, y) : g.moveTo(x, y);
    });
    g.stroke();
    const t = new CanvasTexture(c);
    t.colorSpace = SRGBColorSpace;
    return t;
}
function shirt(fabricImg: CanvasImageSource, fabric: Texture): Form {
    const outline = teeOutline();
    const box = { x0: -1.3, y0: -1.25, w: 2.6, h: 2.5 };
    const NX = 190;
    const NY = 184;
    const count = (NX + 1) * (NY + 1);
    const xs = new Float32Array(count);
    const ys = new Float32Array(count);
    const inn = new Uint8Array(count);
    const dist = new Float32Array(count);
    for (let j = 0; j <= NY; j++)
        for (let i = 0; i <= NX; i++) {
            const k = j * (NX + 1) + i;
            xs[k] = box.x0 + (i / NX) * box.w;
            ys[k] = box.y0 + (j / NY) * box.h;
            inn[k] = inside(outline, xs[k], ys[k]) ? 1 : 0;
        }
    const usable = new Uint8Array(count);
    for (let j = 0; j <= NY; j++)
        for (let i = 0; i <= NX; i++) {
            const k = j * (NX + 1) + i;
            if (inn[k]) {
                usable[k] = 1;
                dist[k] = closest(outline, xs[k], ys[k]).d;
                continue;
            }
            let near = false;
            for (let dj = -1; dj <= 1 && !near; dj++)
                for (let di = -1; di <= 1; di++) {
                    const ii = i + di;
                    const jj = j + dj;
                    if (ii < 0 || jj < 0 || ii > NX || jj > NY)
                        continue;
                    if (inn[jj * (NX + 1) + ii]) {
                        near = true;
                        break;
                    }
                }
            if (near) {
                const c = closest(outline, xs[k], ys[k]);
                xs[k] = c.x;
                ys[k] = c.y;
                usable[k] = 1;
                dist[k] = 0;
            }
        }
    const remap = new Int32Array(count).fill(-1);
    const pos: number[] = [];
    const uv: number[] = [];
    const hgt: number[] = [];
    for (let k = 0; k < count; k++) {
        if (!usable[k])
            continue;
        remap[k] = pos.length / 3;
        const x = xs[k];
        const y = ys[k];
        const ax = Math.abs(x);
        const torso = 0.27 * Math.sqrt(Math.max(0, 1 - (x / 0.86) ** 2)) * (1 - 0.25 * Math.max(0, y - 0.6));
        const sleeve = 0.085;
        const blend = Math.min(1, Math.max(0, (0.78 - ax) / 0.16));
        const T = Math.max(0.05, sleeve + (torso - sleeve) * blend);
        const s = Math.min(1, dist[k] / 0.2);
        const h = T * Math.sqrt(1 - (1 - s) ** 2);
        const drape = 0.03 * Math.sin(3.2 * x + 2.1 * y) + 0.025 * Math.sin(6.5 * y - 1.4 * x) * Math.max(0, -y) + 0.05 * -(x * x) * 0.6;
        pos.push(x, y, drape);
        hgt.push(h);
        uv.push((x - box.x0) / box.w, (y - box.y0) / box.h);
    }
    const index: number[] = [];
    for (let j = 0; j < NY; j++)
        for (let i = 0; i < NX; i++) {
            const a = j * (NX + 1) + i;
            const b = a + 1;
            const c = a + NX + 1;
            const d = c + 1;
            for (const [p, q, r] of [
                [a, b, d],
                [a, d, c],
            ]) {
                if (!usable[p] || !usable[q] || !usable[r])
                    continue;
                if (!inn[p] && !inn[q] && !inn[r])
                    continue;
                index.push(remap[p], remap[q], remap[r]);
            }
        }
    const sheet = (side: 1 | -1) => {
        const geo = new BufferGeometry();
        const P = new Float32Array(pos.length);
        for (let v = 0; v < pos.length / 3; v++) {
            P[v * 3] = pos[v * 3];
            P[v * 3 + 1] = pos[v * 3 + 1];
            P[v * 3 + 2] = pos[v * 3 + 2] + side * hgt[v];
        }
        geo.setAttribute('position', new BufferAttribute(P, 3));
        geo.setAttribute('uv', new BufferAttribute(new Float32Array(uv), 2));
        const idx = side === 1 ? index : index.map((_, i, arr) => arr[i - (i % 3) + (2 - (i % 3))]);
        geo.setIndex(idx);
        geo.computeVertexNormals();
        return geo;
    };
    const map = garmentTexture(fabricImg, outline, box);
    const bump = fabric.clone();
    bump.wrapS = bump.wrapT = MirroredRepeatWrapping;
    bump.repeat.set(2, 2);
    bump.needsUpdate = true;
    const front = knit({ color: '#e2ebe8', map, bumpMap: bump, bumpScale: 0.9 });
    const back = knit({ color: '#c2cbc8', map, bumpMap: bump, bumpScale: 0.9 });
    const neckPts = outline
        .filter((v) => v.y > 0.88 && v.y < 1.14 && Math.abs(v.x) <= 0.3)
        .sort((a, b) => a.x - b.x)
        .map((v) => new Vector3(v.x, v.y + 0.01, 0.02));
    const backNeck = neckPts
        .slice()
        .reverse()
        .map((v) => new Vector3(v.x, 1.13 - (1.13 - v.y) * 0.25, -0.02));
    const collar = new CatmullRomCurve3([...neckPts, ...backNeck.slice(1, -1)], true);
    const collarGeo = new TubeGeometry(collar, 120, 0.032, 10, true);
    const collarMat = knit({ color: '#151817', bumpMap: bump, bumpScale: 1.6 });
    const form = makeForm([
        [sheet(1), front],
        [sheet(-1), back],
        [collarGeo, collarMat],
    ], 'shirt');
    form.group.scale.setScalar(1.02);
    return form;
}
export interface HeroPose {
    stage: number;
    spin: number;
    x: number;
    y: number;
    scale: number;
    intro: number;
}
const smooth = (a: number, b: number, x: number) => {
    const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
    return t * t * (3 - 2 * t);
};
export class HeroScene {
    readonly renderer: WebGLRenderer;
    private scene = new Scene();
    private camera = new PerspectiveCamera(26, 1, 0.1, 60);
    private pivot = new Group();
    private forms: Form[] = [];
    private w = 1;
    private h = 1;
    readonly tilt = { x: 0, y: 0 };
    readonly ready: Promise<void>;
    private cyclic: boolean;
    private window: [
        number,
        number
    ];
    constructor(canvas: HTMLCanvasElement, textureUrl: string | null, kind: 'hero' | 'plaid' = 'hero') {
        this.window = kind === 'hero' ? [0.88, 0.12] : [0.72, 0.22];
        this.cyclic = kind === 'hero';
        this.renderer = new WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'high-performance' });
        this.renderer.setClearColor(0x000000, 0);
        this.renderer.outputColorSpace = SRGBColorSpace;
        this.renderer.toneMapping = NeutralToneMapping;
        this.renderer.toneMappingExposure = 1.02;
        this.camera.position.set(0, 0, 11);
        this.scene.add(new HemisphereLight('#fffaf0', '#a99f86', 1.35));
        const key = new DirectionalLight('#fff3e2', 3.1);
        key.position.set(-6, 4.5, 3.5);
        const rim = new DirectionalLight('#eef3e6', 1.1);
        rim.position.set(5, 1.5, -4);
        const fill = new DirectionalLight('#f4ecdc', 0.55);
        fill.position.set(2, -4, 4);
        this.scene.add(key, rim, fill, this.pivot);
        const texture: Promise<Texture> = kind === 'hero' || !textureUrl
            ? Promise.resolve(new CanvasTexture(ornamentCanvas()))
            : new TextureLoader().loadAsync(textureUrl);
        this.ready = texture.then((tex) => {
            tex.colorSpace = SRGBColorSpace;
            tex.anisotropy = Math.min(8, this.renderer.capabilities.getMaxAnisotropy());
            const img = tex.image as CanvasImageSource;
            this.forms =
                kind === 'hero'
                    ? [
                        fibre(['#e8dcc4', '#d9c6a1', '#cdb48a', '#efe5d2', '#bfa073'], 'alpaca'),
                        spool(),
                        cloth(tex, { repeat: 1.2, fringe: CHARCOAL, key: 'jersey', jersey: true }),
                        shirt(img, tex),
                    ]
                    : [
                        fibre(['#5b4129', '#8a6643', '#c8a979', '#e6d7b8', '#3e2c1c', '#a27a52'], 'plaid-yarn'),
                        cloth(tex, { repeat: 1.1, fringe: '#6e4d31', key: 'plaid-cloth' }),
                        scarf(tex),
                    ];
            if (kind === 'hero')
                for (const f of this.forms)
                    for (const d of f.dissolves) {
                        d.fringe.value = KRAFT_EDGE;
                        d.soft.value = 0.06;
                    }
            this.forms.forEach((f, k) => {
                f.group.quaternion.premultiply(new Quaternion().setFromAxisAngle(AXIS, -k * Math.PI));
                const holder = new Group();
                holder.add(f.group);
                this.pivot.add(holder);
            });
            this.renderer.compile(this.scene, this.camera);
        });
    }
    resize(w: number, h: number) {
        this.w = w;
        this.h = h;
        this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, w < 700 ? 2 : 1.75));
        this.renderer.setSize(w, h, false);
        this.camera.aspect = w / h;
        this.camera.updateProjectionMatrix();
    }
    private view() {
        const vh = 2 * Math.tan((this.camera.fov * Math.PI) / 360) * this.camera.position.z;
        return { vw: vh * this.camera.aspect, vh };
    }
    screen(pose: HeroPose) {
        const { vh } = this.view();
        const base = this.baseSize();
        return { x: pose.x * this.w, y: pose.y * this.h, r: ((base * pose.scale * 1.35) / vh) * this.h };
    }
    private baseSize() {
        const { vw, vh } = this.view();
        return Math.min(vw * 0.27, vh * 0.27);
    }
    render(pose: HeroPose, time: number) {
        const { vw, vh } = this.view();
        const intro = pose.intro;
        const e = 1 - Math.pow(1 - intro, 3);
        this.pivot.position.set((pose.x - 0.5) * vw, (0.5 - pose.y) * vh - (1 - e) * vh * 0.18, 0);
        const bob = Math.sin(time * 0.9) * 0.035;
        this.pivot.position.y += bob;
        this.pivot.scale.setScalar(this.baseSize() * pose.scale * (0.72 + 0.28 * e));
        const angle = (pose.stage + pose.spin) * Math.PI - (1 - e) * Math.PI * 0.9;
        const q = new Quaternion().setFromAxisAngle(AXIS, angle);
        const idle = new Quaternion().setFromAxisAngle(new Vector3(0, 1, 0), Math.sin(time * 0.35) * 0.12 + this.tilt.x * 0.35);
        const pitch = new Quaternion().setFromAxisAngle(new Vector3(1, 0, 0), 0.08 + this.tilt.y * 0.25);
        this.pivot.quaternion.copy(idle).multiply(pitch).multiply(q);
        const s = pose.stage;
        const n = this.forms.length;
        const [a, b] = this.window;
        this.forms.forEach((f, k) => {
            const inn = k === 0 ? 1 : smooth(k - a, k - b, s);
            const out = k === n - 1 && !this.cyclic ? 0 : smooth(k + b, k + a, s);
            let front: number;
            let incoming: number;
            if (this.cyclic && k === 0 && s > n - 1) {
                front = smooth(n - a, n - b, s);
                incoming = 1;
            }
            else if (out > 0) {
                front = out;
                incoming = 0;
            }
            else {
                front = inn;
                incoming = 1;
            }
            if (k === 0 && out === 0 && s < 1) {
                front = smooth(0.05, 0.85, intro);
                incoming = 1;
            }
            const visible = incoming ? front > 0.001 : front < 0.999;
            f.group.parent!.visible = visible;
            for (const d of f.dissolves) {
                d.front.value = front;
                d.incoming.value = incoming;
            }
            const swell = incoming ? 1.08 - 0.08 * front : 1 - 0.06 * front;
            f.group.parent!.scale.setScalar(swell);
        });
        this.renderer.render(this.scene, this.camera);
    }
    dispose() {
        this.scene.traverse((o) => {
            if (o instanceof Mesh) {
                o.geometry.dispose();
                (o.material as Material).dispose();
            }
        });
        this.renderer.dispose();
    }
}
