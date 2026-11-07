import { $ } from './env';
export function runLoader(): Promise<void> {
    const loader = $('[data-loader]');
    if (!loader)
        return Promise.resolve();
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let seen = false;
    try {
        seen = sessionStorage.getItem('tt-loader-seen') === '1';
        sessionStorage.setItem('tt-loader-seen', '1');
    }
    catch {
        seen = false;
    }
    return new Promise<void>((resolve) => {
        window.setTimeout(() => {
            loader.classList.add('is-done');
            document.body.classList.remove('is-loading');
            window.setTimeout(() => {
                loader.remove();
                resolve();
            }, reducedMotion ? 0 : 180);
        }, reducedMotion || seen ? 0 : 440);
    });
}
