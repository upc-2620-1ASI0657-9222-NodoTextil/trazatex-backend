import { ChangeDetectorRef, Directive, HostListener, inject, input } from '@angular/core';
import { DraftStorage } from '../core/draft-storage.service';
import { SessionService } from '../core/session.service';

interface DraftBinding {
  load(): Promise<void>;
  save(): Promise<void>;
  changed(): void;
  clear(): Promise<void>;
  close(): void;
}

export function formDraft<T extends object>(scope: string | (() => string), read: () => T, restore: (value: T) => void): DraftBinding {
  const storage = inject(DraftStorage), session = inject(SessionService);
  let key = '', context = '', loaded = false, cleared = false;
  const save = async () => {
    if (loaded && !cleared && session.loggedIn()) await storage.write(key, { ...read(), __operationId: context });
  };
  return {
    async load() {
      key = 'form/' + session.usuario().id + '/' + (typeof scope === 'function' ? scope() : scope);
      const saved = await storage.recoverForm<T>(key);
      context = saved?.__operationId ?? crypto.randomUUID();
      cleared = false;
      storage.notice.set('');
      if (saved !== undefined) {
        const { __operationId, ...value } = saved;
        restore(value as T); storage.notice.set('Se recuperó el borrador de este formulario.');
      }
      loaded = true;
      storage.activate(key, context, save);
      await save();
    },
    save,
    changed() {
      if (cleared) { context = crypto.randomUUID(); storage.activate(key, context, save); }
      cleared = false;
    },
    async clear() {
      cleared = true;
      try { await storage.clearForm(key, context); }
      catch (error) { cleared = false; throw error; }
      storage.deactivate(key); storage.notice.set('');
    },
    close() { storage.deactivate(key); },
  };
}

@Directive({ selector: '[appDraft]' })
export class FormDraftDirective {
  readonly appDraft = input.required<DraftBinding>();
  private readonly storage = inject(DraftStorage);
  private readonly changes = inject(ChangeDetectorRef);
  private timer?: ReturnType<typeof setTimeout>;
  async ngOnInit() {
    try { await this.appDraft().load(); this.changes.markForCheck(); }
    catch (error) { this.report(error); }
  }
  @HostListener('input') @HostListener('change')
  edited() { this.appDraft().changed(); this.schedule(); }
  @HostListener('click')
  schedule() {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.persist(), 120);
  }
  @HostListener('window:offline') @HostListener('window:pagehide')
  persist() { void this.appDraft().save().catch(error => this.report(error)); }
  ngOnDestroy() { clearTimeout(this.timer); this.storage.notice.set(''); this.persist(); this.appDraft().close(); }
  private report(error: unknown) {
    this.storage.notice.set(error instanceof Error ? error.message : 'No se pudo conservar el borrador.');
  }
}
