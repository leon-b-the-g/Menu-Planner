package com.chordata.menuplanner.model;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * A structural row of the week plan (e.g. "Menu 1", "Dessert"). A slot may
 * only be offered on some weekdays; unavailable cells render disabled.
 */
public record Slot(long id, String name, Set<DayOfWeek> availableDays) {

    public boolean availableOn(DayOfWeek day) {
        return availableDays.contains(day);
    }
}
