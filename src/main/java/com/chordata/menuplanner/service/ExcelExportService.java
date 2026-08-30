package com.chordata.menuplanner.service;

import com.chordata.menuplanner.model.CellKey;
import com.chordata.menuplanner.model.MenuPart;
import com.chordata.menuplanner.model.PlannedMenu;
import com.chordata.menuplanner.model.Slot;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exports planned weeks to an Excel workbook — one sheet per week, slot rows
 * by Mon–Fri columns, mirroring the printed meal plan template. Menu texts are
 * regenerated from the parts at export time, with the effective allergen codes
 * appended per component.
 */
public class ExcelExportService {

    private static final String[] WEEKDAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CatalogService catalog;
    private final PlanRepository repository;

    public ExcelExportService(CatalogService catalog, PlanRepository repository) {
        this.catalog = catalog;
        this.repository = repository;
    }

    /** Writes one sheet per week for every Monday in [fromMonday, toMonday]. */
    public void export(File file, LocalDate fromMonday, LocalDate toMonday) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(file)) {
            Styles styles = new Styles(workbook);
            for (LocalDate monday = fromMonday; !monday.isAfter(toMonday); monday = monday.plusWeeks(1)) {
                writeWeekSheet(workbook, styles, monday);
            }
            workbook.write(out);
        }
    }

    private void writeWeekSheet(XSSFWorkbook workbook, Styles styles, LocalDate monday) {
        WeekFields wf = WeekFields.ISO;
        int week = monday.get(wf.weekOfWeekBasedYear());
        XSSFSheet sheet = workbook.createSheet("CW " + week);

        int rowNum = 0;
        Row title = sheet.createRow(rowNum++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Meal Plan — calendar week " + week + "  ("
                + monday.format(FULL_FORMAT) + " – " + monday.plusDays(4).format(FULL_FORMAT) + ")");
        titleCell.setCellStyle(styles.title);
        rowNum++;

        Row header = sheet.createRow(rowNum++);
        Cell corner = header.createCell(0);
        corner.setCellValue("");
        corner.setCellStyle(styles.header);
        for (int d = 0; d < WEEKDAYS.length; d++) {
            Cell cell = header.createCell(d + 1);
            cell.setCellValue(WEEKDAYS[d] + " " + monday.plusDays(d).format(DAY_FORMAT));
            cell.setCellStyle(styles.header);
        }

        Map<CellKey, Long> assignments =
                repository.getAssignmentsInRange(monday, monday.plusDays(4));

        for (Slot slot : catalog.getSlots()) {
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(48);
            Cell slotCell = row.createCell(0);
            slotCell.setCellValue(slot.name());
            slotCell.setCellStyle(styles.slot);
            for (int d = 0; d < WEEKDAYS.length; d++) {
                LocalDate date = monday.plusDays(d);
                Cell cell = row.createCell(d + 1);
                cell.setCellStyle(styles.body);
                if (!slot.availableOn(date.getDayOfWeek())) {
                    cell.setCellValue("—");
                    continue;
                }
                Long menuId = assignments.get(new CellKey(date, slot.id()));
                repository.getMenu(menuId).ifPresent(menu -> cell.setCellValue(exportText(menu)));
            }
        }

        sheet.setColumnWidth(0, 18 * 256);
        for (int d = 1; d <= WEEKDAYS.length; d++) {
            sheet.setColumnWidth(d, 32 * 256);
        }
    }

    /**
     * Printed text of one menu: each component with its allergen codes in
     * brackets, regenerated from the catalog at export time so the
     * compliance-critical codes are always current.
     */
    String exportText(PlannedMenu menu) {
        StringBuilder sb = new StringBuilder();
        for (MenuPart part : menu.getParts()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(part.getDisplayName());
            Set<String> codes = allergensFor(part);
            if (!codes.isEmpty()) {
                sb.append(" (").append(String.join(",", codes)).append(")");
            }
        }
        return sb.isEmpty() ? menu.getName() : sb.toString();
    }

    private Set<String> allergensFor(MenuPart part) {
        return catalog.findRecipe(part.getRecipeNumber())
                .map(r -> (Set<String>) new LinkedHashSet<>(r.allergens()))
                .orElse(Set.of());
    }

    private static class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle slot;
        final CellStyle body;

        Styles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            title = workbook.createCellStyle();
            title.setFont(titleFont);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            header = workbook.createCellStyle();
            header.setFont(boldFont);
            header.setAlignment(HorizontalAlignment.CENTER);

            slot = workbook.createCellStyle();
            slot.setFont(boldFont);
            slot.setVerticalAlignment(VerticalAlignment.CENTER);

            body = workbook.createCellStyle();
            body.setWrapText(true);
            body.setVerticalAlignment(VerticalAlignment.CENTER);
        }
    }
}
