import { html, mount, sprite, spriteUrl, bar } from './ui.js';

export default function (root, _, { profile: p }) {
  const pr = p.progress || {};
  const v = x => x ?? '-';
  const stage = p.game_completed ? p.max_stage : p.stage;
  mount(root, html`<section class="card hero">
      <div class="avatar">${p.avatar_monster_id ? sprite(spriteUrl(p.avatar_monster_id), 128, 'bob') : html`<div class="noavatar">?</div>`}</div>
      <div class="hero-info"><h1>${p.display_name}</h1>
        <div class="stage-line">Stage <b>${stage}</b> / ${p.max_stage}</div>${bar(stage, p.max_stage, 'stage')}
        ${p.game_completed ? html`<div class="banner win">GAME COMPLETED - you are a true Monster Tamer!</div>` : ''}</div></section>
    <div class="grid stats-grid">
      <div class="card num"><b>${v(pr.collection_size)}</b>Monsters owned</div>
      <div class="card num"><b>${v(pr.species_owned)}</b>Species owned</div>
      <div class="card num"><b>${v(pr.encounters_total)}</b>Encounters</div>
      <div class="card num"><b>${v(pr.encounters_won)}</b>Victories</div></div>
    <h2>Quick links</h2>
    <div class="grid links">${[['encounters', 'Fight', 'Face wild monsters and the stage guardian'], ['teams', 'Teams', 'Build your battle squad'],
      ['collection', 'Collection', 'Your monsters'], ['catalog', 'Catalog', 'Every species in the game'], ['history', 'History', 'Past battles and replays']]
      .map(([h, t, d]) => html`<a class="card link" href="#/${h}"><b>${t}</b><span>${d}</span></a>`)}</div>`);
}
