import { api, errText, state } from './api.js';
import { html, mount, $, on, toast, sprite, elBadge, rarBadge, pct } from './ui.js';

const KEY = 'mt_team';
const save = id => { try { localStorage.setItem(KEY, id); } catch { /* ignore */ } };
const saved = () => { try { return localStorage.getItem(KEY); } catch { return null; } };

export default async function (root) {
  const [board, allTeams] = await Promise.all([api('/encounters'), api('/teams')]);
  const teams = allTeams.filter(t => t.valid);
  const ids = teams.map(t => t.team_id);
  let teamId = ids.includes(saved()) ? saved() : ids[0];

  const card = o => {
    const e = o.enemy;
    const open = o.status === 'open';
    return html`<div class="card enc r-${e.rarity} ${o.kind} ${open ? '' : 'done'}" data-enc="${o.encounter_id}">
      <div class="row between"><span class="badge kind-${o.kind}">${o.kind}</span><span class="badge diff-${o.difficulty}">${o.difficulty}</span></div>
      ${sprite(e.image_url, 128, 'bob')}
      <div class="mon-name">${e.name}</div><div class="lvl">Lv ${e.level}</div>
      <div class="badges">${elBadge(e.element)}${rarBadge(e.rarity)}</div>
      <div class="mini-stats">HP ${e.stats.hp} ATK ${e.stats.attack} DEF ${e.stats.defense} SPD ${e.stats.speed}</div>
      <div class="mini-stats">${e.ability?.name || ''}</div>
      <div class="rewards">+${o.rewards.xp} XP - capture ${pct(o.rewards.capture_chance)}${o.rewards.advances_stage ? html` - <b>advances stage</b>` : ''}</div>
      <button class="btn primary start" ${open && teamId ? '' : 'disabled'}>${open ? 'Fight!' : 'Completed'}</button></div>`;
  };

  mount(root, html`<h1>Encounters</h1>
    <div class="card row between toolbar">
      <span>Stage <b>${board.game_completed ? 'MAX' : board.stage}</b></span>
      ${teams.length ? html`<label>Team <select id="team">${teams.map(t => html`<option value="${t.team_id}" ${t.team_id === teamId ? 'selected' : ''}>${t.name} (${t.members.map(m => m.name).join(', ')})</option>`)}</select></label>`
        : html`<span class="warn">No valid team. <a href="#/teams">Create one</a></span>`}</div>
    ${board.game_completed ? html`<div class="banner win">GAME COMPLETED - keep hunting wild monsters!</div>` : ''}
    <div class="grid enc-grid">${board.encounters.map(card)}</div>`);

  const sel = $(root, '#team');
  if (sel) sel.onchange = () => { teamId = sel.value; save(teamId); };
  on(root, 'click', '.start', async b => {
    b.disabled = true;
    b.textContent = 'Fighting...';
    try {
      const res = await api(`/encounters/${b.closest('.enc').dataset.enc}/start`, { method: 'POST', body: { team_id: teamId } });
      state.result = res;
      location.hash = '#/battle/' + res.run_id;
    } catch (e) {
      toast(errText(e), 'error');
      b.disabled = false;
      b.textContent = 'Fight!';
    }
  });
}
