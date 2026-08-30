package com.chordata.menuplanner.service;

import com.chordata.menuplanner.model.CellKey;
import com.chordata.menuplanner.model.PlannedMenu;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan analytics: per-cell similarity scores and the repetition report.
 * Pure functions over the repository state — no JavaFX dependencies.
 */
public class AnalyticsService {

    /**
     * Delivery days (Mon–Fri) in the 4-calendar-week similarity window — the
     * fixed denominator of the similarity score.
     */
    public static final int SIMILARITY_WINDOW_DAYS = 20;

    /** Scores at or above this fraction render as a warning badge. */
    public static final double SIMILARITY_WARN = 0.15;

    private final PlanRepository repository;

    public AnalyticsService(PlanRepository repository) {
        this.repository = repository;
    }

    /**
     * Similarity score per assigned cell of the visible week: how often the
     * cell's menu is planned within the 4-week window centred on the cell's
     * day (13 days back, 14 ahead, the cell itself counts), divided by the
     * window's {@value #SIMILARITY_WINDOW_DAYS} delivery days. Centred rather
     * than forward-looking, so a meal already planned in previous weeks raises
     * the score of the newly saved cell too.
     */
    public Map<CellKey, Double> similarityScores(LocalDate weekMonday) {
        Map<CellKey, Double> scores = new HashMap<>();
        if (weekMonday == null) {
            return scores;
        }
        LocalDate rangeStart = weekMonday.minusDays(13);
        LocalDate rangeEnd = weekMonday.plusDays(4).plusDays(14);
        Map<CellKey, Long> planned = repository.getAssignmentsInRange(rangeStart, rangeEnd);

        for (Map.Entry<CellKey, Long> entry : repository
                .getAssignmentsInRange(weekMonday, weekMonday.plusDays(4)).entrySet()) {
            CellKey key = entry.getKey();
            Long menuId = entry.getValue();
            LocalDate windowStart = key.date().minusDays(13);
            LocalDate windowEnd = key.date().plusDays(14);

            int occurrences = 0;
            for (Map.Entry<CellKey, Long> p : planned.entrySet()) {
                if (!menuId.equals(p.getValue())) {
                    continue;
                }
                LocalDate d = p.getKey().date();
                if (!d.isBefore(windowStart) && !d.isAfter(windowEnd)) {
                    occurrences++;
                }
            }
            scores.put(key, (double) occurrences / SIMILARITY_WINDOW_DAYS);
        }
        return scores;
    }

    /** One row of the repetition report. */
    public record RepetitionRow(PlannedMenu menu, int count, int deliveryDays) {

        /** Share of delivery days in the range this menu was planned on. */
        public double share() {
            return deliveryDays > 0 ? (double) count / deliveryDays : 0.0;
        }
    }

    /**
     * Counts how often each menu is planned in [from, to], most-used first.
     * The share denominator is the number of delivery days (Mon–Fri) in range.
     */
    public List<RepetitionRow> repetitionReport(LocalDate from, LocalDate to) {
        List<RepetitionRow> rows = new ArrayList<>();
        if (from == null || to == null || to.isBefore(from)) {
            return rows;
        }
        Map<Long, Integer> counts = new HashMap<>();
        repository.getAssignmentsInRange(from, to)
                .forEach((key, menuId) -> counts.merge(menuId, 1, Integer::sum));

        int deliveryDays = countDeliveryDays(from, to);
        counts.forEach((menuId, count) -> repository.getMenu(menuId)
                .ifPresent(menu -> rows.add(new RepetitionRow(menu, count, deliveryDays))));
        rows.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return rows;
    }

    /** Delivery days (Mon–Fri) in [from, to]. */
    public static int countDeliveryDays(LocalDate from, LocalDate to) {
        int days = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                days++;
            }
        }
        return days;
    }
}
