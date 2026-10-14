import { api, errText, state, bus } from './api.js';
import { html, mount, $, $$, on, toast, sprite, loading } from './ui.js';

export default async function (root, _, { profile: p }) {
  mount(root, loading());
  const owned = await api('/collection');
  const species = [...new Map(owned.map(o => [o.monster_id, o])).values()];
  let avatar = p.avatar_monster_id;
  mount(root, html`<h1>Profile</h1>
    <form class="card form">
      <label>Display name<input name="display_name" value="${p.display_name}" maxlength="20" required></label>
      <h3>Avatar</h3>
      <div class="avatars">${species.map(o => html`<button type="button" class="avatar-opt ${o.monster_id === avatar ? 'picked' : ''}" data-mid="${o.monster_id}" title="${o.name}">${sprite(o.image_url, 64)}</button>`)}</div>
      <div class="error" role="alert"></div>
      <button class="btn primary">Save</button></form>`);
  const form = $(root, 'form');
  on(form, 'click', '.avatar-opt', b => {
    avatar = +b.dataset.mid;
    $$(form, '.avatar-opt').forEach(x => x.classList.toggle('picked', x === b));
  });
  form.onsubmit = async e => {
    e.preventDefault();
    const body = { display_name: form.display_name.value };
    if (avatar != null) body.avatar_monster_id = avatar;
    try {
      state.profile = await api('/players/me', { method: 'PATCH', body });
      bus.dispatchEvent(new Event('profile'));
      $(form, '.error').textContent = '';
      toast('Profile saved', 'ok');
    } catch (err) { $(form, '.error').textContent = errText(err); }
  };
}
