package it.unipi.ase.tamer.monsters.api.dto;

import java.math.BigDecimal;

public record GrowthBlock(BigDecimal hp, BigDecimal attack, BigDecimal defense, BigDecimal speed) {
}
