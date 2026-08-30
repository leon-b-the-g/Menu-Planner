package com.chordata.menuplanner.service;

import com.chordata.menuplanner.model.CellKey;
import com.chordata.menuplanner.model.Composition;
import com.chordata.menuplanner.model.MenuPart;
import com.chordata.menuplanner.model.PlannedMenu;
import com.chordata.menuplanner.model.Recipe;
import com.chordata.menuplanner.model.Slot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The application's datastore, standing in for the ERP database tables of the
 * original module ({@code mp_menu}, {@code mp_menu_part}, {@code mp_week_plan_cell},
 * {@code mp_composition}, tag assignments).
 *
 * <p>State lives in memory and is written to a JSON file on every mutation, so
 * plans survive restarts. On first launch the store is seeded with the catalog
 * defaults plus two pre-planned weeks, so the analytics have data to show.</p>
 */
public class PlanRepository {

    private final CatalogService catalog;
    private final File storeFile;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<Long, PlannedMenu> menusById = new LinkedHashMap<>();
    private final Map<CellKey, Long> cellAssignments = new HashMap<>();
    private final List<Composition> compositions = new ArrayList<>();
    private final Map<Long, Set<String>> tagsByRecipe = new HashMap<>();
    private long nextMenuId = 1;

    public PlanRepository(CatalogService catalog, File storeFile) {
        this.catalog = catalog;
        this.storeFile = storeFile;
    }

    /** Default store location: {@code ~/.menu-planner/plan.json}. */
    public static File defaultStoreFile() {
        return new File(new File(System.getProperty("user.home"), ".menu-planner"), "plan.json");
    }

    // ------------------------------------------------------------------
    // Menus
    // ------------------------------------------------------------------

    public List<PlannedMenu> getMenus() {
        return List.copyOf(menusById.values());
    }

    public Optional<PlannedMenu> getMenu(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(menusById.get(id));
    }

