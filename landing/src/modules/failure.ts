import { gsap } from 'gsap';
import type { HeroPose, HeroScene } from './hero3d';
import { $, $$, reduceMotion } from './env';
export function initFailure() {
    const demo = $('[data-trace-demo]');
    if (!demo)
        return;
    const canvas = $<HTMLCanvasElement>('[data-trace-canvas]', demo)!;
    const fallback = $<HTMLImageElement>('[data-trace-fallback]', demo)!;
    const lens = $('[data-lens]', demo)!;
    const caption = $('[data-trace-caption]', demo)!;
    const steps = $$('[data-step]');
    const state = { stage: 1 };
    gsap.set(lens, { xPercent: -50, yPercent: -50 });
    const pose: HeroPose = { stage: 1, spin: 0, x: 0.5, y: 0.47, scale: 1, intro: 1 };
    let scene: HeroScene | null = null;
    import('./hero3d')
        .then(async ({ HeroScene }) => {
        const s = new HeroScene(canvas, '/img/tex/marron.webp', 'plaid');
        await s.ready;
        scene = s;
        resize();
        start();
    })
        .catch(() => {
        canvas.remove();
        fallback.hidden = false;
    });
    const resize = () => scene?.resize(demo.clientWidth, demo.clientHeight);
    new ResizeObserver(resize).observe(demo);
    let visible = false;
    let raf = 0;
    let time = 0;
    let last = performance.now();
    const frame = (now: number) => {
        raf = 0;
        if (!scene || !visible)
            return;
        const dt = Math.min(0.05, (now - last) / 1000);
        last = now;
        if (!reduceMotion)
            time += dt;
        pose.stage = state.stage;
        scene.render(pose, time);
        raf = requestAnimationFrame(frame);
    };
    const start = () => {
        if (!raf && scene && visible) {
            last = performance.now();
            raf = requestAnimationFrame(frame);
        }
    };
    const say = (text: string, step: 'back' | 'forward' | null) => {
        caption.textContent = text;
        steps.forEach((li) => li.classList.toggle('is-on', li.dataset.step === step));
    };
    if (reduceMotion) {
        gsap.set(lens, { left: '50%', top: '46%', autoAlpha: 1 });
        new IntersectionObserver(([e]) => {
            visible = e.isIntersecting;
            start();
        }).observe(demo);
        return;
    }
    const at = (x: number, y: number, duration: number, ease = 'power2.inOut') => ({ left: `${x}%`, top: `${y}%`, duration, ease }) as gsap.TweenVars;
    gsap.set(lens, { left: '88%', top: '86%', autoAlpha: 0, rotation: -12 });
    const tl = gsap.timeline({ repeat: -1, paused: true, defaults: { ease: 'power1.inOut' } });
    tl.add(() => say('Tela a cuadros', null))
        .to(lens, { autoAlpha: 1, duration: 0.4 }, 0.3)
        .to(lens, at(52, 46, 1.3), 0.3)
        .add(() => say('Hacia el origen: tela → hilo', 'back'), 1.4)
        .to(state, { stage: 0, duration: 1.8 }, 1.5)
        .to(lens, at(44, 52, 0.9, 'sine.inOut'), 1.6)
        .to(lens, at(56, 42, 0.9, 'sine.inOut'), 2.5)
        .to(lens, at(14, 18, 1.2), 4.4)
        .to(lens, { rotation: 8, duration: 1.2 }, 4.4)
        .add(() => say('Tela a cuadros', null), 4.9)
        .to(state, { stage: 1, duration: 1.4 }, 4.9)
        .to(lens, at(50, 48, 1.3), 7.0)
        .to(lens, { rotation: -12, duration: 1.3 }, 7.0)
        .add(() => say('Hacia adelante: tela → bufanda', 'forward'), 8.1)
        .to(state, { stage: 2, duration: 1.8 }, 8.2)
        .to(lens, at(56, 56, 0.9, 'sine.inOut'), 8.3)
        .to(lens, at(46, 40, 0.9, 'sine.inOut'), 9.2)
        .to(lens, at(88, 86, 1.2), 11.1)
        .to(lens, { autoAlpha: 0, duration: 0.4 }, 11.9)
        .add(() => say('Tela a cuadros', null), 11.6)
        .to(state, { stage: 1, duration: 1.4 }, 11.6)
        .to({}, { duration: 0.6 }, 13);
    new IntersectionObserver(([e]) => {
        visible = e.isIntersecting;
        if (visible) {
            start();
            tl.play();
        }
        else
            tl.pause();
    }, { threshold: 0.25 }).observe(demo);
}
