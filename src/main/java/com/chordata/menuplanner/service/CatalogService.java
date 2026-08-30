package com.chordata.menuplanner.service;

import com.chordata.menuplanner.model.Composition;
import com.chordata.menuplanner.model.Recipe;
import com.chordata.menuplanner.model.Slot;
import com.chordata.menuplanner.model.Tag;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Synthetic stand-in for the recipe backend of the original ERP system.
 * Serves a fixed catalog of recipes (with portion defaults, per-100g costs and
 * allergen codes), the tag vocabulary, default tag assignments, seed
 * compositions and the slot layout of the week plan.
 */
public class CatalogService {

    /** EU allergen letter codes used in the synthetic data. */
    private static final String GLUTEN = "A";
    private static final String EGGS = "C";
    private static final String FISH = "D";
    private static final String SOY = "F";
    private static final String MILK = "G";
    private static final String CELERY = "L";
    private static final String MUSTARD = "M";

    private final List<Recipe> recipes = new ArrayList<>();
    private final Map<Long, Recipe> recipesByNumber = new LinkedHashMap<>();
    private final List<Tag> tags = new ArrayList<>();
    private final Map<String, Tag> tagsByName = new LinkedHashMap<>();
    private final Map<Long, Set<String>> defaultTagAssignments = new LinkedHashMap<>();
    private final List<Composition> seedCompositions = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();

    public CatalogService() {
        buildTags();
        buildRecipes();
        buildSeedCompositions();
        buildSlots();
    }

    public List<Recipe> getRecipes() { return List.copyOf(recipes); }
    public List<Tag> getTags() { return List.copyOf(tags); }
    public List<Composition> getSeedCompositions() { return List.copyOf(seedCompositions); }
    public List<Slot> getSlots() { return List.copyOf(slots); }

    public Optional<Recipe> findRecipe(long number) {
        return Optional.ofNullable(recipesByNumber.get(number));
    }

    public Optional<Tag> findTag(String name) {
        return Optional.ofNullable(tagsByName.get(name));
    }

    /** Default recipe → tag-name assignments, applied on first launch. */
    public Map<Long, Set<String>> getDefaultTagAssignments() {
        Map<Long, Set<String>> copy = new LinkedHashMap<>();
        defaultTagAssignments.forEach((k, v) -> copy.put(k, Set.copyOf(v)));
        return copy;
    }

    // ------------------------------------------------------------------
    // Catalog data
    // ------------------------------------------------------------------

    private void buildTags() {
        tag("Vegetarian", "#4D7C5F");
        tag("Vegan", "#65A30D");
        tag("Poultry", "#D9A21B");
        tag("Beef", "#A85B2C");
        tag("Fish", "#3B7EA1");
        tag("Pasta", "#B0813B");
        tag("Rice", "#8A8F5C");
        tag("Potato", "#96703D");
        tag("Soup", "#C4553B");
        tag("Dessert", "#B05BA1");
        tag("Whole grain", "#6E7F4E");
        tag("Kid favorite", "#84CC16");
    }

    private void tag(String name, String color) {
        Tag t = new Tag(name, color);
        tags.add(t);
        tagsByName.put(name, t);
    }

