import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { $$, reduceMotion } from './env';
export function initReveals() {
    if (reduceMotion)
        return;
    for (const el of $$('[data-split]')) {
        const lines = $$('.line > span', el);
        gsap.set(lines, { opacity: 0 });
        ScrollTrigger.create({
            trigger: el,
            start: 'top 88%',
            once: true,
            onEnter: () => gsap.to(lines, { opacity: 1, duration: 0.32, stagger: 0.04, ease: 'power1.out' }),
        });
    }
    for (const el of $$('[data-reveal], [data-fade]')) {
        gsap.set(el, { opacity: 0 });
        ScrollTrigger.create({
            trigger: el,
            start: 'top 90%',
            once: true,
            onEnter: () => gsap.to(el, { opacity: 1, duration: 0.35, ease: 'power1.out' }),
        });
    }
}
