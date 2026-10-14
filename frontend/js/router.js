// Hash router + top nav. Views are lazy modules: default export (root, arg, {profile}) => optional cleanup fn
import { state, getToken, loadProfile, logout, bus } from './api.js';
import { html, mount, $, $$, loading, errorBox, spriteUrl } from './ui.js';

const views = Object.fromEntries(['login', 'starters', 'home', 'collection', 'catalog', 'teams', 'encounters', 'battle', 'history', 'profile']
  .map(n => [n, () => import(`./${n}.js`)]));
const LINKS = ['home', 'collection', 'catalog', 'teams', 'encounters', 'history', 'profile'];
let cleanup = null;
let seq = 0;
let current = '';

function renderNav() {
  const nav = $(document, '#nav');
  const p = state.profile;
  if (!getToken() || !p) return void (nav.innerHTML = '');
  const full = p.starters_chosen;
  mount(nav, html`<div class="nav-in"><a class="brand" href="#/home">MT</a>
    ${full ? html`<nav>${LINKS.map(l => html`<a href="#/${l}" class="${l === current || (l === 'encounters' && current === 'battle') ? 'on' : ''}">${l}</a>`)}</nav>` : ''}
    <span class="who">${p.avatar_monster_id ? html`<img class="sprite" src="${spriteUrl(p.avatar_monster_id)}" width="32" height="32" alt="">` : ''}${p.display_name}</span>
    <button class="btn small" id="logout">Logout</button></div>`);
  $(nav, '#logout').onclick = logout;
}

async function route() {
  const n = ++seq;
  const [, name = '', arg = ''] = location.hash.match(/^#\/([^/]*)\/?(.*)$/) || [];
  cleanup?.();
  cleanup = null;
  $$(document, '.overlay').forEach(o => o.remove());
  const authed = !!getToken();
  if (!authed && name !== 'login') return void (location.hash = '#/login');
  if (authed && name === 'login') return void (location.hash = '#/home');
  if (!views[name]) return void (location.hash = authed ? '#/home' : '#/login');
  current = name;
  const root = document.createElement('div');
  root.className = 'view view-' + name;
  $(document, '#app').replaceChildren(root);
  mount(root, loading());
  try {
    let profile = null;
    if (authed) {
      profile = await loadProfile();
      if (n !== seq) return;
      if (!profile.starters_chosen && name !== 'starters') return void (location.hash = '#/starters');
      if (profile.starters_chosen && name === 'starters') return void (location.hash = '#/home');
    }
    renderNav();
    const mod = await views[name]();
    root.innerHTML = '';
    const fn = await mod.default(root, arg, { profile });
    if (n !== seq) fn?.();
    else cleanup = fn || null;
  } catch (e) {
    if (n === seq && e.status !== 401) mount(root, errorBox(e));
  }
}

bus.addEventListener('profile', renderNav);
addEventListener('hashchange', route);
route();
