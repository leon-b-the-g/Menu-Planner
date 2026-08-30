package com.chordata.menuplanner.model;

import java.util.List;

/**
 * A bulk-kitchen recipe from the recipe backend — the atomic building block
 * of every planned menu.
 *
 * @param number       stable recipe number from the source system
 * @param name         display name
 * @param defaultGrams default portion size when the recipe is added to a menu
 * @param costPer100g  current ingredient cost per 100 g in EUR
 * @param allergens    allergen codes (EU letter scheme: A gluten, C eggs, G milk, ...)
 */
public record Recipe(long number, String name, int defaultGrams,
                     double costPer100g, List<String> allergens) {

    /** Cost of a portion of {@code grams} at the current per-100g rate. */
    public double costFor(int grams) {
        return costPer100g * grams / 100.0;
    }

    @Override
    public String toString() {
        return name;
    }
}
