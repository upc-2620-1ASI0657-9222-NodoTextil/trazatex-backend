import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { $, $$, finePointer, reduceMotion } from './env';
function initCarousel() {
    const root = $('[data-carousel]');
    if (!root)
        return;
    const track = $('[data-carousel-track]', root)!;
    const viewport = $('.carousel__viewport', root)!;
    const cards = $$('.tcard', track);
    if (reduceMotion) {
        viewport.classList.add('is-manual');
        $('[data-carousel-prev]', root)!.addEventListener('click', () => viewport.scrollBy({ left: -viewport.clientWidth * 0.8 }));
        $('[data-carousel-next]', root)!.addEventListener('click', () => viewport.scrollBy({ left: viewport.clientWidth * 0.8 }));
        return;
    }
    for (const c of cards) {
        const clone = c.cloneNode(true) as HTMLElement;
        clone.setAttribute('aria-hidden', 'true');
        track.append(clone);
    }
    let loop: gsap.core.Tween | null = null;
    const build = () => {
        const progress = loop ? loop.progress() : 0;
        loop?.kill();
        const gap = parseFloat(getComputedStyle(track).columnGap) || 0;
        const setWidth = cards.reduce((w, c) => w + c.offsetWidth + gap, 0);
        loop = gsap.fromTo(track, { x: 0 }, {
            x: -setWidth,
            duration: cards.length * 7,
            ease: 'none',
            repeat: -1,
        });
        loop.progress(progress);
        if (!visible || hovering)
            loop.pause();
    };
    let hovering = false;
    let visible = false;
    const speed = { t: 1 };
    const ease = (to: number) => gsap.to(speed, { t: to, duration: 0.6, ease: 'power2.out', onUpdate: () => loop?.timeScale(Math.max(0.001, speed.t)) });
    root.addEventListener('pointerenter', () => {
        hovering = true;
        ease(0);
    });
    root.addEventListener('pointerleave', () => {
        hovering = false;
        loop?.play();
        ease(1);
    });
    root.addEventListener('focusin', () => loop?.pause());
    root.addEventListener('focusout', () => !hovering && loop?.play());
    const step = (dir: 1 | -1) => {
        if (!loop)
            return;
        const per = loop.duration() / cards.length;
        const target = Math.round(loop.time() / per + dir) * per;
        const wrapped = ((target % loop.duration()) + loop.duration()) % loop.duration();
        const paused = loop.paused();
        loop.pause();
        gsap.to(loop, {
            time: wrapped < loop.time() && dir === 1 ? loop.duration() : wrapped,
            duration: 0.7,
            ease: 'expo.out',
            onComplete: () => {
                if (loop!.time() >= loop!.duration() - 0.01)
                    loop!.time(0);
                if (!paused && !hovering)
                    loop!.play();
            },
        });
    };
    $('[data-carousel-prev]', root)!.addEventListener('click', () => step(-1));
    $('[data-carousel-next]', root)!.addEventListener('click', () => step(1));
    new IntersectionObserver(([e]) => {
        visible = e.isIntersecting;
        if (!loop)
            build();
        if (visible && !hovering)
            loop!.play();
        else
            loop!.pause();
    }).observe(root);
    new ResizeObserver(() => loop && build()).observe(track);
}
function initMagnetic() {
    if (!finePointer || reduceMotion)
        return;
    for (const btn of $$('.btn--forest, .btn--solid, .btn--nav, .video__play-icon, .carousel__btn, .gnode__open')) {
        const x = gsap.quickTo(btn, 'x', { duration: 0.5, ease: 'power3.out' });
        const y = gsap.quickTo(btn, 'y', { duration: 0.5, ease: 'power3.out' });
        btn.addEventListener('pointermove', (e) => {
            const r = btn.getBoundingClientRect();
            x(((e.clientX - r.left) / r.width - 0.5) * Math.min(10, r.width * 0.08));
            y(((e.clientY - r.top) / r.height - 0.5) * 6);
        });
        btn.addEventListener('pointerleave', () => {
            x(0);
            y(0);
        });
    }
}
function initProgress() {
    const nav = $('[data-nav]');
    if (!nav)
        return;
    const line = document.createElement('span');
    line.className = 'nav__progress';
    line.setAttribute('aria-hidden', 'true');
    nav.append(line);
    ScrollTrigger.create({
        start: 0,
        end: 'max',
        onUpdate: (self) => gsap.set(line, { scaleX: self.progress }),
    });
}
function initDepth() {
    if (reduceMotion)
        return;
    for (const img of $$('.voices__photo img, .video__poster, .about .values__item')) {
        if (img.tagName === 'IMG') {
            gsap.fromTo(img, { yPercent: -6, scale: 1.12 }, {
                yPercent: 6,
                scale: 1.12,
                ease: 'none',
                scrollTrigger: { trigger: img.parentElement!, start: 'top bottom', end: 'bottom top', scrub: true },
            });
        }
    }
    const values = $$('.values__item');
    if (values.length)
        gsap.from(values, {
            x: 18,
            opacity: 0,
            duration: 0.8,
            ease: 'expo.out',
            stagger: 0.1,
            scrollTrigger: { trigger: values[0], start: 'top 85%', once: true },
        });
    for (const sec of $$('.cut')) {
        gsap.fromTo(sec, { '--cut': '5.5vw' }, {
            '--cut': '2.2vw',
            ease: 'none',
            scrollTrigger: { trigger: sec, start: 'top bottom', end: 'top 35%', scrub: 0.6 },
        });
    }
}
function initGlow() {
    if (!finePointer || reduceMotion)
        return;
    for (const sec of $$('[data-glow]')) {
        const mx = gsap.quickTo(sec, '--mx', { duration: 0.9, ease: 'power3.out' });
        const my = gsap.quickTo(sec, '--my', { duration: 0.9, ease: 'power3.out' });
        sec.addEventListener('pointermove', (e) => {
            const r = sec.getBoundingClientRect();
            mx(((e.clientX - r.left) / r.width) * 100);
            my(((e.clientY - r.top) / r.height) * 100);
        });
    }
}
function initAnchors() {
    if (reduceMotion)
        return;
    document.addEventListener('click', (e) => {
        const a = (e.target as HTMLElement).closest<HTMLAnchorElement>('a[href^="#"]');
        const id = a?.getAttribute('href');
        if (!a || !id || id === '#')
            return;
        const target = $(id);
        if (!target)
            return;
        e.preventDefault();
        const navH = $('[data-nav]')?.offsetHeight ?? 0;
        window.scrollTo({ top: target.getBoundingClientRect().top + window.scrollY - navH, behavior: 'smooth' });
        history.replaceState(null, '', id);
    });
}
export function initPolish() {
    initAnchors();
    initGlow();
    initCarousel();
    initMagnetic();
    initProgress();
    initDepth();
}
