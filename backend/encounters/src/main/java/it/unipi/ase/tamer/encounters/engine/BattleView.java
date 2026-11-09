package it.unipi.ase.tamer.encounters.engine;

import java.util.List;

/**
 * Read-only snapshot of a battle, with the fields of the "Battle state" JSON of the plan that the engine owns.
 * The service adds {@code encounter_id}, the wild {@code image_url} and fills {@code result} (reward, level-ups,
 * run) around {@link #outcome()} and {@link #xpGained()}.
 *
 * @param status    {@code in_progress} or {@code finished}
 * @param turn      number of rounds resolved
 * @param lastRound {@code null} before the first action
 * @param outcome   {@code null} while in progress, else {@code victory|defeat|fled}
 * @param xpGained  XP for every team member (0 unless victory)
 */
public record BattleView(String status, int turn, TemplateInfo template, WildView wild, List<TeamMemberView> team,
                         RoundResult lastRound, String outcome, int xpGained) {

    public static final String IN_PROGRESS = "in_progress";
    public static final String FINISHED = "finished";

    public BattleView {
        team = List.copyOf(team);
    }

    /** {@code ability} object of the wild monster. */
    public record AbilityView(String name, String description) {
    }

    public record WildView(int monsterId, String name, String element, String rarity, int level, int hp, int maxHp,
                           AbilityView ability) {
    }

    public record TeamMemberView(int position, String ownedId, int monsterId, String name, int level, String element,
                                 int hp, int maxHp, boolean active, boolean knockedOut) {
    }
}
