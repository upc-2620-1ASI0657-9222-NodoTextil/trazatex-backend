import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { $, $$, finePointer, reduceMotion } from './env';
function initTeam() {
    const line = $('[data-team-line]');
    if (!line)
        return;
    const cord = $('.team__cord', line)!;
    const tags = $$('.member__tag', line);
    if (reduceMotion)
        return;
    gsap.set(cord, { scaleX: 0 });
    gsap.set(tags, { y: -60, autoAlpha: 0, rotation: (i) => (i % 2 ? 10 : -10) });
    ScrollTrigger.create({
        trigger: line,
        start: 'top 92%',
        once: true,
        onEnter: () => {
            gsap.to(cord, { scaleX: 1, duration: 0.24, ease: 'power1.out' });
            gsap.to(tags, { y: 0, autoAlpha: 1, duration: 0.28, ease: 'power2.out', stagger: 0.025, delay: 0.08 });
            gsap.to(tags, {
                rotation: (i) => (i % 2 ? 1.6 : -1.4),
                duration: 0.45,
                ease: 'power2.out',
                stagger: 0.025,
                delay: 0.12,
            });
        },
    });
    if (!finePointer)
        return;
    for (const [i, tag] of tags.entries()) {
        const rest = i % 2 ? 1.6 : -1.4;
        let lastX = 0;
        tag.addEventListener('pointerenter', (e) => (lastX = e.clientX));
        tag.addEventListener('pointermove', (e) => {
            const vx = e.clientX - lastX;
            lastX = e.clientX;
            if (Math.abs(vx) > 6 && !gsap.isTweening(tag))
                gsap.fromTo(tag, { rotation: rest + Math.max(-9, Math.min(9, vx * 0.6)) }, { rotation: rest, duration: 1.6, ease: 'elastic.out(1, 0.3)' });
        });
    }
}
function initFaq() {
    for (const item of $$<HTMLDetailsElement>('.faq__item')) {
        const summary = $('summary', item)!;
        const body = $('.faq__body', item)!;
        summary.addEventListener('click', (e) => {
            if (reduceMotion)
                return;
            e.preventDefault();
            if (item.open) {
                gsap.to(body, {
                    height: 0,
                    opacity: 0,
                    duration: 0.32,
                    ease: 'power2.in',
                    onComplete: () => {
                        item.open = false;
                        gsap.set(body, { clearProps: 'height,opacity' });
                    },
                });
            }
            else {
                item.open = true;
                gsap.fromTo(body, { height: 0, opacity: 0 }, { height: 'auto', opacity: 1, duration: 0.5, ease: 'expo.out' });
            }
        });
    }
}
export function initSections() {
    initTeam();
    initFaq();
}
