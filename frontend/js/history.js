import { api, errText, state } from './api.js';
import { html, mount, $, on, modal, toast, loading, sprite, elBadge, fmtDate, pager } from './ui.js';

export default async function (root) {
  let page = 0;
  let outcome = '';
  mount(root, html`<div class="row between"><h1>History</h1><span id="verify" class="badge">checking...</span></div>
    <form class="filters"><label>Outcome<select name="outcome"><option value="">All</option><option>victory</option><option>defeat</option></select></label></form>
    <div id="list"></div>`);
  const list = $(root, '#list');

  api('/history/verify').then(v => {
    const b = $(root, '#verify');
    if (!b) return;
    b.className = 'badge ' + (v.valid ? 'ok' : 'bad');
    b.textContent = v.valid ? `Save verified ✓ (${v.records} records)` : `Warning: record #${v.first_invalid_sequence} was tampered with`;
  }).catch(() => { const b = $(root, '#verify'); if (b) b.textContent = 'verification unavailable'; });

  const load = async () => {
    mount(list, loading());
    try {
      const r = await api('/history', { query: { outcome, page, size: 10 } });
      mount(list, html`${r.items.length ? '' : html`<p class="muted">No battles yet.</p>`}
        <div class="rows">${r.items.map(h => html`<div class="card hrow ${h.outcome}" data-run="${h.run_id}">
          <span class="badge ${h.outcome === 'victory' ? 'ok' : 'bad'}">${h.outcome}</span>
          <div class="chips">${h.team.map(m => sprite(m.image_url, 48))}<b>vs</b>${sprite(h.enemy.image_url, 48)}</div>
          <div class="hinfo"><b>${h.enemy.name}</b> Lv ${h.enemy.level}<br><small>Stage ${h.stage} - ${h.kind} - ${h.rounds} rounds - ${fmtDate(h.occurred_at)}</small></div>
          ${h.captured ? html`<div class="cap" title="Captured ${h.captured.name}">${sprite(h.captured.image_url, 40)}<small>captured</small></div>` : ''}</div>`)}</div>
        ${pager(r.page, r.total_pages)}`);
    } catch (e) { mount(list, errorBox(e)); }
  };
  $(root, 'form').onchange = e => { outcome = e.target.value; page = 0; load(); };
  on(list, 'click', '[data-page]', b => { page = +b.dataset.page; load(); });
  on(list, 'click', '.hrow', async row => {
    const m = modal(loading());
    try {
      const d = await api('/history/' + row.dataset.run);
      const r = d.result;
      m.body.innerHTML = html`<h2>${d.outcome === 'victory' ? 'Victory' : 'Defeat'} - Stage ${d.stage} ${d.kind}</h2>
        <p class="muted">#${d.sequence} - ${fmtDate(d.occurred_at)} - ${d.rounds} rounds</p>
        <div class="vs"><div><h3>Your team</h3><div class="chips">${r.team.map(t => html`<div class="chip">${sprite(t.image_url, 64)}<span>${t.name}</span><small>Lv ${t.level}</small></div>`)}</div></div>
          <div><h3>Enemy</h3><div class="chip">${sprite(r.enemy.image_url, 96)}<span>${r.enemy.name}</span><small>Lv ${r.enemy.level}</small>${elBadge(r.enemy.element)}</div></div></div>
        <div class="hash">hash ${d.hash}<br>prev ${d.prev_hash}</div>
        <button class="btn primary" id="replay">Replay battle</button>`.s;
      $(m.body, '#replay').onclick = () => { state.result = r; m.close(); location.hash = '#/battle/' + d.run_id; };
    } catch (e) { m.close(); toast(errText(e), 'error'); }
  });
  await load();
}
