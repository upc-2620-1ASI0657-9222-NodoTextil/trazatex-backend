import { gsap } from 'gsap';
import { $, reduceMotion } from './env';
export interface Hero {
    intro: () => gsap.core.Timeline;
    ready: Promise<void>;
}
export function initHero(): Hero {
    const hero = $('[data-hero]')!;
    const photo = $<HTMLImageElement>('[data-hero-media] img', hero)!;
    const copy = $('[data-hero-copy]', hero)!;
    const loaded = photo.complete
        ? Promise.resolve()
        : new Promise<void>((resolve) => {
            photo.addEventListener('load', () => resolve(), { once: true });
            photo.addEventListener('error', () => resolve(), { once: true });
        });
    const ready = loaded.then(() => photo.decode().catch(() => undefined));
    const intro = () => {
        const timeline = gsap.timeline();
        if (!reduceMotion) {
            timeline.fromTo(copy, { opacity: 0.9 }, { opacity: 1, duration: 0.25, ease: 'power1.out' });
        }
        return timeline;
    };
    return { intro, ready };
}
