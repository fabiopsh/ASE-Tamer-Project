package it.unipi.ase.tamer.monsters.api.dto;

import java.util.List;
import java.util.Map;

public record ElementsResponse(List<String> cycle, Map<String, String> strongAgainst,
                               Map<String, String> weakAgainst) {
}
