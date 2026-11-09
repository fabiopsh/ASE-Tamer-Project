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
    public static final int ENGINE_VERSION = 1;
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
            attack(attacker, target, events);
            if (!target.alive()) {
                events.add(BattleEvent.knockout(target.side, target == wild ? null : target.position));
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

    private void attack(Fighter attacker, Fighter defender, List<BattleEvent> events) {
        AbilityEngine.AttackModifiers m = abilities.attackModifiers(attacker, defender, turn);
        ElementChart.Effectiveness raw = chart.effectiveness(attacker.combatant.element(),
                defender.combatant.element());
        ElementChart.Effectiveness eff = DamageCalculator.effectiveness(chart, attacker.combatant.element(),
                defender.combatant.element(), m.ignoreDisadvantage());
        boolean dodged = m.dodgeChance() > 0 && rng.nextDouble() < m.dodgeChance();
        int dealt = 0;
        List<String> used = new ArrayList<>();
        if (dodged) {
            used.add(defender.ability().name());
        } else {
            double variance = DamageCalculator.rollVariance(rng);
            int damage = DamageCalculator.damage(attacker.combatant.attack() * m.attackFactor(),
                    defender.combatant.defense(), eff.multiplier(), variance, m.boost(), m.reduction());
            dealt = Math.min(damage, defender.hp);
            defender.hp -= dealt;
            if (m.boost() != 1.0 || (m.ignoreDisadvantage() && raw != eff)) {
                used.add(attacker.ability().name());
            }
            if (m.attackFactor() != 1.0 || m.reduction() != 1.0) {
                used.add(defender.ability().name());
            }
        }
        defender.attacksReceived++;
        events.add(BattleEvent.attack(attacker.side, attacker.combatant.name(), defender.combatant.name(), dealt,
                eff.wireName(), dodged, used));
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
