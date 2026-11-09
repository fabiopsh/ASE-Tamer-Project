package it.unipi.ase.tamer.encounters.engine;

import java.util.List;

/** The events of one resolved round ({@code last_round} of the battle state; one entry of the turn log). */
public record RoundResult(int turn, List<BattleEvent> events) {

    public RoundResult {
        events = List.copyOf(events);
    }
}
