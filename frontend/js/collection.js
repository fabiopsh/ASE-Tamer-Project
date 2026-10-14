import { api, errText } from './api.js';
import { html, mount, $, on, modal, toast, loading, monsterCard, detailHtml, select, ELEMENTS, RARITIES, errorBox } from './ui.js';

export default async function (root) {
  mount(root, html`<h1>Collection</h1>
    <form class="filters">${select('element', 'Element', ELEMENTS)}${select('rarity', 'Rarity', RARITIES)}
      <label>Sort<select name="sort"><option value="level">Level</option><option value="recent">Recent</option><option value="species">Species</option></select></label></form>
    <div id="list"></div>`);
  const form = $(root, 'form');
  const list = $(root, '#list');
  const load = async () => {
    mount(list, loading());
    try {
      const items = await api('/collection', { query: Object.fromEntries(new FormData(form)) });
      mount(list, items.length ? html`<div class="grid">${items.map(m => monsterCard(m))}</div>` : html`<p class="muted">No monsters match.</p>`);
    } catch (e) { mount(list, errorBox(e)); }
  };
  form.onchange = load;
  on(list, 'click', '.mon', async card => {
    const m = modal(loading());
    try { m.body.innerHTML = detailHtml(await api('/collection/' + card.dataset.id)).s; }
    catch (e) { m.close(); toast(errText(e), 'error'); }
  });
  await load();
}
