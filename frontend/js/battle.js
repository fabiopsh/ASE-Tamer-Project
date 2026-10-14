// Battle view + animation player: replays result.log event by event
import { api, state } from './api.js';
import { html, mount, $, $$, sprite, bar, elBadge, monsterCard } from './ui.js';

const BASE_MS = 600;

export default async function (root, runId) {
  let res = state.result?.run_id === runId ? state.result : null;
  if (!res) {
    try { res = await api('/encounters/runs/' + runId); }
    catch (e) { if (e.status === 401) throw e; res = (await api('/history/' + runId)).result; }
  }
  let stop = () => {};
  const start = () => { stop(); stop = play(root, res, start); };
  start();
  return () => stop();
}

function play(root, res, replay) {
  const T = res.team;
  const maxHp = { wild: res.enemy.max_hp };
  const hp = { wild: res.enemy.max_hp };
  T.forEach((m, i) => { maxHp['team:' + i] = hp['team:' + i] = m.max_hp; });
  let cur = 0, speed = 1, skipping = false, dead = false, wake = null;

  mount(root, html`<div class="row between"><h1>Stage ${res.stage} - ${res.kind}</h1><div class="row">
      <button class="btn small" id="spd">Speed x1</button><button class="btn small" id="skip">Skip</button></div></div>
    <div class="arena" id="arena">
      <div class="fighter foe" id="foe"></div><div class="fighter me" id="me"></div></div>
    <div class="roster" id="roster">${T.map((m, i) => html`<div class="rost" data-r="${i}">${sprite(m.image_url, 40)}</div>`)}</div>
    <div class="log" id="log"></div><div id="result"></div>`);
  const arena = $(root, '#arena'), foe = $(root, '#foe'), me = $(root, '#me'), log = $(root, '#log');

  const fighter = (el, m, hpKey) => {
    mount(el, html`<div class="info"><div class="row between"><b>${m.name}</b><span>Lv ${m.level}</span></div>
      ${elBadge(m.element)}<div class="hp">${bar(0, 1, 'hpbar')}<small class="hpt"></small></div></div>
      <div class="spr">${sprite(m.image_url, 224)}</div>`);
    el.dataset.who = hpKey;
    setHp(hpKey);
  };
  const elOf = who => (who === 'wild' ? foe : me);
  function setHp(who) {
    const el = elOf(who);
    if (el.dataset.who !== who) return;
    const p = maxHp[who] ? 100 * hp[who] / maxHp[who] : 0;
    const b = $(el, '.hpbar');
    $(b, 'i').style.width = p + '%';
    b.classList.toggle('mid', p <= 50 && p > 20);
    b.classList.toggle('low', p <= 20);
    $(el, '.hpt').textContent = `${hp[who]} / ${maxHp[who]}`;
  }
  const rost = i => $(root, `.rost[data-r="${i}"]`);
  const anim = (el, cls, ms = 600) => {
    if (!el) return;
    el.classList.remove(cls);
    void el.offsetWidth;
    el.classList.add(cls);
    setTimeout(() => el.classList.remove(cls), ms);
  };
  const spr = who => $(elOf(who), '.spr');
  function float(who, text, cls) {
    const f = document.createElement('div');
    f.className = 'float ' + cls;
    f.textContent = text;
    elOf(who).append(f);
    setTimeout(() => f.remove(), 1300);
  }
  const showMe = (i, fresh) => {
    cur = i;
    fighter(me, T[i], 'team:' + i);
    $$(root, '.rost').forEach(r => r.classList.toggle('active', +r.dataset.r === i));
    if (fresh) anim(spr('team:' + i), 'enter', 700);
  };
  fighter(foe, res.enemy, 'wild');
  showMe(0);

  const ensure = w => { if (w?.startsWith('team:') && +w.slice(5) !== cur) showMe(+w.slice(5), true); };
  function line(e) {
    const d = document.createElement('div');
    d.className = `line ${e.type} ${e.actor === 'wild' ? 'enemy' : 'ally'}`;
    d.textContent = (e.round != null ? `[${e.round}] ` : '') + (e.text || e.type);
    log.append(d);
    log.scrollTop = log.scrollHeight;
  }

  function step(e, fast) {
    ensure(e.actor);
    ensure(e.target);
    line(e);
    const before = e.actor ? hp[e.actor] : 0;
    if (e.actor && e.actor_hp != null) hp[e.actor] = e.actor_hp;
    if (e.target && e.target_hp != null) hp[e.target] = e.target_hp;
    if (e.actor) setHp(e.actor);
    if (e.target) setHp(e.target);
    const fainted = e.type === 'faint' ? (e.actor_hp === 0 ? e.actor : e.target_hp === 0 ? e.target : e.target || e.actor) : null;
    if (fainted?.startsWith('team:')) rost(+fainted.slice(5))?.classList.add('fainted');
    if (e.type === 'revive' && e.actor?.startsWith('team:')) rost(+e.actor.slice(5))?.classList.remove('fainted');
    if (fast) {
      if (fainted) spr(fainted)?.classList.add('fainted');
      if (e.type === 'revive') spr(e.actor)?.classList.remove('fainted');
      return;
    }
    switch (e.type) {
      case 'attack':
        anim(spr(e.actor), 'lunge', 450);
        setTimeout(() => {
          if (dead) return;
          anim(spr(e.target), 'hit', 450);
          if (e.critical) anim(arena, 'flash', 400);
        }, 220 / speed);
        break;
      case 'dodge': float(e.target, 'DODGE', 'info'); break;
      case 'ability': float(e.actor, e.ability || 'Ability', 'ability'); break;
      case 'heal': float(e.actor, `+${Math.max(0, hp[e.actor] - before)}`, 'heal'); break;
      case 'stun': float(e.target, 'STUNNED', 'info'); break;
      case 'skip': float(e.actor, 'SKIP', 'info'); break;
      case 'revive': spr(e.actor)?.classList.remove('fainted'); float(e.actor, 'REVIVE!', 'heal'); break;
      case 'faint': spr(fainted)?.classList.add('fainted'); break;
      case 'enter': anim(spr(e.actor), 'enter', 700); break;
    }
    if (e.damage > 0 && e.target) {
      float(e.target, (e.critical ? 'CRIT ' : '') + '-' + e.damage, e.critical ? 'dmg crit' : 'dmg');
      if (e.effectiveness === 'strong') float(e.target, 'Super effective!', 'eff strong');
      else if (e.effectiveness === 'weak') float(e.target, 'Not very effective...', 'eff weak');
    }
  }

  function showResult() {
    const r = res.rewards || {};
    const win = res.outcome === 'victory';
    mount($(root, '#result'), html`<div class="card result ${res.outcome}">
      <div class="banner ${win ? 'win' : 'lose'}">${win ? 'VICTORY!' : 'DEFEAT'}</div>
      <p class="muted center">${res.rounds} rounds</p>
      <h3>Experience</h3>
      <div class="xp-list">${(r.xp || []).map(x => {
        const m = T.find(t => t.owned_id === x.owned_id) || {};
        const up = x.new_level > x.old_level;
        return html`<div class="xp-row ${up ? 'levelup' : ''}">${m.image_url ? sprite(m.image_url, 48) : ''}<b>${m.name}</b>
          <span>+${x.xp} XP</span><span>Lv ${x.old_level}${up ? html` &rarr; <b>${x.new_level}</b> LEVEL UP!` : ''}</span></div>`;
      })}</div>
      ${r.captured ? html`<h3>Captured!</h3><div class="reveal capture">${monsterCard(r.captured)}</div>` : ''}
      ${r.stage_advanced ? html`<div class="banner win">Stage cleared! You advanced to stage ${r.new_stage}</div>` : ''}
      <div class="row center"><a class="btn primary" href="#/encounters">Encounters</a>
        <button class="btn" id="again">Replay battle</button><a class="btn" href="#/history">History</a></div></div>`);
    $(root, '#again').onclick = replay;
    $(root, '#result').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  const pause = ms => new Promise(r => { wake = r; setTimeout(r, ms); });
  $(root, '#skip').onclick = () => { skipping = true; wake?.(); };
  $(root, '#spd').onclick = e => { speed = speed === 1 ? 2 : 1; e.target.textContent = `Speed x${speed}`; };

  (async () => {
    await pause(500);
    for (const e of res.log) {
      if (dead) return;
      step(e, skipping);
      if (!skipping) await pause(BASE_MS / speed);
    }
    if (!dead) showResult();
  })();

  return () => { dead = true; wake?.(); };
}
