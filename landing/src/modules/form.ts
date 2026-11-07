import { $ } from './env';
const CONTACT_ENDPOINT = 'https://formsubmit.co/ajax/fluffyspaceuwu@gmail.com';
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
export function initForm() {
    const form = $<HTMLFormElement>('[data-form]');
    const status = $('[data-form-status]');
    if (!form || !status)
        return;
    const fields = ['nombre', 'empresa', 'email'] as const;
    const motivo = form.elements.namedItem('motivo') as HTMLSelectElement;
    const submitLabel = form.querySelector('[data-submit-label]')!;
    const LABELS: Record<string, string> = {
        licencia: 'Enviar solicitud de licencia',
        demo: 'Solicitar demostración',
        soporte: 'Enviar consulta',
    };
    const syncLabel = () => (submitLabel.textContent = LABELS[motivo.value] ?? 'Enviar solicitud');
    motivo.addEventListener('change', syncLabel);
    document.addEventListener('click', (e) => {
        const link = (e.target as HTMLElement).closest<HTMLElement>('[data-motivo]');
        if (!link)
            return;
        motivo.value = link.dataset.motivo!;
        syncLabel();
    });
    syncLabel();
    const check = (name: (typeof fields)[number]) => {
        const input = form.elements.namedItem(name) as HTMLInputElement;
        const errId = name === 'email' ? 'f-correo-err' : `f-${name}-err`;
        const err = document.getElementById(errId)!;
        const value = input.value.trim();
        const ok = name === 'email' ? EMAIL.test(value) : value.length > 1;
        input.setAttribute('aria-invalid', String(!ok));
        if (ok)
            input.removeAttribute('aria-describedby');
        else
            input.setAttribute('aria-describedby', err.id);
        err.hidden = ok;
        return ok;
    };
    for (const name of fields) {
        const input = form.elements.namedItem(name) as HTMLInputElement;
        input.addEventListener('blur', () => {
            if (input.value)
                check(name);
        });
        input.addEventListener('input', () => {
            if (input.getAttribute('aria-invalid') === 'true')
                check(name);
        });
    }
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const results = fields.map(check);
        if (results.includes(false)) {
            const first = fields[results.indexOf(false)];
            (form.elements.namedItem(first) as HTMLInputElement).focus();
            status.textContent = 'Revisa los campos marcados.';
            return;
        }
        const button = form.querySelector<HTMLButtonElement>('button[type="submit"]')!;
        if (button.disabled)
            return;
        const data = Object.fromEntries(new FormData(form).entries()) as Record<string, string>;
        const reason = motivo.selectedOptions[0]?.textContent?.trim() || 'Consulta';
        data._subject = `TrazaTex: ${reason}`;
        data._template = 'table';
        data._captcha = 'false';
        button.disabled = true;
        status.textContent = 'Enviando solicitud…';
        try {
            const response = await fetch(CONTACT_ENDPOINT, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify(data),
            });
            if (!response.ok)
                throw new Error('Error de conexión');
            const result = await response.json() as {
                success?: boolean | string;
                message?: string;
            };
            const success = result.success === true || result.success === 'true';
            if (!success) {
                const activationPending = /activat/i.test(result.message || '');
                status.textContent = activationPending
                    ? 'El formulario está pendiente de activación. Inténtalo más adelante.'
                    : 'No se pudo enviar la solicitud. Vuelve a intentarlo.';
                return;
            }
            const name = data.nombre;
            form.reset();
            syncLabel();
            for (const field of fields) {
                const input = form.elements.namedItem(field) as HTMLInputElement;
                input.removeAttribute('aria-invalid');
                input.removeAttribute('aria-describedby');
                document.getElementById(field === 'email' ? 'f-correo-err' : `f-${field}-err`)!.hidden = true;
            }
            status.textContent = `Gracias, ${name}. Tu solicitud se envió correctamente.`;
        }
        catch {
            status.textContent = 'No pudimos enviar tu solicitud. Comprueba tu conexión e inténtalo de nuevo.';
        }
        finally {
            button.disabled = false;
        }
    });
}
