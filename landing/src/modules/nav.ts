import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { $, $$ } from './env';
export function initNav() {
    const nav = $('[data-nav]');
    if (!nav)
        return;
    const toggle = $<HTMLButtonElement>('[data-nav-toggle]', nav)!;
    const menu = $('[data-nav-links]', nav)!;
    const setOpen = (open: boolean) => {
        toggle.setAttribute('aria-expanded', String(open));
        nav.classList.toggle('is-open', open);
        document.body.classList.toggle('menu-open', open);
    };
    toggle.addEventListener('click', () => setOpen(toggle.getAttribute('aria-expanded') !== 'true'));
    menu.addEventListener('click', (e) => {
        if ((e.target as HTMLElement).closest('a'))
            setOpen(false);
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && nav.classList.contains('is-open')) {
            setOpen(false);
            toggle.focus();
        }
    });
    window.matchMedia('(min-width: 1081px)').addEventListener('change', (e) => e.matches && setOpen(false));
    ScrollTrigger.create({
        start: 8,
        end: 'max',
        onToggle: (self) => nav.classList.toggle('is-scrolled', self.isActive),
    });
    const links = $$<HTMLAnchorElement>('.nav__links a');
    for (const a of links) {
        const target = $(a.getAttribute('href') ?? '');
        if (!target)
            continue;
        const endAt = a.getAttribute('href') === '#beneficios' ? $('#producto') : null;
        ScrollTrigger.create({
            trigger: target,
            start: 'top 40%',
            endTrigger: endAt ?? target,
            end: endAt ? 'top 40%' : 'bottom 40%',
            onToggle: (self) => {
                if (self.isActive)
                    links.forEach((l) => l.toggleAttribute('aria-current', l === a));
                else
                    a.removeAttribute('aria-current');
            },
        });
    }
}
