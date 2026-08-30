package com.chordata.menuplanner.model;

import java.time.LocalDate;

/**
 * Address of one plan cell: a service date plus a slot.
 */
public record CellKey(LocalDate date, long slotId) {
}
