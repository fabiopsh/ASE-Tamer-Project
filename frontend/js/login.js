import { api, setToken, errText } from './api.js';
import { html, mount, $, $$, spriteUrl } from './ui.js';

export default function (root) {
  let mode = 'login';
  const draw = () => {
    const reg = mode === 'register';
    mount(root, html`<section class="auth">
      <div class="logo">MONSTER<br>TAMERS</div>
      <div class="floaters">${[1, 2, 3].map(i => html`<img class="sprite bob" style="animation-delay:${i * 0.4}s" src="${spriteUrl(i)}" width="96" height="96" alt="">`)}</div>
      <div class="card auth-card">
        <div class="tabs"><button class="tab ${reg ? '' : 'on'}" data-mode="login">Login</button><button class="tab ${reg ? 'on' : ''}" data-mode="register">Register</button></div>
        <form>
          <label>Username<input name="username" autocomplete="username" required></label>
          <label>Password<input name="password" type="password" autocomplete="${reg ? 'new-password' : 'current-password'}" required></label>
          <div class="error" role="alert"></div>
          <button class="btn primary">${reg ? 'Create account' : 'Enter'}</button>
        </form></div></section>`);
    $$(root, '.tab').forEach(b => (b.onclick = () => { mode = b.dataset.mode; draw(); }));
    const form = $(root, 'form');
    form.onsubmit = async e => {
      e.preventDefault();
      const body = Object.fromEntries(new FormData(form));
      const btn = $(form, '.btn');
      btn.disabled = true;
      $(form, '.error').textContent = '';
      try {
        if (reg) await api('/auth/register', { method: 'POST', body });
        const r = await api('/auth/login', { method: 'POST', body });
        setToken(r.access_token);
        location.hash = '#/home';
      } catch (err) {
        $(form, '.error').textContent = errText(err);
        btn.disabled = false;
      }
    };
  };
  draw();
}
