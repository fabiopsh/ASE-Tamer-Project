package it.unipi.ase.tamers.encounters.engine;

import java.util.List;

public record BattleResult(Outcome outcome, int rounds, List<BattleEvent> log) {
    public boolean victory() {
        return outcome == Outcome.VICTORY;
    }
}
