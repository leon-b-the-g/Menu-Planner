package com.chordata.menuplanner.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A saved menu: a name plus its component parts. Menus live in a registry and
 * can be assigned to any number of plan cells, which is what the repetition
 * analytics count.
 */
public class PlannedMenu {

    private long id;
    private String name;
    private List<MenuPart> parts = new ArrayList<>();
    /** Date the parts' price snapshots were last taken. */
    private LocalDate priceAsOf;

    public PlannedMenu() {
    }

    public PlannedMenu(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<MenuPart> getParts() { return parts; }
    public void setParts(List<MenuPart> parts) { this.parts = parts; }
    public LocalDate getPriceAsOf() { return priceAsOf; }
    public void setPriceAsOf(LocalDate priceAsOf) { this.priceAsOf = priceAsOf; }
}
