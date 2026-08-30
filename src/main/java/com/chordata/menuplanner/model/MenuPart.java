package com.chordata.menuplanner.model;

/**
 * One plate component of a planned menu: a recipe with an editable portion
 * size. {@code priceSnapshot} is the cost of that portion frozen at the last
 * save — the locked cell view shows snapshots, the editor shows live cost.
 */
public class MenuPart {

    private long recipeNumber;
    private String displayName;
    private Integer grams;
    private Double priceSnapshot;

    public MenuPart() {
    }

    public MenuPart(long recipeNumber, String displayName, Integer grams) {
        this.recipeNumber = recipeNumber;
        this.displayName = displayName;
        this.grams = grams;
    }

    public MenuPart copy() {
        MenuPart part = new MenuPart(recipeNumber, displayName, grams);
        part.priceSnapshot = priceSnapshot;
        return part;
    }

    public long getRecipeNumber() { return recipeNumber; }
    public void setRecipeNumber(long recipeNumber) { this.recipeNumber = recipeNumber; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Integer getGrams() { return grams; }
    public void setGrams(Integer grams) { this.grams = grams; }
    public Double getPriceSnapshot() { return priceSnapshot; }
    public void setPriceSnapshot(Double priceSnapshot) { this.priceSnapshot = priceSnapshot; }
}
