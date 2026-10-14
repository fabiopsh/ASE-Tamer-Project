// Shared UI helpers: safe html templates, toasts, modal, badges, monster cards
export class Raw { constructor(s) { this.s = s; } }
const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
const esc = v => v instanceof Raw ? v.s : Array.isArray(v) ? v.map(esc).join('')
  : v == null || v === false ? '' : String(v).replace(/[&<>"']/g, c => ESC[c]);
// tagged template: interpolated values are escaped unless produced by html``
export const html = (s, ...v) => new Raw(s.reduce((a, x, i) => a + x + (i < v.length ? esc(v[i]) : ''), ''));
export const mount = (el, h) => { el.innerHTML = h.s; return el; };

export const $ = (r, s) => r.querySelector(s);
export const $$ = (r, s) => [...r.querySelectorAll(s)];
export const on = (root, ev, sel, fn) => root.addEventListener(ev, e => {
  const t = e.target.closest(sel);
  if (t && root.contains(t)) fn(t, e);
});
export const sleep = ms => new Promise(r => setTimeout(r, ms));
export const debounce = (fn, ms = 300) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
export const spriteUrl = id => `/images/monsters/${String(id).padStart(3, '0')}.png`;
export const fmtDate = s => (s ? new Date(s).toLocaleString() : '');
export const pct = c => Math.round(c * 100) + '%';

export const ELEMENTS = ['dream', 'echo', 'time', 'gravity', 'silence', 'memory', 'chaos'];
export const RARITIES = ['common', 'rare', 'legendary'];

export function toast(msg, type = 'info') {
  const t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  document.getElementById('toasts').append(t);
  setTimeout(() => t.remove(), 4500);
}

export function modal(content) {
  const ov = document.createElement('div');
  ov.className = 'overlay';
  ov.innerHTML = '<div class="modal"><button class="btn small close" aria-label="Close">X</button><div class="modal-body"></div></div>';
  const body = ov.querySelector('.modal-body');
  body.innerHTML = content.s;
  const close = () => { ov.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = e => e.key === 'Escape' && close();
  ov.addEventListener('click', e => { if (e.target === ov || e.target.closest('.close')) close(); });
  document.addEventListener('keydown', onKey);
  document.body.append(ov);
  return { body, close };
}

export const loading = () => html`<div class="loading">Loading<span>.</span><span>.</span><span>.</span></div>`;
export const errorBox = e => html`<div class="card error-box"><b>Error</b> ${e.message}${e.details?.length ? html`<ul>${e.details.map(d => html`<li>${d}</li>`)}</ul>` : ''}</div>`;

export const elBadge = e => html`<span class="badge el-${e}">${e}</span>`;
export const rarBadge = r => html`<span class="badge rar-${r}">${r}</span>`;
export const sprite = (url, size = 128, cls = '') => html`<img class="sprite ${cls}" src="${url}" width="${size}" height="${size}" alt="">`;
export const bar = (v, max, cls = '') => html`<div class="bar ${cls}"><i style="width:${max ? Math.min(100, 100 * v / max) : 0}%"></i></div>`;

const STATS = ['hp', 'attack', 'defense', 'speed'];
export const statBars = (s, g) => html`<div class="stats">${STATS.map(k => html`
  <div class="stat"><span class="sl">${k.slice(0, 3)}</span>${bar(s[k], 200, 's-' + k)}
  <span class="sv">${s[k]}${g ? html` <small>+${g[k]}/lv</small>` : ''}</span></div>`)}</div>`;

export const abilityBox = a => a ? html`<div class="ability"><b>${a.name}</b>
  ${a.element ? elBadge(a.element) : html`<span class="badge">generic</span>`}<p>${a.description}</p></div>` : '';

// card for MonsterSummary / OwnedMonster; `extra` must be html``
export const monsterCard = (m, extra = '', cls = '') => html`<div class="card mon r-${m.rarity} ${cls}" data-id="${m.owned_id || m.monster_id}">
  ${sprite(m.image_url, 128, 'bob')}<div class="mon-name">${m.name}</div>
  <div class="badges">${elBadge(m.element)}${rarBadge(m.rarity)}</div>
  ${m.level != null ? html`<div class="lvl">Lv ${m.level}</div>` : ''}${extra}</div>`;

// detail for MonsterDetail or OwnedMonsterDetail
export function detailHtml(d) {
  const owned = d.owned_id != null;
  const base = d.base_stats || { hp: d.base_hp, attack: d.base_attack, defense: d.base_defense, speed: d.base_speed };
  const growth = d.growth || { hp: d.growth_hp, attack: d.growth_attack, defense: d.growth_defense, speed: d.growth_speed };
  return html`<div class="detail r-${d.rarity}">
    <div class="detail-head">${sprite(d.image_url, 192, 'bob')}
      <div><h2>${d.name}</h2><div class="badges">${elBadge(d.element)}${rarBadge(d.rarity)}</div>
      ${owned ? html`<div class="lvl big">Lv ${d.level}</div>
        <div class="xp">XP ${d.xp} / ${d.xp_to_next}${bar(d.xp, d.xp_to_next, 'xp')}</div>` : ''}</div></div>
    <p class="desc">${d.description}</p>
    <h3>${owned ? 'Stats' : 'Base stats'}</h3>${statBars(owned ? d.stats : base, growth)}
    <h3>Ability</h3>${abilityBox(d.ability)}
    <div class="matchups">Strong vs ${elBadge(d.strong_against)} Weak vs ${elBadge(d.weak_against)}</div>
    ${owned ? html`<div class="muted">Source: ${d.source} - Obtained ${fmtDate(d.obtained_at)}</div>` : ''}</div>`;
}

export const select = (name, label, opts, val = '') => html`<label>${label}<select name="${name}">
  <option value="">All</option>${opts.map(o => html`<option ${o === val ? 'selected' : ''}>${o}</option>`)}</select></label>`;

export const pager = (p, t) => t > 1 ? html`<div class="pager">
  <button class="btn small" data-page="${p - 1}" ${p <= 0 ? 'disabled' : ''}>Prev</button><span>Page ${p + 1} / ${t}</span>
  <button class="btn small" data-page="${p + 1}" ${p + 1 >= t ? 'disabled' : ''}>Next</button></div>` : '';
