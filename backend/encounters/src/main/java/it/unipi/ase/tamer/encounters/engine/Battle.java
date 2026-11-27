package it.unipi.ase.tamer.encounters.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Mutable battle aggregate: relay 1v1 between the team (in position order) and one wild monster.
 * {@link #apply(Action)} resolves one full round. Not thread-safe: the caller serialises the actions.
 *
 * <p>Round resolution:
 * <ol>
 *   <li>switch (the new monster does not attack this round) or flee ({@code p_flee} roll; success ends the
 *       battle as {@code fled});</li>
 *   <li>attacks: on {@code attack} both sides in {@code speed_eff} order (tie → RNG), otherwise only the wild;</li>
 *   <li>each attack: dodge roll (only if a dodge effect is active) → {@code var} roll (skipped when dodged) →
 *       damage → HP → KO check; a KO'd monster does not act later in the round;</li>
 *   <li>end: wild KO → {@code victory}; else active KO → first alive member by position ({@code auto_switch}),
 *       none → {@code defeat}.</li>
 * </ol>
 * RNG call order (part of {@link #ENGINE_VERSION}): wild rarity → species → level (in {@link WildSelector}),
 * then per round: flee roll (flee only) → tie-break (only when both attack with equal speed) →
 * per attack: dodge roll (if any) → var (if not dodged).
 */
public final class Battle {

    /** Version of the rules and of the RNG call order; stored in every history record. */
    public static final int ENGINE_VERSION = 2;
    public static final int MAX_TEAM_SIZE = 3;

    private final BattleRandom rng;
    private final EncounterSpec spec;
    private final ElementChart chart;
    private final AbilityEngine abilities = new AbilityEngine();
    private final List<Fighter> team;
    private final Fighter wild;
    private final List<RoundResult> log = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private Fighter active;
    private int turn;
    private Outcome outcome = Outcome.IN_PROGRESS;

    /**
     * @param rng  the battle RNG, already used by {@link WildSelector} for a normal template
     * @param spec template and wild monster
     * @param team 1–3 members in position order (index 0 = position 1); effective stats
     */
    public Battle(BattleRandom rng, EncounterSpec spec, List<Combatant> team, ElementChart chart) {
        this.rng = Objects.requireNonNull(rng, "rng");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.chart = Objects.requireNonNull(chart, "chart");
        Objects.requireNonNull(team, "team");
        if (team.isEmpty() || team.size() > MAX_TEAM_SIZE) {
            throw new IllegalArgumentException("A team has between 1 and " + MAX_TEAM_SIZE + " members.");
        }
        List<Fighter> members = new ArrayList<>();
        for (int i = 0; i < team.size(); i++) {
            Combatant c = Objects.requireNonNull(team.get(i), "team member");
            members.add(new Fighter(c, Fighter.TEAM, i + 1, HpCalculator.teamMaxHp(c.hp())));
        }
        this.team = List.copyOf(members);
        this.wild = new Fighter(spec.wild(), Fighter.WILD, 0, HpCalculator.wildMaxHp(spec.wild().hp(), spec.isBoss()));
        this.active = this.team.get(0);
    }

    /** Convenience for a fresh RNG (boss templates, tests). */
    public Battle(long seed, EncounterSpec spec, List<Combatant> team, ElementChart chart) {
        this(new BattleRandom(seed), spec, team, chart);
    }

    /** Resolves one round. Invalid action or finished battle → {@link InvalidActionException}, state unchanged. */
    public RoundResult apply(Action action) {
        Objects.requireNonNull(action, "action");
        if (isFinished()) {
            throw new InvalidActionException("The battle is already finished.");
        }
        Fighter switchTarget = null;
        if (action.type() == Action.Type.SWITCH) {
            int slot = action.slot();
            if (slot < 1 || slot > team.size()) {
                throw new InvalidActionException("Slot " + slot + " is not a team position.");
            }
            switchTarget = team.get(slot - 1);
            if (switchTarget == active) {
                throw new InvalidActionException("The monster in slot " + slot + " is already active.");
            }
            if (!switchTarget.alive()) {
                throw new InvalidActionException("The monster in slot " + slot + " is knocked out.");
            }
        }

        turn++;
        actions.add(action);
        List<BattleEvent> events = new ArrayList<>();

        if (switchTarget != null) {
            events.add(BattleEvent.switchMember(active.position, switchTarget.position));
            active = switchTarget;
        }
        markOnField(active);
        markOnField(wild);
        applyCopyAbility(active, wild, events);
        rollRandomBoosts(active, wild, events);

        if (action.type() == Action.Type.FLEE) {
            double p = FleeCalculator.probability(abilities.speedEff(active, wild, turn),
                    abilities.speedEff(wild, active, turn));
            boolean success = FleeCalculator.roll(p, rng);
            events.add(BattleEvent.flee(success));
            if (success) {
                return finish(Outcome.FLED, events);
            }
        }

        List<Fighter> order;
        if (action.type() == Action.Type.ATTACK) {
            boolean teamFirst = TurnOrder.teamFirst(abilities.speedEff(active, wild, turn),
                    abilities.speedEff(wild, active, turn), rng);
            order = teamFirst ? List.of(active, wild) : List.of(wild, active);
        } else {
            order = List.of(wild);
        }
        for (Fighter attacker : order) {
            if (!attacker.alive()) {
                continue;
            }
            Fighter target = attacker == wild ? active : wild;
            if (attacker.stunned) {
                String source = attacker.stunnedBy != null ? attacker.stunnedBy : "Stun";
                attacker.stunned = false;
                attacker.stunnedBy = null;
                events.add(BattleEvent.ability(attacker.side, source, "stun", attacker.combatant.name(),
                        attacker.combatant.name() + " is stunned and skips its action.", null));
                continue;
            }
            attack(attacker, target, events);
            if (!target.alive() || !attacker.alive()) {
                break;
            }
        }

        if (!wild.alive()) {
            return finish(Outcome.VICTORY, events);
        }
        if (!active.alive()) {
            Fighter next = team.stream().filter(Fighter::alive).findFirst().orElse(null);
            if (next == null) {
                return finish(Outcome.DEFEAT, events);
            }
            active = next;
            events.add(BattleEvent.autoSwitch(next.position));
        }
        return record(events);
    }

    private void markOnField(Fighter f) {
        if (f.firstRound == 0) {
            f.firstRound = turn;
        }
    }

    private void applyCopyAbility(Fighter teamFighter, Fighter wildFighter, List<BattleEvent> events) {
        Ability teamOrig = teamFighter.ability();
        Ability wildOrig = wildFighter.ability();
        if (teamOrig != null && abilities.shouldCopyAbility(teamOrig, teamFighter, wildFighter, turn)
                && wildOrig != null && !abilities.handler(wildOrig.effectType()).copiesAbility()) {
            teamFighter.copiedAbility = wildOrig;
            events.add(BattleEvent.ability(teamFighter.side, teamOrig.name(), "copy_ability",
                    teamFighter.combatant.name(),
                    teamFighter.combatant.name() + " copied " + wildOrig.name() + " from "
                            + wildFighter.combatant.name() + ".", null));
        }
        if (wildOrig != null && abilities.shouldCopyAbility(wildOrig, wildFighter, teamFighter, turn)
                && teamOrig != null && !abilities.handler(teamOrig.effectType()).copiesAbility()) {
            wildFighter.copiedAbility = teamOrig;
            events.add(BattleEvent.ability(wildFighter.side, wildOrig.name(), "copy_ability",
                    wildFighter.combatant.name(),
                    wildFighter.combatant.name() + " copied " + teamOrig.name() + " from "
                            + teamFighter.combatant.name() + ".", null));
        }
    }

    private void rollRandomBoosts(Fighter teamFighter, Fighter wildFighter, List<BattleEvent> events) {
        teamFighter.clearRandomBoost();
        wildFighter.clearRandomBoost();
        rollRandomBoost(teamFighter, wildFighter, events);
        rollRandomBoost(wildFighter, teamFighter, events);
    }

    private void rollRandomBoost(Fighter self, Fighter opponent, List<BattleEvent> events) {
        if (!self.alive() || !abilities.isRandomBoostActive(self, opponent, turn)) {
            return;
        }
        Ability a = self.ability();
        int pick = rng.nextInt(3);
        String stat = pick == 0 ? "attack" : (pick == 1 ? "defense" : "speed");
        self.randomBoostStat = stat;
        self.randomBoostFactor = abilities.handler(a.effectType()).randomBoostFactor(a.effectValue());
        events.add(BattleEvent.ability(self.side, a.name(), "random_boost", self.combatant.name(),
                self.combatant.name() + "'s " + stat + " increased by " + a.effectValue() + "% (" + a.name() + ").",
                a.effectValue()));
    }

    private void attack(Fighter attacker, Fighter defender, List<BattleEvent> events) {
        AbilityEngine.AttackModifiers m = abilities.attackModifiers(attacker, defender, turn);
        ElementChart.Effectiveness raw = chart.effectiveness(attacker.combatant.element(),
                defender.combatant.element());
        ElementChart.Effectiveness afterInvert = m.invertAdvantage() ? DamageCalculator.invert(raw) : raw;
        ElementChart.Effectiveness eff = DamageCalculator.effectiveness(chart, attacker.combatant.element(),
                defender.combatant.element(), m.invertAdvantage(), m.ignoreDisadvantage());
        boolean dodged = m.dodgeChance() > 0 && rng.nextDouble() < m.dodgeChance();
        int dealt = 0;
        List<String> used = new ArrayList<>();
        if (dodged) {
            used.add(defender.ability().name());
        } else {
            double variance = DamageCalculator.rollVariance(rng);
            double effectiveAttack = attacker.combatant.attack() * m.attackerAttackFactor() * m.attackFactor();
            double effectiveDefense = defender.combatant.defense() * m.defenseFactor();
            int damage = DamageCalculator.damage(effectiveAttack, effectiveDefense, eff.multiplier(), variance,
                    m.boost(), m.reduction());
            dealt = Math.min(damage, defender.hp);
            defender.hp -= dealt;
            if (m.boost() != 1.0 || m.attackerAttackFactor() != 1.0
                    || (m.ignoreDisadvantage() && afterInvert != eff)
                    || (m.attackerInverts() && !m.defenderInverts() && afterInvert != raw)) {
                used.add(attacker.ability().name());
            }
            if (m.attackFactor() != 1.0 || m.reduction() != 1.0 || m.defenseFactor() != 1.0
                    || (m.defenderInverts() && !m.attackerInverts() && afterInvert != raw)) {
                used.add(defender.ability().name());
            }
        }
        defender.attacksReceived++;
        events.add(BattleEvent.attack(attacker.side, attacker.combatant.name(), defender.combatant.name(), dealt,
                eff.wireName(), dodged, used));

        if (!defender.alive()) {
            tryRevive(defender, events);
        }
        if (defender.alive()) {
            tryHeal(defender, events);
        }
        if (!dodged && dealt > 0) {
            tryReflect(defender, attacker, dealt, events);
        }
        if (!dodged && dealt > 0 && defender.alive()) {
            tryStun(attacker, defender, events);
        }
        if (!defender.alive()) {
            events.add(BattleEvent.knockout(defender.side, defender == wild ? null : defender.position));
        }
    }

    private void tryRevive(Fighter fighter, List<BattleEvent> events) {
        if (fighter.revived) {
            return;
        }
        Ability a = fighter.ability();
        if (a == null) {
            return;
        }
        int pct = abilities.revivePercent(a);
        if (pct <= 0) {
            return;
        }
        fighter.revived = true;
        int restored = Math.min(fighter.maxHp, Math.max(1, (fighter.maxHp * pct) / 100));
        fighter.hp = restored;
        events.add(BattleEvent.ability(fighter.side, a.name(), "revive", fighter.combatant.name(),
                fighter.combatant.name() + " revived with " + restored + " HP (" + a.name() + ").", restored));
    }

    private void tryHeal(Fighter fighter, List<BattleEvent> events) {
        if (fighter.healed || !fighter.alive()) {
            return;
        }
        Ability a = fighter.ability();
        if (a == null) {
            return;
        }
        int pct = abilities.healPercent(a, fighter);
        if (pct <= 0) {
            return;
        }
        fighter.healed = true;
        int rawHeal = Math.max(1, (fighter.maxHp * pct) / 100);
        int healed = Math.min(fighter.maxHp - fighter.hp, rawHeal);
        fighter.hp += healed;
        events.add(BattleEvent.ability(fighter.side, a.name(), "heal", fighter.combatant.name(),
                fighter.combatant.name() + " restored " + healed + " HP (" + a.name() + ").", healed));
    }

    private void tryReflect(Fighter defender, Fighter attacker, int dealt, List<BattleEvent> events) {
        Ability a = defender.ability();
        if (a == null || !attacker.alive()) {
            return;
        }
        int pct = abilities.reflectPercent(a);
        if (pct <= 0) {
            return;
        }
        int rawReflect = Math.max(1, (dealt * pct) / 100);
        int reflectDealt = Math.min(rawReflect, attacker.hp);
        attacker.hp -= reflectDealt;
        events.add(BattleEvent.ability(defender.side, a.name(), "reflect", defender.combatant.name(),
                defender.combatant.name() + " reflected " + reflectDealt + " damage to "
                        + attacker.combatant.name() + " (" + a.name() + ").", reflectDealt));
        if (!attacker.alive()) {
            tryRevive(attacker, events);
        }
        if (attacker.alive()) {
            tryHeal(attacker, events);
        }
        if (!attacker.alive()) {
            events.add(BattleEvent.knockout(attacker.side, attacker == wild ? null : attacker.position));
        }
    }

    private void tryStun(Fighter attacker, Fighter defender, List<BattleEvent> events) {
        Ability a = attacker.ability();
        if (a == null) {
            return;
        }
        double chance = abilities.stunChance(a);
        if (chance > 0 && rng.nextDouble() < chance) {
            defender.stunned = true;
            defender.stunnedBy = a.name();
            events.add(BattleEvent.ability(attacker.side, a.name(), "stun", attacker.combatant.name(),
                    attacker.combatant.name() + " stunned " + defender.combatant.name() + " (" + a.name() + ").",
                    null));
        }
    }

    private RoundResult finish(Outcome result, List<BattleEvent> events) {
        outcome = result;
        events.add(BattleEvent.end(result));
        return record(events);
    }

    private RoundResult record(List<BattleEvent> events) {
        RoundResult round = new RoundResult(turn, events);
        log.add(round);
        return round;
    }

    // ---- read side -------------------------------------------------------------------------------------------

    public long seed() {
        return rng.seed();
    }

    public Outcome outcome() {
        return outcome;
    }

    public boolean isFinished() {
        return outcome != Outcome.IN_PROGRESS;
    }

    /** Number of rounds resolved. */
    public int turn() {
        return turn;
    }

    public EncounterSpec spec() {
        return spec;
    }

    /** Team inputs in position order. */
    public List<Combatant> teamInputs() {
        return team.stream().map(f -> f.combatant).toList();
    }

    /** Position (1-based) of the active team member. */
    public int activePosition() {
        return active.position;
    }

    /** Full turn log: one entry per resolved round, in order. */
    public List<RoundResult> log() {
        return Collections.unmodifiableList(log);
    }

    /** Actions applied so far (valid ones only), in order: with the seed and the inputs they replay the battle. */
    public List<Action> actions() {
        return Collections.unmodifiableList(actions);
    }

    /** {@code null} before the first round. */
    public RoundResult lastRound() {
        return log.isEmpty() ? null : log.get(log.size() - 1);
    }

    /** XP given to every team member: {@link XpCalculator#victoryXp} on victory, else 0. */
    public int xpGained() {
        return outcome == Outcome.VICTORY
                ? XpCalculator.victoryXp(wild.combatant.level(), wild.combatant.rarity())
                : 0;
    }

    public BattleView view() {
        Combatant w = wild.combatant;
        BattleView.AbilityView ability = w.ability() == null ? null
                : new BattleView.AbilityView(w.ability().name(), w.ability().description());
        BattleView.WildView wildView = new BattleView.WildView(w.monsterId(), w.name(), w.element(), w.rarity(),
                w.level(), wild.hp, wild.maxHp, ability);
        List<BattleView.TeamMemberView> members = team.stream()
                .map(f -> new BattleView.TeamMemberView(f.position, f.combatant.id(), f.combatant.monsterId(),
                        f.combatant.name(), f.combatant.level(), f.combatant.element(), f.hp, f.maxHp,
                        f == active && !isFinishedWithTeamDown(), !f.alive()))
                .toList();
        return new BattleView(isFinished() ? BattleView.FINISHED : BattleView.IN_PROGRESS, turn, spec.template(),
                wildView, members, lastRound(), isFinished() ? outcome.wireName() : null, xpGained());
    }

    private boolean isFinishedWithTeamDown() {
        return outcome == Outcome.DEFEAT;
    }
}
