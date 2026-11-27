import { describe, expect, it } from 'vitest';
import type { BattleEvent } from '../api/types';
import { describeEvent, describeRound, namesFrom } from './battleText';

const names = namesFrom('Chronos', [
  { position: 1, name: 'Lumen' },
  { position: 2, name: 'Vesper' },
]);

describe('lib/battleText', () => {
  it('formats attack events (normal, super effective, weak, dodged, abilities)', () => {
    expect(
      describeEvent(
        {
          type: 'attack',
          side: 'team',
          attacker: 'Lumen',
          target: 'Chronos',
          damage: 42,
          effectiveness: 'strong',
          dodged: false,
          abilities: ['Solar Flare'],
        },
        names,
      ),
    ).toEqual({
      text: 'Lumen attacks Chronos for 42 damage.',
      tone: 'strong',
      detail: "It's super effective! Solar Flare took effect.",
      damage: 42,
    });

    expect(
      describeEvent(
        {
          type: 'attack',
          side: 'wild',
          attacker: 'Chronos',
          target: 'Lumen',
          damage: 0,
          effectiveness: 'neutral',
          dodged: true,
        },
        names,
      ),
    ).toEqual({
      text: "Lumen dodged Chronos's attack!",
      tone: 'miss',
      detail: undefined,
    });
  });

  it('formats all EN-4 complex ability events cleanly', () => {
    const cases: Array<{ event: BattleEvent; expectedText: string; expectedTone: string; heal?: number; damage?: number }> = [
      {
        event: { type: 'ability', side: 'team', ability: 'Regenerate', effect: 'heal', monster: 'Lumen', amount: 18 },
        expectedText: 'Lumen recovered 18 HP with Regenerate.',
        expectedTone: 'heal',
        heal: 18,
      },
      {
        event: { type: 'ability', side: 'team', ability: 'Mirror Shell', effect: 'reflect', target: 'Chronos', amount: 12 },
        expectedText: 'Mirror Shell reflected 12 damage back to Chronos!',
        expectedTone: 'ability',
        damage: 12,
      },
      {
        event: { type: 'ability', side: 'team', ability: 'Time Lock', effect: 'stun', target: 'Chronos' },
        expectedText: 'Chronos was stunned by Time Lock and cannot act!',
        expectedTone: 'ability',
      },
      {
        event: { type: 'ability', side: 'team', ability: 'Phoenix Echo', effect: 'revive', monster: 'Lumen', amount: 35 },
        expectedText: 'Lumen revived with 35 HP thanks to Phoenix Echo!',
        expectedTone: 'heal',
        heal: 35,
      },
      {
        event: { type: 'ability', side: 'wild', ability: 'Entropy', effect: 'random_boost', monster: 'Chronos', stat: 'attack', amount: 30 },
        expectedText: "Entropy boosted Chronos's attack (+30%)!",
        expectedTone: 'ability',
      },
      {
        event: { type: 'ability', side: 'team', ability: 'Eternal Archive', effect: 'copy_ability', monster: 'Lumen', copied: 'Time Lock' },
        expectedText: 'Lumen copied Time Lock with Eternal Archive!',
        expectedTone: 'ability',
      },
      {
        event: { type: 'ability', side: 'wild', ability: 'Paradox Engine', effect: 'invert_advantage' },
        expectedText: 'Paradox Engine inverted elemental advantages!',
        expectedTone: 'ability',
      },
    ];

    for (const c of cases) {
      const line = describeEvent(c.event, names);
      expect(line).not.toBeNull();
      expect(line?.text).toBe(c.expectedText);
      expect(line?.tone).toBe(c.expectedTone);
      if (c.heal !== undefined) expect(line?.heal).toBe(c.heal);
      if (c.damage !== undefined) expect(line?.damage).toBe(c.damage);
    }
  });

  it('formats knockout, switch, auto_switch, flee, and end events', () => {
    expect(describeEvent({ type: 'knockout', side: 'wild' }, names)).toEqual({
      text: 'Chronos was knocked out!',
      tone: 'ko',
    });
    expect(describeEvent({ type: 'knockout', side: 'team', position: 1 }, names)).toEqual({
      text: 'Lumen was knocked out!',
      tone: 'ko',
    });
    expect(describeEvent({ type: 'switch', from: 1, to: 2 }, names)).toEqual({
      text: 'You call back Lumen and send out Vesper.',
      tone: 'switch',
    });
    expect(describeEvent({ type: 'auto_switch', to: 2 }, names)).toEqual({
      text: 'Vesper steps into the fight!',
      tone: 'switch',
    });
    expect(describeEvent({ type: 'flee', success: true }, names)).toEqual({
      text: 'You got away safely!',
      tone: 'flee',
    });
    expect(describeEvent({ type: 'flee', success: false }, names)).toEqual({
      text: "You tried to flee, but couldn't escape!",
      tone: 'flee',
    });
    expect(describeEvent({ type: 'end', outcome: 'victory' }, names)).toEqual({
      text: 'Victory! Chronos has been defeated.',
      tone: 'end',
    });
    expect(describeEvent({ type: 'end', outcome: 'defeat' }, names)).toEqual({
      text: 'Defeat… your whole team has fallen.',
      tone: 'ko',
    });
  });

  it('describes a full round in event order', () => {
    const lines = describeRound(
      {
        turn: 1,
        events: [
          { type: 'flee', success: false },
          { type: 'knockout', side: 'team', position: 1 },
          { type: 'auto_switch', to: 2 },
        ],
      },
      names,
    );
    expect(lines).toHaveLength(3);
    expect(lines.map((l) => l.tone)).toEqual(['flee', 'ko', 'switch']);
  });
});
