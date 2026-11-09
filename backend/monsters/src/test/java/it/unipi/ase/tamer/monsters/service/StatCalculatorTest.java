package it.unipi.ase.tamer.monsters.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StatCalculatorTest {

    @Test
    void floorsFractionalGrowth() {
        assertEquals(38, StatCalculator.effective(38, new BigDecimal("1.50"), 1));
        assertEquals(42, StatCalculator.effective(38, new BigDecimal("1.50"), 4));
    }

    @Test
    void rejectsInvalidLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> StatCalculator.effective(40, new BigDecimal("1.50"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> StatCalculator.effective(40, new BigDecimal("1.50"), 51));
    }
}
