import { api, errText } from './api.js';
import { html, mount, $, on, debounce, modal, toast, loading, monsterCard, detailHtml, select, pager, ELEMENTS, RARITIES, errorBox } from './ui.js';

export default async function (root) {
  let page = 0;
  mount(root, html`<h1>Catalog</h1>
    <form class="filters">${select('element', 'Element', ELEMENTS)}${select('rarity', 'Rarity', RARITIES)}
      <label>Name<input name="q" placeholder="search..." autocomplete="off"></label></form>
    <div id="list"></div>`);
  const form = $(root, 'form');
  const list = $(root, '#list');
  const load = async () => {
    mount(list, loading());
    try {
      const r = await api('/monsters', { query: { ...Object.fromEntries(new FormData(form)), page, size: 20 } });
      mount(list, html`${r.items.length ? html`<div class="grid">${r.items.map(m => monsterCard(m))}</div>` : html`<p class="muted">No monsters found.</p>`}
        ${pager(r.page, r.total_pages)}<p class="muted center">${r.total} monsters</p>`);
    } catch (e) { mount(list, errorBox(e)); }
  };
  const reset = () => { page = 0; load(); };
  form.onsubmit = e => { e.preventDefault(); reset(); };
  on(form, 'change', 'select', reset);
  $(form, '[name=q]').oninput = debounce(reset, 350);
  on(list, 'click', '[data-page]', b => { page = +b.dataset.page; load(); scrollTo(0, 0); });
  on(list, 'click', '.mon', async card => {
    const m = modal(loading());
    try { m.body.innerHTML = detailHtml(await api('/monsters/' + card.dataset.id)).s; }
    catch (e) { m.close(); toast(errText(e), 'error'); }
  });
  await load();
}