    public Optional<PlannedMenu> findMenuByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return menusById.values().stream()
                .filter(m -> name.trim().equalsIgnoreCase(m.getName()))
                .findFirst();
    }

    /**
     * Saves a menu under {@code name} with the given parts: reuses the menu of
     * the same name if one exists (updating its parts), otherwise registers a
     * new one. Part prices are snapshotted at the current catalog cost.
     */
    public PlannedMenu saveMenu(String name, List<MenuPart> parts) {
        PlannedMenu menu = findMenuByName(name).orElseGet(() -> {
            PlannedMenu m = new PlannedMenu(nextMenuId++, name.trim());
            menusById.put(m.getId(), m);
            return m;
        });
        menu.setName(name.trim());
        List<MenuPart> copies = new ArrayList<>();
        for (MenuPart part : parts) {
            MenuPart copy = part.copy();
            copy.setPriceSnapshot(currentPriceFor(copy));
            copies.add(copy);
        }
        menu.setParts(copies);
        menu.setPriceAsOf(LocalDate.now());
        persist();
        return menu;
    }

    /** Live cost of a part's current portion, or null when grams are missing. */
    public Double currentPriceFor(MenuPart part) {
        if (part.getGrams() == null) {
            return null;
        }
        return catalog.findRecipe(part.getRecipeNumber())
                .map(r -> r.costFor(part.getGrams()))
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Plan cells
    // ------------------------------------------------------------------

    public Map<CellKey, Long> getCellAssignments() {
        return Map.copyOf(cellAssignments);
    }

    public Optional<PlannedMenu> getMenuAt(CellKey key) {
        return getMenu(cellAssignments.get(key));
    }

    public void assignMenu(CellKey key, long menuId) {
        cellAssignments.put(key, menuId);
        persist();
    }

    public void clearCell(CellKey key) {
        cellAssignments.remove(key);
        persist();
    }

    /** All assignments in [from, to], useful for reports and exports. */
    public Map<CellKey, Long> getAssignmentsInRange(LocalDate from, LocalDate to) {
        Map<CellKey, Long> result = new TreeMap<>(Comparator
                .comparing(CellKey::date)
                .thenComparingLong(CellKey::slotId));
        cellAssignments.forEach((key, menuId) -> {
            if (!key.date().isBefore(from) && !key.date().isAfter(to)) {
                result.put(key, menuId);
            }
        });
        return result;
    }

    // ------------------------------------------------------------------
    // Compositions
    // ------------------------------------------------------------------

    public List<Composition> getCompositions() {
        return List.copyOf(compositions);
    }

    public boolean compositionExists(String compositionId) {
        return compositions.stream().anyMatch(c -> c.id().equals(compositionId));
    }

    public Optional<Composition> findComposition(String compositionId) {
        return compositions.stream().filter(c -> c.id().equals(compositionId)).findFirst();
    }

    public void addComposition(Composition composition) {
        compositions.add(composition);
        compositions.sort(Comparator.comparing(Composition::name, String.CASE_INSENSITIVE_ORDER));
        persist();
    }

    public void removeComposition(Composition composition) {
        compositions.remove(composition);
        persist();
    }

    // ------------------------------------------------------------------
    // Tag assignments
    // ------------------------------------------------------------------

    public Set<String> getTagsForRecipe(long recipeNumber) {
        return Set.copyOf(tagsByRecipe.getOrDefault(recipeNumber, Set.of()));
    }

    public void setTagAssigned(long recipeNumber, String tagName, boolean assigned) {
        Set<String> tags = tagsByRecipe.computeIfAbsent(recipeNumber, k -> new HashSet<>());
        if (assigned) {
            tags.add(tagName);
        } else {
            tags.remove(tagName);
        }
        persist();
    }

    // ------------------------------------------------------------------
    // Load / seed / persist
    // ------------------------------------------------------------------

    /** Loads the store file, or seeds a fresh dataset when none exists. */
    public void loadOrSeed() {
        if (storeFile.exists()) {
            try {
                applyDto(mapper.readValue(storeFile, StoreDto.class));
                return;
            } catch (IOException e) {
                System.err.println("[PLANNER] Could not read " + storeFile + " — reseeding: " + e.getMessage());
            }
        }
        seed();
        persist();
    }

    private void seed() {
        compositions.addAll(catalog.getSeedCompositions());
        compositions.sort(Comparator.comparing(Composition::name, String.CASE_INSENSITIVE_ORDER));
        catalog.getDefaultTagAssignments().forEach((number, tags) ->
                tagsByRecipe.put(number, new HashSet<>(tags)));

        // Pre-plan last week and this week so reports and similarity have content.
        List<Composition> veggie = compositions.stream()
                .filter(c -> c.recipeNumbers().stream().allMatch(this::isVeggieRecipe))
                .toList();
        List<Composition> hearty = compositions.stream()
                .filter(c -> !veggie.contains(c))
                .toList();
        List<Recipe> desserts = catalog.getRecipes().stream()
                .filter(r -> catalog.getDefaultTagAssignments()
                        .getOrDefault(r.number(), Set.of()).contains("Dessert"))
                .toList();

        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        int pick = 0;
        for (LocalDate monday : List.of(thisMonday.minusWeeks(1), thisMonday)) {
            for (int d = 0; d < 5; d++) {
                LocalDate date = monday.plusDays(d);
                for (Slot slot : catalog.getSlots()) {
                    if (!slot.availableOn(date.getDayOfWeek())) {
                        continue;
                    }
                    // Leave a few gaps so the issues report isn't empty.
                    if ((d + slot.id()) % 5 == 4) {
                        continue;
                    }
                    PlannedMenu menu;
                    if (slot.id() == 3) {
                        Recipe dessert = desserts.get(pick % desserts.size());
                        menu = seedMenuFromRecipes(dessert.name(), List.of(dessert));
                    } else {
                        List<Composition> pool = (slot.id() == 2) ? veggie : hearty;
                        Composition comp = pool.get(pick % pool.size());
                        menu = seedMenuFromComposition(comp);
                    }
                    pick++;
                    cellAssignments.put(new CellKey(date, slot.id()), menu.getId());
                }
            }
        }
    }

    private boolean isVeggieRecipe(long recipeNumber) {
        Set<String> tags = catalog.getDefaultTagAssignments().getOrDefault(recipeNumber, Set.of());
        return tags.contains("Vegetarian") || tags.contains("Vegan");
    }

    private PlannedMenu seedMenuFromComposition(Composition comp) {
        return findMenuByName(comp.name()).orElseGet(() -> {
            List<MenuPart> parts = new ArrayList<>();
            for (Long number : comp.recipeNumbers()) {
                catalog.findRecipe(number).ifPresent(r ->
                        parts.add(new MenuPart(r.number(), r.name(), r.defaultGrams())));
            }
            return saveMenuWithoutPersist(comp.name(), parts);
        });
    }

    private PlannedMenu seedMenuFromRecipes(String name, List<Recipe> recipes) {
        return findMenuByName(name).orElseGet(() -> {
            List<MenuPart> parts = new ArrayList<>();
            for (Recipe r : recipes) {
                parts.add(new MenuPart(r.number(), r.name(), r.defaultGrams()));
            }
            return saveMenuWithoutPersist(name, parts);
        });
    }

    private PlannedMenu saveMenuWithoutPersist(String name, List<MenuPart> parts) {
        PlannedMenu menu = new PlannedMenu(nextMenuId++, name);
        for (MenuPart part : parts) {
            part.setPriceSnapshot(currentPriceFor(part));
        }
        menu.setParts(parts);
        menu.setPriceAsOf(LocalDate.now());
        menusById.put(menu.getId(), menu);
        return menu;
    }

    private void persist() {
        try {
            File dir = storeFile.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                throw new IOException("Cannot create " + dir);
            }
            mapper.writeValue(storeFile, toDto());
        } catch (IOException e) {
            System.err.println("[PLANNER] Could not write " + storeFile + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // JSON DTOs (plain fields + ISO date strings, no Jackson modules needed)
    // ------------------------------------------------------------------

    private StoreDto toDto() {
        StoreDto dto = new StoreDto();
        for (PlannedMenu menu : menusById.values()) {
            MenuDto m = new MenuDto();
            m.id = menu.getId();
            m.name = menu.getName();
            m.priceAsOf = menu.getPriceAsOf() == null ? null : menu.getPriceAsOf().toString();
            for (MenuPart part : menu.getParts()) {
                PartDto p = new PartDto();
                p.recipeNumber = part.getRecipeNumber();
                p.displayName = part.getDisplayName();
                p.grams = part.getGrams();
                p.priceSnapshot = part.getPriceSnapshot();
                m.parts.add(p);
            }
            dto.menus.add(m);
        }
        cellAssignments.forEach((key, menuId) -> {
            CellDto c = new CellDto();
            c.date = key.date().toString();
            c.slotId = key.slotId();
            c.menuId = menuId;
            dto.cells.add(c);
        });
        for (Composition comp : compositions) {
            CompositionDto c = new CompositionDto();
            c.id = comp.id();
            c.name = comp.name();
            c.recipeNumbers = new ArrayList<>(comp.recipeNumbers());
            dto.compositions.add(c);
        }
        tagsByRecipe.forEach((number, tags) ->
                dto.tagsByRecipe.put(String.valueOf(number), new ArrayList<>(tags)));
        return dto;
    }

    private void applyDto(StoreDto dto) {
        menusById.clear();
        cellAssignments.clear();
        compositions.clear();
        tagsByRecipe.clear();
        nextMenuId = 1;

        for (MenuDto m : dto.menus) {
            PlannedMenu menu = new PlannedMenu(m.id, m.name);
            menu.setPriceAsOf(m.priceAsOf == null ? null : LocalDate.parse(m.priceAsOf));
            for (PartDto p : m.parts) {
                MenuPart part = new MenuPart(p.recipeNumber, p.displayName, p.grams);
                part.setPriceSnapshot(p.priceSnapshot);
                menu.getParts().add(part);
            }
            menusById.put(menu.getId(), menu);
            nextMenuId = Math.max(nextMenuId, menu.getId() + 1);
        }
        for (CellDto c : dto.cells) {
            cellAssignments.put(new CellKey(LocalDate.parse(c.date), c.slotId), c.menuId);
        }
        for (CompositionDto c : dto.compositions) {
            compositions.add(new Composition(c.id, c.name, List.copyOf(c.recipeNumbers)));
        }
        compositions.sort(Comparator.comparing(Composition::name, String.CASE_INSENSITIVE_ORDER));
        dto.tagsByRecipe.forEach((number, tags) ->
                tagsByRecipe.put(Long.parseLong(number), new HashSet<>(tags)));
    }

    public static class StoreDto {
        public List<MenuDto> menus = new ArrayList<>();
        public List<CellDto> cells = new ArrayList<>();
        public List<CompositionDto> compositions = new ArrayList<>();
        public Map<String, List<String>> tagsByRecipe = new LinkedHashMap<>();
    }

    public static class MenuDto {
        public long id;
        public String name;
        public String priceAsOf;
        public List<PartDto> parts = new ArrayList<>();
    }

    public static class PartDto {
        public long recipeNumber;
        public String displayName;
        public Integer grams;
        public Double priceSnapshot;
    }

    public static class CellDto {
        public String date;
        public long slotId;
        public Long menuId;
    }

    public static class CompositionDto {
        public String id;
        public String name;
        public List<Long> recipeNumbers = new ArrayList<>();
    }
}
