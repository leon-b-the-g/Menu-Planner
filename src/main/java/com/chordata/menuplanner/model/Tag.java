package com.chordata.menuplanner.model;

/**
 * A colored label recipes can carry (diet, cuisine, main component, ...).
 * Tags drive the library's AND/OR filter and render as colored chips.
 */
public record Tag(String name, String color) {

    @Override
    public String toString() {
        return name;
    }
}
