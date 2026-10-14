import { api, errText } from './api.js';
import { html, mount, $, on, debounce, toast, sprite, monsterCard, errorBox } from './ui.js';

const memberChip = m => html`<div class="chip">${sprite(m.image_url, 64)}<span>${m.name}</span><small>Lv ${m.level}</small></div>`;
const ORD = ['1st', '2nd', '3rd'];

export default async function (root) {
  let teams = [];
  let coll = [];
  let ed = null; // editor state: { id, name, ids, valid, errors }

  async function list() {
    ed = null;
    teams = await api('/teams');
    mount(root, html`<div class="row between"><h1>Teams</h1><button class="btn primary" id="new">New team</button></div>
      ${teams.length ? '' : html`<p class="muted">No teams yet. Create one to start fighting.</p>`}
      <div class="grid teams">${teams.map(t => html`<div class="card team" data-team="${t.team_id}">
        <div class="row between"><h3>${t.name}</h3><span class="badge ${t.valid ? 'ok' : 'bad'}">${t.valid ? 'valid' : 'invalid'}</span></div>
        <div class="chips">${t.members.map(memberChip)}</div>
        ${t.errors?.length ? html`<ul class="errors">${t.errors.map(e => html`<li>${e}</li>`)}</ul>` : ''}
        <div class="row"><button class="btn small" data-act="edit">Edit</button><button class="btn small danger" data-act="del">Delete</button></div></div>`)}</div>`);
    $(root, '#new').onclick = () => edit({ team_id: null, name: '', owned_ids: [] });
  }
  on(root, 'click', '[data-act]', async b => {
    const t = teams.find(x => x.team_id === b.closest('.team').dataset.team);
    if (b.dataset.act === 'edit') return edit(t);
    if (!confirm(`Delete team "${t.name}"?`)) return;
    try { await api('/teams/' + t.team_id, { method: 'DELETE' }); toast('Team deleted', 'ok'); await list(); }
    catch (e) { toast(errText(e), 'error'); }
  });

  async function edit(t) {
    ed = { id: t.team_id, name: t.name, ids: [...t.owned_ids], valid: false, errors: [] };
    mount(root, html`<p class="muted">Loading collection...</p>`);
    coll = await api('/collection');
    draw();
    validate();
  }

  const byId = id => coll.find(o => o.owned_id === id);
  function draw() {
    mount(root, html`<h1>${ed.id ? 'Edit team' : 'New team'}</h1>
      <label class="field">Team name<input id="tname" maxlength="30" value="${ed.name}"></label>
      <h3>Battle order</h3>
      <div class="slots">${[0, 1, 2].map(i => {
        const m = byId(ed.ids[i]);
        return m ? html`<div class="slot full r-${m.rarity}" data-slot="${i}"><small>${ORD[i]}</small>${sprite(m.image_url, 96, 'bob')}<b>${m.name}</b><small>Lv ${m.level}</small>
          <div class="row"><button class="btn small" data-mv="-1" ${i === 0 ? 'disabled' : ''}>&lt;</button><button class="btn small" data-mv="1" ${i === ed.ids.length - 1 ? 'disabled' : ''}>&gt;</button><button class="btn small danger" data-rm="1">x</button></div></div>`
          : html`<div class="slot"><small>${ORD[i]}</small><span class="muted">empty</span></div>`;
      })}</div>
      <div id="vbox" class="validation"></div>
      <div class="row"><button class="btn primary" id="save">Save team</button><button class="btn" id="cancel">Cancel</button></div>
      <h3>Your monsters (click to add / remove)</h3>
      <div class="grid" id="pool">${coll.map(o => monsterCard(o, '', ed.ids.includes(o.owned_id) ? 'picked' : ''))}</div>`);
    showValidation();
    $(root, '#tname').oninput = e => { ed.name = e.target.value; validate(); };
    $(root, '#cancel').onclick = list;
    $(root, '#save').onclick = save;
  }
  on(root, 'click', '.slot [data-mv],.slot [data-rm]', b => {
    const i = +b.closest('.slot').dataset.slot;
    if (b.dataset.rm) ed.ids.splice(i, 1);
    else { const j = i + +b.dataset.mv; [ed.ids[i], ed.ids[j]] = [ed.ids[j], ed.ids[i]]; }
    draw(); validate();
  });
  on(root, 'click', '#pool .mon', c => {
    const id = c.dataset.id;
    if (ed.ids.includes(id)) ed.ids = ed.ids.filter(x => x !== id);
    else if (ed.ids.length < 3) ed.ids.push(id);
    else return toast('A team holds up to 3 monsters', 'warn');
    draw(); validate();
  });

  function showValidation() {
    const box = $(root, '#vbox');
    if (!box) return;
    mount(box, ed.valid ? html`<span class="ok">Team is valid</span>`
      : html`<ul class="errors">${ed.errors.map(e => html`<li>${e}</li>`)}</ul>`);
    $(root, '#save').disabled = !ed.valid;
  }
  const validate = debounce(async () => {
    if (!ed) return;
    const snap = JSON.stringify([ed.name, ed.ids]);
    try {
      const r = await api('/teams/validate', { method: 'POST', body: { name: ed.name, owned_ids: ed.ids } });
      if (snap !== JSON.stringify([ed.name, ed.ids])) return;
      ed.valid = r.valid;
      ed.errors = r.errors;
    } catch (e) { ed.valid = false; ed.errors = [errText(e)]; }
    showValidation();
  }, 250);

  async function save() {
    const body = { name: ed.name, owned_ids: ed.ids };
    try {
      await api(ed.id ? '/teams/' + ed.id : '/teams', { method: ed.id ? 'PUT' : 'POST', body });
      toast('Team saved', 'ok');
      await list();
    } catch (e) { toast(errText(e), 'error'); }
  }

  try { await list(); } catch (e) { mount(root, errorBox(e)); }
}
