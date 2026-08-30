package com.chordata.menuplanner.model;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A named combination of recipes ("Schnitzel + mashed potatoes + gravy").
 * Its id is derived from the sorted recipe numbers, which makes duplicate
 * combinations detectable regardless of selection order.
 */
public record Composition(String id, String name, List<Long> recipeNumbers) {

    /** Derives the canonical composition id: sorted recipe numbers joined with "-". */
    public static String idFor(List<Long> recipeNumbers) {
        return recipeNumbers.stream()
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining("-"));
    }

    @Override
    public String toString() {
        return name;
    }
}
