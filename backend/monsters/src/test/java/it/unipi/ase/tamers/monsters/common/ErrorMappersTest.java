package it.unipi.ase.tamers.monsters.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ErrorMappersTest {
    @Test
    void violationPathsAreReportedAsSnakeCaseFieldNames() {
        assertEquals("player_id", ErrorMappers.lastNode("add.request.playerId"));
        assertEquals("monster_ids[0]", ErrorMappers.lastNode("addBatch.request.monsterIds[0].<list element>"));
        assertEquals("xp", ErrorMappers.lastNode("xp.request.awards[1].xp"));
        assertEquals("source", ErrorMappers.lastNode("source"));
    }
}