    private void buildRecipes() {
        // Mains
        recipe(3001, "Spaghetti with Tomato Sauce", 320, 0.42, List.of(GLUTEN), "Pasta", "Vegetarian", "Kid favorite");
        recipe(3002, "Whole Grain Penne Bolognese", 330, 0.68, List.of(GLUTEN, CELERY), "Pasta", "Beef", "Whole grain");
        recipe(3003, "Chicken Fricassee", 220, 0.95, List.of(GLUTEN, MILK), "Poultry");
        recipe(3004, "Breaded Fish Fillet", 180, 1.10, List.of(GLUTEN, FISH), "Fish");
        recipe(3005, "Vegetable Curry", 280, 0.55, List.of(), "Vegan", "Vegetarian");
        recipe(3006, "Beef Goulash", 240, 1.25, List.of(CELERY), "Beef");
        recipe(3007, "Potato Gratin", 300, 0.48, List.of(MILK), "Potato", "Vegetarian");
        recipe(3008, "Chicken Nuggets", 160, 0.92, List.of(GLUTEN, EGGS), "Poultry", "Kid favorite");
        recipe(3009, "Lentil Dal", 280, 0.38, List.of(), "Vegan", "Vegetarian");
        recipe(3010, "Cheese Spaetzle", 320, 0.62, List.of(GLUTEN, EGGS, MILK), "Pasta", "Vegetarian");
        recipe(3011, "Meatballs in Gravy", 200, 0.88, List.of(GLUTEN, EGGS, MUSTARD), "Beef", "Kid favorite");
        recipe(3012, "Salmon in Dill Sauce", 170, 1.65, List.of(FISH, MILK), "Fish");
        recipe(3013, "Vegetable Stir-Fry with Tofu", 290, 0.58, List.of(SOY), "Vegan", "Vegetarian");
        recipe(3014, "Turkey Steak", 180, 1.05, List.of(), "Poultry");
        recipe(3015, "Falafel Balls", 190, 0.52, List.of(), "Vegan", "Vegetarian");

        // Sides
        recipe(3101, "Steamed Rice", 200, 0.18, List.of(), "Rice", "Vegan");
        recipe(3102, "Mashed Potatoes", 230, 0.25, List.of(MILK), "Potato", "Vegetarian");
        recipe(3103, "Buttered Noodles", 210, 0.30, List.of(GLUTEN, MILK), "Pasta", "Vegetarian");
        recipe(3104, "Roast Potatoes", 220, 0.28, List.of(), "Potato", "Vegan");
        recipe(3105, "Whole Grain Rice", 200, 0.22, List.of(), "Rice", "Whole grain", "Vegan");
        recipe(3106, "Steamed Broccoli", 140, 0.35, List.of(), "Vegan");
        recipe(3107, "Glazed Carrots", 140, 0.30, List.of(MILK), "Vegetarian");
        recipe(3108, "Cucumber Salad", 110, 0.26, List.of(), "Vegan");
        recipe(3109, "Mixed Leaf Salad", 90, 0.32, List.of(MUSTARD), "Vegan");
        recipe(3110, "Couscous", 200, 0.24, List.of(GLUTEN), "Vegan", "Whole grain");

        // Soups
        recipe(3201, "Tomato Soup", 250, 0.32, List.of(CELERY), "Soup", "Vegan", "Vegetarian");
        recipe(3202, "Potato Soup", 250, 0.30, List.of(CELERY, MILK), "Soup", "Potato", "Vegetarian");
        recipe(3203, "Carrot Ginger Soup", 250, 0.34, List.of(), "Soup", "Vegan");

        // Desserts
        recipe(3301, "Vanilla Pudding", 150, 0.28, List.of(MILK), "Dessert", "Vegetarian", "Kid favorite");
        recipe(3302, "Apple Compote", 140, 0.24, List.of(), "Dessert", "Vegan");
        recipe(3303, "Chocolate Mousse", 130, 0.45, List.of(MILK, EGGS), "Dessert", "Vegetarian");
        recipe(3304, "Fruit Salad", 150, 0.42, List.of(), "Dessert", "Vegan");
        recipe(3305, "Semolina Porridge", 180, 0.26, List.of(GLUTEN, MILK), "Dessert", "Vegetarian");
        recipe(3306, "Berry Quark", 150, 0.38, List.of(MILK), "Dessert", "Vegetarian");
    }

    private void recipe(long number, String name, int defaultGrams, double costPer100g,
                        List<String> allergens, String... tagNames) {
        Recipe r = new Recipe(number, name, defaultGrams, costPer100g, allergens);
        recipes.add(r);
        recipesByNumber.put(number, r);
        defaultTagAssignments.put(number, Set.of(tagNames));
    }

    private void buildSeedCompositions() {
        seedComposition("Spaghetti Napoli Lunch", 3001, 3109);
        seedComposition("Bolognese Whole Grain Plate", 3002, 3109);
        seedComposition("Chicken Fricassee & Rice", 3003, 3101, 3107);
        seedComposition("Fish Friday Classic", 3004, 3102, 3108);
        seedComposition("Vegetable Curry Bowl", 3005, 3105);
        seedComposition("Goulash & Noodles", 3006, 3103, 3106);
        seedComposition("Nugget Day", 3008, 3104, 3108);
        seedComposition("Lentil Dal & Couscous", 3009, 3110);
        seedComposition("Cheese Spaetzle Plate", 3010, 3109);
        seedComposition("Meatball Monday", 3011, 3102, 3107);
        seedComposition("Salmon Plate", 3012, 3101, 3106);
        seedComposition("Falafel Bowl", 3015, 3110, 3108);
    }

    private void seedComposition(String name, long... recipeNumbers) {
        List<Long> numbers = new ArrayList<>();
        for (long n : recipeNumbers) {
            numbers.add(n);
        }
        seedCompositions.add(new Composition(Composition.idFor(numbers), name, numbers));
    }

    private void buildSlots() {
        Set<DayOfWeek> monToFri = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        slots.add(new Slot(1, "Menu 1", monToFri));
        slots.add(new Slot(2, "Menu 2 (Veggie)", monToFri));
        slots.add(new Slot(3, "Dessert", Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)));
    }
}
