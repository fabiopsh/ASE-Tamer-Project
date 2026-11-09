package it.unipi.ase.tamer.encounters.engine;

import java.util.Objects;

/** The template fields shown in the battle state ({@code template} object of the API). */
public record TemplateInfo(int templateId, String name, int stage, String element, boolean isBoss) {

    public TemplateInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(element, "element");
    }
}
