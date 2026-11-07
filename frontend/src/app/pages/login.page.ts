import { IconComponent } from '../shared/icon.component';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../core/api.service';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Rol } from '../core/models';
import { SessionService } from '../core/session.service';
import { UiService } from '../shared/ui';

@Component({
  selector: 'app-login', imports: [FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="login">
      <section class="cover" aria-label="TrazaTex">
        <div class="cover-main">
          <div class="brand">
            <img src="/brand/wordmark-blanco-480.webp" alt="TrazaTex" />
          </div>
          <div class="cover-copy">
            <h1>Cada lote,<br />de la fibra<br />a la prenda.</h1>
            <p>Trazabilidad por lotes para la cadena<br class="desktop-break" /> textil peruana.</p>
          </div>
        </div>
        <div class="photo-strip" aria-hidden="true">
          <img src="/login/imagen1_login.webp" alt="" />
          <img src="/login/imagen2_login.jpg" alt="" />
          <img src="/login/imagen3_login.jpg" alt="" />
          <img src="/login/imagen4_login.jpg" alt="" />
        </div>
      </section>

      <section class="form-side">
        <form class="login-card" (submit)="$event.preventDefault(); entrar()">
          <span class="hole" aria-hidden="true"></span>
          <h2>Login</h2>
          <div class="roles" role="group" aria-label="Tipo de usuario" aria-describedby="rol-ayuda">
            @for (r of roles; track r.rol) {
              <button
                class="role"
                type="button"
                [attr.aria-pressed]="rol() === r.rol"
                [disabled]="busy()"
                (click)="rol.set(r.rol)"
              >
                <app-icon [name]="r.icon" [size]="34" />
                <span>{{ r.etiqueta }}</span>
              </button>
            }
          </div>
          <p class="role-hint" id="rol-ayuda">El rol lo define tu cuenta.</p>
          <div class="credentials">
            <label class="login-field">
              <span>Correo</span>
              <input type="email" name="correo" [(ngModel)]="email" required placeholder="tucorreo@gmail.com" autocomplete="username" />
            </label>
            <label class="login-field">
              <span>Contraseña</span>
              <input type="password" name="contrasena" [(ngModel)]="password" required placeholder="tucontraseña123" autocomplete="current-password" />
            </label>
          </div>
          @if (session.endNotice()) { <p class="role-hint" role="status" data-testid="fin-sesion">{{ session.endNotice() }}</p> }
          <p role="alert">{{ error() }}</p><button class="enter" type="submit" [disabled]="busy()">{{ busy() ? "Ingresando…" : "Ingresar" }}</button>
        </form>
      </section>
    </main>
  `,
  styles: `
    :host { display: block; }
    .login {
      display: grid;
      grid-template-columns: 52.5% 47.5%;
      min-height: 100dvh;
      overflow: hidden;
    }
    .cover {
      display: grid;
      grid-template-rows: minmax(0, 1fr) 26%;
      min-width: 0;
      background: var(--green-950);
    }
    .cover-main {
      position: relative;
      min-height: 19rem;
      background: linear-gradient(rgb(5 28 17 / .59), rgb(5 28 17 / .62)), url('/tex/verde.webp') center / 540px, var(--green-950);
      color: var(--cream);
    }
    .brand {
      position: absolute;
      top: clamp(1rem, 3.8vh, 2.2rem);
      left: clamp(1.6rem, 3.5vw, 3.5rem);
      display: flex;
      align-items: center;
      gap: .2rem;
      height: 1.5rem;
    }
    .brand img:first-child { width: 5rem; height: 1.6rem; object-fit: contain; }
    .brand img:last-child { width: 5rem; height: 1.6rem; object-fit: contain; }
    .cover-copy {
      position: absolute;
      left: clamp(1.6rem, 3.5vw, 3.5rem);
      top: clamp(5.6rem, 23vh, 13rem);
      width: calc(100% - 3.2rem);
    }
    .cover-copy h1 {
      margin: 0;
      font: 400 clamp(2.45rem, 4.4vw, 5rem) / 1.01 'Arvo', serif;
      letter-spacing: 0;
      color: var(--cream);
    }
    .cover-copy p {
      margin: .55rem 0 0;
      font: 400 clamp(.7rem, .94vw, 1rem) / 1.5 var(--f-body);
      color: #f2e9d7;
    }
    .photo-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); min-height: 0; }
    .photo-strip img { width: 100%; height: 100%; min-height: 0; object-fit: cover; }
    .form-side {
      display: grid;
      place-items: center;
      min-width: 0;
      padding: 1rem;
      background: #ede4d6;
    }
    .login-card {
      position: relative;
      display: flex;
      flex-direction: column;
      width: min(30.8vw, 26rem);
      min-width: 17rem;
      height: min(92dvh, 38rem);
      padding: clamp(2.05rem, 8.5vh, 3.5rem) 1rem clamp(2rem, 9vh, 4.5rem);
      background: #f9f6ef;
      color: var(--green-900);
      clip-path: polygon(1.15rem 0, calc(100% - .5rem) 0, 100% .5rem, 100% 100%, 0 100%, 0 1.15rem);
      filter: drop-shadow(.75rem .75rem 0 rgb(200 169 121 / .18));
    }
    .hole {
      position: absolute;
      top: .45rem;
      left: 50%;
      width: .65rem;
      height: .65rem;
      transform: translateX(-50%);
      background: #e8dec9;
      border-radius: 50%;
    }
    .login-card h2 {
      margin: 0 0 .6rem;
      font: 400 clamp(2.2rem, 3.8vw, 3rem) / 1 'Arvo', serif;
      letter-spacing: 0;
      color: var(--green-900);
    }
    .roles { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); width: 100%; }
    .role {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      min-width: 0;
      height: clamp(3.2rem, 12.4vh, 4.4rem);
      padding: .12rem .1rem .16rem;
      border: 0;
      background: #e8dbc5;
      color: var(--green-900);
      cursor: pointer;
      transition: background-color 130ms ease;
    }
    .role:nth-child(2) { background: #e1d5c3; }
    .role[aria-pressed='true'], .role:nth-child(2)[aria-pressed='true'] { background: var(--green-950); color: #fffaf0; box-shadow: inset 0 -3px 0 #d1c3a9; }
    .role:hover:not([aria-pressed='true']) { background: #d3c7b4; }
    .role:focus-visible { outline: 2px solid var(--green-700); outline-offset: -3px; }
    .role img { display: block; width: min(100%, 2.5rem); height: min(8.5vh, 2.3rem); object-fit: contain; filter: brightness(0) saturate(100%) invert(13%) sepia(17%) saturate(1141%) hue-rotate(94deg); }
    .role[aria-pressed='true'] img { filter: none; }
    .role span { display: block; max-width: 100%; font: 700 clamp(.59rem, .91vw, .79rem) / 1.12 var(--f-body); white-space: nowrap; }
    .credentials { display: grid; gap: .45rem; margin-top: clamp(1.3rem, 5.7vh, 2rem); }
    .login-field { display: grid; gap: .1rem; }
    .login-field span { font: 700 clamp(.82rem, 1.5vw, 1.05rem) / 1.25 var(--f-body); }
    .login-field input {
      width: 100%;
      height: clamp(2rem, 7.3vh, 2.65rem);
      min-width: 0;
      padding: .3rem .45rem;
      border: 1px solid rgb(15 46 32 / .45);
      border-radius: 0;
      background: #f9f6ef;
      color: var(--green-900);
      font: 400 .9rem var(--f-body);
    }
    .role-hint { margin: .55rem 0 0; font-size: .8rem; color: #4a4b37; }
    .login-field input::placeholder { color: #5f6155; opacity: 1; }
    .login-field input:focus-visible { outline: 2px solid var(--green-700); outline-offset: 1px; }
    .enter {
      display: block;
      width: 100%;
      min-height: 2.65rem;
      margin-top: clamp(1.5rem, 6.8vh, 2.6rem);
      padding: .32rem .6rem;
      border: 0;
      background: var(--green-900);
      color: #fff;
      cursor: pointer;
      font: 700 clamp(.9rem, 1.45vw, 1.15rem) / 1.15 var(--f-body);
      transition: background-color 130ms ease;
    }
    .enter:hover { background: #1f5a3e; }
    .enter:focus-visible { outline: 2px solid #b8955f; outline-offset: 3px; }
    @media (max-height: 480px) and (min-width: 761px) {
      .cover-main { min-height: 0; }
    }
    @media (max-width: 760px) {
      .login { grid-template-columns: 1fr; }
      .cover { grid-template-rows: minmax(15rem, 1fr) 5.5rem; height: 21rem; }
      .cover-main { min-height: 0; }
      .cover-copy { top: 5rem; }
      .cover-copy h1 { font-size: clamp(2.2rem, 7.5vw, 3.2rem); }
      .cover-copy p { font-size: .75rem; }
      .form-side { padding: 1.5rem 1rem 2rem; }
      .login-card { width: min(100%, 23rem); min-width: 0; height: auto; min-height: 24rem; padding: 2.5rem 1rem 2.7rem; }
      .login-card h2 { font-size: 2.65rem; }
      .role { height: 4rem; }
      .role img { height: 2.45rem; }
      .role span { font-size: .72rem; }
      .credentials { margin-top: 1.6rem; }
      .login-field span { font-size: .95rem; }
      .login-field input { height: 2.55rem; }
      .enter { margin-top: 1.8rem; min-height: 2.5rem; }
    }
    @media (max-width: 360px) {
      .brand { left: 1.25rem; }
      .cover-copy { left: 1.25rem; }
      .role span { font-size: .64rem; }
    }
  `,
})
export class LoginPage {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly ui = inject(UiService);
  readonly rol = signal<Rol>('Operario');
  readonly roles: { rol: Rol; etiqueta: string; icon: string }[] = [
    { rol: 'Operario', etiqueta: 'Operario', icon: 'process' },
    { rol: 'Administrador de Empresa', etiqueta: 'Administrador', icon: 'company' },
    { rol: 'Dueño de Licencia', etiqueta: 'Dueño', icon: 'license' },
  ];

  email = '';
  password = '';
  readonly error = signal('');
  readonly busy = signal(false);
  async entrar() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.session.login(this.email.trim(), this.password);
      // El selector de arriba es solo una ayuda visual: el rol real lo dicta la cuenta. Si no coinciden, se dice cuál es.
      const real = this.session.rol();
      if (real !== this.rol()) this.ui.toast('Tu cuenta es de ' + real + '; entraste con ese rol.');
      const target = this.route.snapshot.queryParamMap.get('returnUrl');
      await this.router.navigateByUrl(!this.session.esDueno() && target?.startsWith('/lotes/') && !target.includes('//') ? target : this.session.esDueno() ? '/empresas' : '/trazabilidad');
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
}
