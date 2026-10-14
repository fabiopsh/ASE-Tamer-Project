import { api, errText } from './api.js';
import { html, mount, $, $$, on, modal, toast, monsterCard, detailHtml, loading } from './ui.js';

export default async function (root, _, { profile }) {
  const { items } = await api('/monsters', { query: { ids: profile.starter_pool.join(','), size: 10 } });
  const sel = new Set();
  mount(root, html`<h1>Choose your starters</h1>
    <p class="muted">Pick exactly 3 monsters from this pool. Press (i) to inspect one. This choice is final.</p>
    <div class="grid" id="grid">${items.map((m, i) => html`<div class="reveal" style="animation-delay:${i * 130}ms">
      ${monsterCard(m, html`<button class="btn small ibtn" data-info="${m.monster_id}">i</button>`)}</div>`)}</div>
    <div class="sticky-bar"><span id="count">0 / 3 selected</span><button class="btn primary" id="go" disabled>Confirm starters</button></div>`);
  const grid = $(root, '#grid');
  const go = $(root, '#go');
  on(grid, 'click', '.mon', (card, e) => {
    const info = e.target.closest('[data-info]');
    if (info) return void showInfo(+info.dataset.info);
    const id = +card.dataset.id;
    if (sel.has(id)) sel.delete(id);
    else if (sel.size < 3) sel.add(id);
    else return toast('You can only pick 3 starters', 'warn');
    $$(grid, '.mon').forEach(c => c.classList.toggle('picked', sel.has(+c.dataset.id)));
    $(root, '#count').textContent = `${sel.size} / 3 selected`;
    go.disabled = sel.size !== 3;
  });

  async function showInfo(id) {
    const m = modal(loading());
    try { m.body.innerHTML = detailHtml(await api('/monsters/' + id)).s; }
    catch (e) { m.close(); toast(errText(e), 'error'); }
  }

  go.onclick = async () => {
    go.disabled = true;
    try {
      const owned = await api('/players/me/starters', { method: 'POST', body: { monster_ids: [...sel] } });
      const m = modal(html`<h2>Welcome, tamer!</h2><div class="grid">${owned.map((o, i) => html`<div class="reveal" style="animation-delay:${i * 300}ms">${monsterCard(o)}</div>`)}</div>
        <button class="btn primary" id="start">Start the adventure</button>`);
      $(m.body, '#start').onclick = () => { m.close(); location.hash = '#/home'; };
    } catch (e) {
      toast(errText(e), 'error');
      go.disabled = false;
    }
  };
}
