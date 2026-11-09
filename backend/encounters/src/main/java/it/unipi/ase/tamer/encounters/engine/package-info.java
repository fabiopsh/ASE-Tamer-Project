/**
 * Battle engine (EN-2): pure Java, no Quarkus, no DB. Rules in {@code docs/plan/07-encounters.md}.
 *
 * <h2>Starting a battle</h2>
 * <pre>{@code
 * BattleRandom rng = new BattleRandom(seed);                 // one RNG per battle, seed from SecureRandom
 * // normal template: choose the wild monster with the SAME rng (boss template: skip, the species is fixed)
 * WildSelector.WildPick pick = WildSelector.select(rng, catalogue, template.element(), bossIds,
 *         rarityWeights, levelMin, levelMax);
 * Combatant wild = new Combatant(null, pick.monsterId(), name, element, pick.rarity(), pick.level(),
 *         hp, attack, defense, speed, ability);              // effective stats at pick.level() from monsters
 * List<Combatant> team = ...;                                // position order, id = owned_id, effective stats
 * Battle battle = new Battle(rng, new EncounterSpec(templateInfo, wild), team, ElementChart.defaults());
 * }</pre>
 *
 * <h2>Playing</h2>
 * {@link it.unipi.ase.tamer.encounters.engine.Battle#apply(Action)} with {@code Action.attack()},
 * {@code Action.switchTo(slot)} or {@code Action.flee()} resolves one round and returns its
 * {@link it.unipi.ase.tamer.encounters.engine.RoundResult}. An illegal action (switch to the active or a KO'd
 * member, slot out of range, any action once {@code isFinished()}) throws
 * {@link it.unipi.ase.tamer.encounters.engine.InvalidActionException} and leaves the state unchanged
 * (map it to {@code 400 INVALID_ACTION}). {@code Action.Type.fromWire("attack")} parses the DTO value.
 *
 * <h2>Reading</h2>
 * <ul>
 *   <li>{@code battle.view()}: {@link it.unipi.ase.tamer.encounters.engine.BattleView} with status, turn,
 *       template, wild, team, last_round and outcome; the service adds {@code encounter_id}, the wild
 *       {@code image_url} and the {@code result} object (reward, level-ups, run).</li>
 *   <li>{@code battle.outcome()}, {@code isFinished()}, {@code seed()}, {@code xpGained()} (XP for every team
 *       member, 0 unless victory).</li>
 *   <li>{@code battle.log()}: every round with its events (types {@code attack}, {@code switch},
 *       {@code auto_switch}, {@code knockout}, {@code flee}, {@code end}); serialise with {@code NON_NULL}.</li>
 * </ul>
 *
 * <h2>What to persist in the history record</h2>
 * {@link it.unipi.ase.tamer.encounters.engine.Battle#ENGINE_VERSION}, {@code seed()}, the inputs
 * ({@code spec()}, {@code teamInputs()}), {@code actions()}, {@code log()} and the outcome. Seed + inputs +
 * actions replay the battle exactly with the same engine version.
 *
 * <p>Abilities: P1 effects are implemented in {@link it.unipi.ase.tamer.encounters.engine.AbilityEngine};
 * P2 effects and the triggers {@code on_hit}, {@code on_attack}, {@code once} are no-ops in version 1.
 */
package it.unipi.ase.tamer.encounters.engine;
