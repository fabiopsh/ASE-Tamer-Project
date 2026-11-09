package it.unipi.ase.tamer.encounters.api.dto;

import it.unipi.ase.tamer.encounters.engine.BattleView;
import it.unipi.ase.tamer.encounters.engine.RoundResult;
import it.unipi.ase.tamer.encounters.engine.TemplateInfo;
import java.util.List;
import java.util.UUID;

/**
 * Battle state (docs/plan/07-encounters.md, "Battle state"): the engine view plus {@code encounter_id}, the
 * sprites and {@code result}. {@code status}: {@code in_progress | finished}; {@code last_round} is {@code null}
 * before the first action; {@code result} is {@code null} until the battle is finished. Events inside
 * {@code last_round} omit the fields that do not belong to their type.
 */
public record BattleStateResponse(UUID encounterId, String status, int turn, TemplateInfo template, WildState wild,
                                  List<TeamMemberState> team, RoundResult lastRound, BattleResultResponse result) {

    /** The wild monster with its battle HP. */
    public record WildState(int monsterId, String name, String element, String rarity, int level, int hp, int maxHp,
                            String imageUrl, BattleView.AbilityView ability) {
    }

    /** A team member with its battle HP; exactly one is {@code active} while the battle is in progress. */
    public record TeamMemberState(int position, String ownedId, int monsterId, String name, int level, String element,
                                  int hp, int maxHp, boolean active, boolean knockedOut, String imageUrl) {
    }

    /** {@code /images/monsters/NNN.png}, served by monsters through the gateway. */
    public static String imageUrl(int monsterId) {
        return String.format("/images/monsters/%03d.png", monsterId);
    }

    public static BattleStateResponse of(UUID encounterId, BattleView view, BattleResultResponse result) {
        BattleView.WildView w = view.wild();
        WildState wild = new WildState(w.monsterId(), w.name(), w.element(), w.rarity(), w.level(), w.hp(), w.maxHp(),
                imageUrl(w.monsterId()), w.ability());
        List<TeamMemberState> team = view.team().stream()
                .map(m -> new TeamMemberState(m.position(), m.ownedId(), m.monsterId(), m.name(), m.level(),
                        m.element(), m.hp(), m.maxHp(), m.active(), m.knockedOut(), imageUrl(m.monsterId())))
                .toList();
        return new BattleStateResponse(encounterId, view.status(), view.turn(), view.template(), wild, team,
                view.lastRound(), result);
    }
}
