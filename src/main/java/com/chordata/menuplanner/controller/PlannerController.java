package com.chordata.menuplanner.controller;

import com.chordata.menuplanner.model.CellKey;
import com.chordata.menuplanner.model.Composition;
import com.chordata.menuplanner.model.MenuPart;
import com.chordata.menuplanner.model.PlannedMenu;
import com.chordata.menuplanner.model.Recipe;
import com.chordata.menuplanner.model.Slot;
import com.chordata.menuplanner.service.AnalyticsService;
import com.chordata.menuplanner.service.CatalogService;
import com.chordata.menuplanner.service.ExcelExportService;
import com.chordata.menuplanner.service.PlanRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Planner tab: the week grid (slot rows × Mon–Fri) with drag-and-drop
 * building blocks, an in-cell menu editor with live cost totals, similarity
 * badges on saved cells, the issues + repetition reports, and Excel export.
 */
public class PlannerController {

    // Toolbar
    @FXML private Button buttonPrevWeek;
    @FXML private Button buttonNextWeek;
    @FXML private Button buttonThisWeek;
    @FXML private DatePicker datePickerWeek;
    @FXML private Label labelWeek;
    @FXML private Button buttonExport;

    // Building blocks
    @FXML private TextField textFieldCompSearch;
    @FXML private ListView<Composition> listViewCompositions;
    @FXML private TextField textFieldRecipeSearch;
    @FXML private ListView<Recipe> listViewRecipes;

    // Grid + reports
    @FXML private GridPane gridPlan;
    @FXML private VBox vboxIssues;
    @FXML private DatePicker datePickerRepFrom;
    @FXML private DatePicker datePickerRepTo;
    @FXML private VBox vboxRepetition;

    private static final String TOKEN_COMPOSITION = "C";
    private static final String TOKEN_RECIPE = "R";
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String[] WEEKDAY_LABELS = {"Mon", "Tue", "Wed", "Thu", "Fri"};

    private Stage stage;
    private CatalogService catalog;
    private PlanRepository repository;
    private AnalyticsService analytics;
    private ExcelExportService exporter;

    private final ObservableList<Composition> compositionItems = FXCollections.observableArrayList();
    private FilteredList<Composition> filteredCompositions;
    private final ObservableList<Recipe> recipeItems = FXCollections.observableArrayList();
    private FilteredList<Recipe> filteredRecipes;

    private LocalDate weekMonday;
    private Map<CellKey, Double> similarityScores = Map.of();

    /** Transient editor state of cells currently being edited (not yet saved). */
    private final Map<CellKey, EditingCell> editingCells = new java.util.HashMap<>();

    private static class EditingCell {
        String name = "";
        final List<MenuPart> parts = new ArrayList<>();
    }

    public void postInitialize(Stage stage, CatalogService catalog, PlanRepository repository,
                               AnalyticsService analytics, ExcelExportService exporter) {
        this.stage = stage;
        this.catalog = catalog;
        this.repository = repository;
        this.analytics = analytics;
        this.exporter = exporter;

        weekMonday = mondayOf(LocalDate.now());
        initializeToolbar();
        initializeBuildingBlocks();
        initializeRepetitionReport();
        rebuildAll();
    }

    // ------------------------------------------------------------------
    // Toolbar / week navigation
    // ------------------------------------------------------------------

    private void initializeToolbar() {
        applyIsoDateFormat(datePickerWeek);
        datePickerWeek.setValue(weekMonday);
        datePickerWeek.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                LocalDate monday = mondayOf(n);
                if (!monday.equals(weekMonday)) {
                    weekMonday = monday;
                    rebuildAll();
                }
                if (!monday.equals(n)) {
                    datePickerWeek.setValue(monday);
                }
            }
        });

        buttonPrevWeek.setOnAction(e -> shiftWeek(-1));
        buttonNextWeek.setOnAction(e -> shiftWeek(1));
        buttonThisWeek.setOnAction(e -> {
            weekMonday = mondayOf(LocalDate.now());
            datePickerWeek.setValue(weekMonday);
            rebuildAll();
        });
        buttonExport.setOnAction(e -> exportExcel());
    }

    private void shiftWeek(int weeks) {
        weekMonday = weekMonday.plusWeeks(weeks);
        datePickerWeek.setValue(weekMonday);
        rebuildAll();
    }

    private void updateWeekLabel() {
        WeekFields wf = WeekFields.ISO;
        labelWeek.setText("CW " + weekMonday.get(wf.weekOfWeekBasedYear())
                + "  ·  " + weekMonday.format(FULL_FORMAT)
                + " – " + weekMonday.plusDays(4).format(FULL_FORMAT));
    }

    // ------------------------------------------------------------------
    // Building blocks (draggable pills)
    // ------------------------------------------------------------------

    private void initializeBuildingBlocks() {
        filteredCompositions = new FilteredList<>(compositionItems, c -> true);
        listViewCompositions.setItems(filteredCompositions);
        listViewCompositions.setCellFactory(lv -> new PillCell<>(this::compositionPillText,
                comp -> TOKEN_COMPOSITION + "|" + comp.id()));

        filteredRecipes = new FilteredList<>(recipeItems, r -> true);
        listViewRecipes.setItems(filteredRecipes);
        listViewRecipes.setCellFactory(lv -> new PillCell<>(this::recipePillText,
                recipe -> TOKEN_RECIPE + "|" + recipe.number()));

        textFieldCompSearch.textProperty().addListener((obs, o, n) -> {
            String query = n == null ? "" : n.toLowerCase(Locale.ROOT).trim();
            filteredCompositions.setPredicate(c ->
                    query.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(query));
        });
        textFieldRecipeSearch.textProperty().addListener((obs, o, n) -> {
            String query = n == null ? "" : n.toLowerCase(Locale.ROOT).trim();
            filteredRecipes.setPredicate(r ->
                    query.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(query));
        });

        refreshBuildingBlocks();
    }

    /** Reloads compositions and recipes; called by the shell after library changes. */
    public void refreshBuildingBlocks() {
        compositionItems.setAll(repository.getCompositions());
        recipeItems.setAll(catalog.getRecipes());
    }

    private String compositionPillText(Composition comp) {
        double cost = 0;
        for (Long number : comp.recipeNumbers()) {
            Recipe recipe = catalog.findRecipe(number).orElse(null);
            if (recipe != null) {
                cost += recipe.costFor(recipe.defaultGrams());
            }
        }
        return comp.name() + "   " + formatPrice(cost);
    }

    private String recipePillText(Recipe recipe) {
        return recipe.name() + "   " + formatPrice(recipe.costFor(recipe.defaultGrams()));
    }

    /** List cell rendered as a draggable pill carrying a dragboard token. */
    private static class PillCell<T> extends ListCell<T> {
        private final java.util.function.Function<T, String> textOf;
        private final java.util.function.Function<T, String> tokenOf;

        PillCell(java.util.function.Function<T, String> textOf,
                 java.util.function.Function<T, String> tokenOf) {
            this.textOf = textOf;
            this.tokenOf = tokenOf;
            // Adopt the ListView's width (long names ellipsize) instead of
            // forcing a horizontal scrollbar.
            setPrefWidth(0);
            setOnDragDetected(e -> {
                T item = getItem();
                if (item == null) {
                    return;
                }
                Dragboard db = startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString(tokenOf.apply(item));
                db.setContent(content);
                e.consume();
            });
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("pill-cell");
            if (empty || item == null) {
                setText(null);
                return;
            }
            getStyleClass().add("pill-cell");
            setText(textOf.apply(item));
        }
    }

    // ------------------------------------------------------------------
    // Week grid
    // ------------------------------------------------------------------

    private void rebuildAll() {
        similarityScores = analytics.similarityScores(weekMonday);
        updateWeekLabel();
        rebuildGrid();
        refreshIssues();
        refreshRepetitionReport();
    }

    private void rebuildGrid() {
        gridPlan.getChildren().clear();
        gridPlan.getColumnConstraints().clear();
        gridPlan.getRowConstraints().clear();

        List<Slot> slots = catalog.getSlots();

        ColumnConstraints slotCol = new ColumnConstraints();
        slotCol.setPercentWidth(13);
        gridPlan.getColumnConstraints().add(slotCol);
        for (int d = 0; d < WEEKDAY_LABELS.length; d++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setPercentWidth(87.0 / WEEKDAY_LABELS.length);
            gridPlan.getColumnConstraints().add(dayCol);
        }

        RowConstraints headerRow = new RowConstraints();
        headerRow.setMinHeight(30);
        headerRow.setPrefHeight(30);
        headerRow.setVgrow(Priority.NEVER);
        gridPlan.getRowConstraints().add(headerRow);
        for (int s = 0; s < slots.size(); s++) {
            RowConstraints row = new RowConstraints();
            row.setVgrow(Priority.ALWAYS);
            row.setFillHeight(true);
            row.setMinHeight(90);
            gridPlan.getRowConstraints().add(row);
        }

        Label corner = new Label("");
        corner.getStyleClass().add("grid-header");
        corner.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        gridPlan.add(corner, 0, 0);
        for (int d = 0; d < WEEKDAY_LABELS.length; d++) {
            Label header = new Label(WEEKDAY_LABELS[d] + "  " + weekMonday.plusDays(d).format(DAY_FORMAT));
            header.getStyleClass().add("grid-header");
            header.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            header.setAlignment(Pos.CENTER);
            gridPlan.add(header, d + 1, 0);
        }

        for (int s = 0; s < slots.size(); s++) {
            Slot slot = slots.get(s);
            Label slotLabel = new Label(slot.name());
            slotLabel.getStyleClass().add("grid-slot-label");
            slotLabel.setWrapText(true);
            slotLabel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            slotLabel.setAlignment(Pos.CENTER);
            gridPlan.add(slotLabel, 0, s + 1);

            for (int d = 0; d < WEEKDAY_LABELS.length; d++) {
                LocalDate date = weekMonday.plusDays(d);
                gridPlan.add(buildCell(date, slot), d + 1, s + 1);
            }
        }
    }

    private Node buildCell(LocalDate date, Slot slot) {
        VBox cell = new VBox(4);
        cell.setPadding(new Insets(6));
        cell.getStyleClass().add("plan-cell");
        cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        if (!slot.availableOn(date.getDayOfWeek())) {
            cell.getStyleClass().add("plan-cell-unavailable");
            return cell;
        }

        CellKey key = new CellKey(date, slot.id());
        wireDragTarget(cell, key);

        EditingCell editing = editingCells.get(key);
        if (editing != null) {
            cell.getStyleClass().add("plan-cell-editing");
            Node editor = buildEditor(key, editing);
            VBox.setVgrow(editor, Priority.ALWAYS);
            cell.getChildren().add(editor);
            return cell;
        }

        Optional<PlannedMenu> menu = repository.getMenuAt(key);
        if (menu.isPresent()) {
            cell.getStyleClass().add("plan-cell-filled");
            Node locked = buildLockedView(key, menu.get());
            VBox.setVgrow(locked, Priority.ALWAYS);
            cell.getChildren().add(locked);
            cell.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    startEditing(key, menu.get());
                }
            });
        } else {
            cell.getStyleClass().add("plan-cell-empty");
            Label dash = new Label("—");
            dash.getStyleClass().add("plan-cell-placeholder");
            Label dateLabel = new Label(date.format(DAY_FORMAT));
            dateLabel.getStyleClass().add("plan-cell-date");
            cell.setAlignment(Pos.CENTER);
            cell.getChildren().addAll(dash, dateLabel);
        }
        return cell;
    }

    private void wireDragTarget(VBox cell, CellKey key) {
        cell.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasString() && (db.getString().startsWith(TOKEN_COMPOSITION + "|")
                    || db.getString().startsWith(TOKEN_RECIPE + "|"))) {
                e.acceptTransferModes(TransferMode.COPY);
                if (!cell.getStyleClass().contains("plan-cell-drop")) {
                    cell.getStyleClass().add("plan-cell-drop");
                }
            }
            e.consume();
        });
        cell.setOnDragExited(e -> cell.getStyleClass().remove("plan-cell-drop"));
        cell.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean ok = db.hasString() && handleDrop(key, db.getString());
            e.setDropCompleted(ok);
            e.consume();
            if (ok) {
                rebuildGrid();
            }
        });
    }

    /**
     * Drop semantics mirroring the original: a composition seeds the cell's
     * editor with its recipes as grams-editable parts; a recipe adds one part
     * to the open editor (or starts a fresh single-part editor).
     */
    private boolean handleDrop(CellKey key, String token) {
        int sep = token.indexOf('|');
        if (sep < 0) {
            return false;
        }
        String kind = token.substring(0, sep);
        String value = token.substring(sep + 1);

        if (TOKEN_COMPOSITION.equals(kind)) {
            Composition comp = repository.findComposition(value).orElse(null);
            if (comp == null) {
                return false;
            }
            EditingCell editing = new EditingCell();
            editing.name = comp.name();
            for (Long number : comp.recipeNumbers()) {
                catalog.findRecipe(number).ifPresent(r ->
                        editing.parts.add(new MenuPart(r.number(), r.name(), r.defaultGrams())));
            }
            editingCells.put(key, editing);
            return true;
        }
        if (TOKEN_RECIPE.equals(kind)) {
            Recipe recipe = catalog.findRecipe(Long.parseLong(value)).orElse(null);
            if (recipe == null) {
                return false;
            }
            EditingCell editing = editingCells.computeIfAbsent(key, k -> new EditingCell());
            editing.parts.add(new MenuPart(recipe.number(), recipe.name(), recipe.defaultGrams()));
            return true;
        }
        return false;
    }

    private void startEditing(CellKey key, PlannedMenu menu) {
        EditingCell editing = new EditingCell();
        editing.name = menu.getName();
        for (MenuPart part : menu.getParts()) {
            editing.parts.add(part.copy());
        }
        editingCells.put(key, editing);
        rebuildGrid();
    }

    // ------------------------------------------------------------------
    // Cell editor
    // ------------------------------------------------------------------

    private Node buildEditor(CellKey key, EditingCell editing) {
        VBox box = new VBox(5);
        box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        TextField nameField = new TextField(editing.name);
        nameField.setPromptText("Menu name…");
        nameField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        nameField.textProperty().addListener((obs, o, n) -> editing.name = n);

        Button loadButton = new Button("▼");
        loadButton.getStyleClass().add("btn-cell-small");
        loadButton.setTooltip(new Tooltip("Load the saved menu with this name"));
        loadButton.setOnAction(e -> repository.findMenuByName(editing.name).ifPresent(menu -> {
            editing.parts.clear();
            for (MenuPart part : menu.getParts()) {
                editing.parts.add(part.copy());
            }
            rebuildGrid();
        }));
        attachMenuNameAutocomplete(nameField, loadButton, editing);
        updateLoadButton(loadButton, editing.name);
        HBox nameRow = new HBox(4, nameField, loadButton);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        VBox partsBox = new VBox(3);
        Label totals = new Label();
        totals.getStyleClass().add("cell-totals");
        totals.setMaxWidth(Double.MAX_VALUE);
        totals.setAlignment(Pos.CENTER);
        Runnable refreshTotals = () -> totals.setText(liveTotalsText(editing));
        for (MenuPart part : editing.parts) {
            partsBox.getChildren().add(buildPartRow(key, editing, part, refreshTotals));
        }
        refreshTotals.run();
        if (editing.parts.isEmpty()) {
            Label hint = new Label("Drop recipes here…");
            hint.getStyleClass().add("plan-cell-placeholder");
            partsBox.getChildren().add(hint);
        }

        ScrollPane scroll = new ScrollPane(partsBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("cell-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button discard = new Button("✕ Discard");
        discard.getStyleClass().add("btn-cell-discard");
        discard.setMaxWidth(Double.MAX_VALUE);
        discard.setOnAction(e -> {
            editingCells.remove(key);
            rebuildGrid();
        });
        Button save = new Button("✓ Save");
        save.getStyleClass().add("btn-cell-save");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(e -> saveCell(key, editing));
        HBox.setHgrow(discard, Priority.ALWAYS);
        HBox.setHgrow(save, Priority.ALWAYS);
        HBox actions = new HBox(6, discard, save);

        box.getChildren().addAll(nameRow, scroll, totals, actions);
        return box;
    }

    private Node buildPartRow(CellKey key, EditingCell editing, MenuPart part, Runnable refreshTotals) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("cell-part-row");

        Label name = new Label(part.getDisplayName());
        name.getStyleClass().add("cell-part-name");
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        TextField gramsField = new TextField(part.getGrams() == null ? "" : String.valueOf(part.getGrams()));
        gramsField.setPromptText("g");
        gramsField.getStyleClass().add("cell-grams-field");
        gramsField.setPrefWidth(52);

        Label price = new Label();
        price.getStyleClass().add("cell-part-price");
        Runnable updatePrice = () -> {
            Double cost = repository.currentPriceFor(part);
            price.setText(cost == null ? "–" : formatPrice(cost));
        };
        gramsField.textProperty().addListener((obs, o, n) -> {
            part.setGrams(parseGrams(n));
            updatePrice.run();
            refreshTotals.run();
        });
        updatePrice.run();

        Button remove = new Button("×");
        remove.getStyleClass().add("btn-cell-small");
        remove.setTooltip(new Tooltip("Remove component"));
        remove.setOnAction(e -> {
            editing.parts.remove(part);
            rebuildGrid();
        });

        row.getChildren().addAll(name, gramsField, price, remove);
        return row;
    }

    private void saveCell(CellKey key, EditingCell editing) {
        if (editing.name == null || editing.name.isBlank()) {
            warn("Please enter a menu name.");
            return;
        }
        if (editing.parts.isEmpty()) {
            warn("Please add at least one component.");
            return;
        }
        for (MenuPart part : editing.parts) {
            if (part.getGrams() == null || part.getGrams() <= 0) {
                warn("Please enter a portion size for every component (" + part.getDisplayName() + ").");
                return;
            }
        }
        PlannedMenu menu = repository.saveMenu(editing.name, editing.parts);
        repository.assignMenu(key, menu.getId());
        editingCells.remove(key);
        rebuildAll();
    }

    /**
     * Autocomplete under the menu-name field: saved menu names containing the
     * typed text; picking one fills the field. The ▼ button shows only when
     * the text exactly matches a saved menu.
     */
    private void attachMenuNameAutocomplete(TextField field, Button loadButton, EditingCell editing) {
        ContextMenu suggestions = new ContextMenu();
        field.textProperty().addListener((obs, o, n) -> {
            updateLoadButton(loadButton, n);
            suggestions.getItems().clear();
            String query = n == null ? "" : n.trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                suggestions.hide();
                return;
            }
            int count = 0;
            for (PlannedMenu menu : repository.getMenus()) {
                String name = menu.getName();
                if (name == null || !name.toLowerCase(Locale.ROOT).contains(query)
                        || name.equalsIgnoreCase(n)) {
                    continue;
                }
                MenuItem item = new MenuItem(name);
                item.setOnAction(e -> {
                    field.setText(name);
                    suggestions.hide();
                });
                suggestions.getItems().add(item);
                if (++count >= 8) {
                    break;
                }
            }
            if (suggestions.getItems().isEmpty()) {
                suggestions.hide();
            } else if (field.isFocused() && !suggestions.isShowing()) {
                suggestions.show(field, Side.BOTTOM, 0, 0);
            }
        });
    }

    private void updateLoadButton(Button loadButton, String name) {
        boolean match = repository.findMenuByName(name).isPresent();
        loadButton.setVisible(match);
        loadButton.setManaged(match);
    }

    // ------------------------------------------------------------------
    // Locked cell view
    // ------------------------------------------------------------------

    private Node buildLockedView(CellKey key, PlannedMenu menu) {
        VBox box = new VBox(4);
        box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        box.setAlignment(Pos.TOP_CENTER);

        Button quickDelete = new Button("✕");
        quickDelete.getStyleClass().add("btn-cell-small");
        quickDelete.setMinWidth(Region.USE_PREF_SIZE);
        quickDelete.setTooltip(new Tooltip("Remove from this day"));
        quickDelete.setOnAction(e -> {
            repository.clearCell(key);
            rebuildAll();
        });
        Label asOf = new Label(menu.getPriceAsOf() == null
                ? "" : menu.getPriceAsOf().format(DAY_FORMAT));
        asOf.getStyleClass().add("plan-cell-date");
        asOf.setMinWidth(Region.USE_PREF_SIZE);
        asOf.setTooltip(new Tooltip("Date of the cost snapshot"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(4, quickDelete, asOf, spacer);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Double similarity = similarityScores.get(key);
        if (similarity != null && similarity > 0) {
            Label badge = new Label("~" + Math.round(similarity * 100) + "%");
            badge.getStyleClass().add("sim-badge");
            badge.setMinWidth(Region.USE_PREF_SIZE);
            if (similarity >= AnalyticsService.SIMILARITY_WARN) {
                badge.getStyleClass().add("sim-badge-warn");
            }
            badge.setTooltip(new Tooltip("Similarity: occurrences of this menu in the 4-week window around this day / "
                    + AnalyticsService.SIMILARITY_WINDOW_DAYS + " delivery days"));
            topRow.getChildren().add(badge);
        }
        box.getChildren().add(topRow);

        Label name = new Label(menu.getName());
        name.getStyleClass().add("cell-menu-name");
        name.setWrapText(true);
        name.setAlignment(Pos.CENTER);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(name, Priority.ALWAYS);
        box.getChildren().add(name);

        Set<String> allergens = new LinkedHashSet<>();
        for (MenuPart part : menu.getParts()) {
            catalog.findRecipe(part.getRecipeNumber()).ifPresent(r -> allergens.addAll(r.allergens()));
        }
        if (!allergens.isEmpty()) {
            Label allergenLabel = new Label("Allergens: " + String.join(",", allergens));
            allergenLabel.getStyleClass().add("plan-cell-date");
            allergenLabel.setMaxWidth(Double.MAX_VALUE);
            allergenLabel.setAlignment(Pos.CENTER);
            box.getChildren().add(allergenLabel);
        }

        Label totals = new Label(snapshotTotalsText(menu));
        totals.getStyleClass().add("cell-totals");
        totals.setMaxWidth(Double.MAX_VALUE);
        totals.setAlignment(Pos.CENTER);
        totals.setTooltip(new Tooltip(partBreakdown(menu)));
        box.getChildren().add(totals);

        Button edit = new Button("✎ Edit");
        edit.getStyleClass().add("btn-cell-edit");
        edit.setMaxWidth(Double.MAX_VALUE);
        edit.setOnAction(e -> startEditing(key, menu));
        box.getChildren().add(edit);
        return box;
    }

    private String liveTotalsText(EditingCell editing) {
        int grams = 0;
        double cost = 0;
        boolean anyPrice = false;
        for (MenuPart part : editing.parts) {
            if (part.getGrams() != null) {
                grams += part.getGrams();
            }
            Double price = repository.currentPriceFor(part);
            if (price != null) {
                cost += price;
                anyPrice = true;
            }
        }
        return "Σ " + grams + " g  ·  " + (anyPrice ? formatPrice(cost) : "–");
    }

    private String snapshotTotalsText(PlannedMenu menu) {
        int grams = 0;
        double cost = 0;
        boolean anyPrice = false;
        for (MenuPart part : menu.getParts()) {
            if (part.getGrams() != null) {
                grams += part.getGrams();
            }
            if (part.getPriceSnapshot() != null) {
                cost += part.getPriceSnapshot();
                anyPrice = true;
            }
        }
        return "Σ " + grams + " g  ·  " + (anyPrice ? formatPrice(cost) : "–");
    }

    private String partBreakdown(PlannedMenu menu) {
        StringBuilder sb = new StringBuilder();
        for (MenuPart part : menu.getParts()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(part.getDisplayName()).append(": ")
                    .append(part.getGrams() == null ? "–" : part.getGrams() + " g")
                    .append("  ·  ")
                    .append(part.getPriceSnapshot() == null ? "–" : formatPrice(part.getPriceSnapshot()));
        }
        return sb.isEmpty() ? "—" : sb.toString();
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    private void refreshIssues() {
        vboxIssues.getChildren().clear();
        List<Node> issues = new ArrayList<>();

        for (Slot slot : catalog.getSlots()) {
            for (int d = 0; d < WEEKDAY_LABELS.length; d++) {
                LocalDate date = weekMonday.plusDays(d);
                if (!slot.availableOn(date.getDayOfWeek())) {
                    continue;
                }
                CellKey key = new CellKey(date, slot.id());
                if (repository.getMenuAt(key).isEmpty() && !editingCells.containsKey(key)) {
                    issues.add(issueRow("?", "Empty slot: " + slot.name() + " · "
                            + WEEKDAY_LABELS[d] + " " + date.format(DAY_FORMAT)));
                }
            }
        }
        similarityScores.forEach((key, score) -> {
            if (score != null && score >= AnalyticsService.SIMILARITY_WARN) {
                repository.getMenuAt(key).ifPresent(menu ->
                        issues.add(issueRow("!", "High similarity ("
                                + Math.round(score * 100) + "%): " + menu.getName())));
            }
        });

        if (issues.isEmpty()) {
            vboxIssues.getChildren().add(issueRow("✓", "No findings for this week."));
        } else {
            vboxIssues.getChildren().addAll(issues);
        }
    }

    private Node issueRow(String icon, String text) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("issue-icon");
        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("perf-detail");
        textLabel.setWrapText(true);
        textLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textLabel, Priority.ALWAYS);
        HBox row = new HBox(6, iconLabel, textLabel);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private void initializeRepetitionReport() {
        applyIsoDateFormat(datePickerRepFrom);
        applyIsoDateFormat(datePickerRepTo);
        datePickerRepFrom.setValue(weekMonday.minusWeeks(2));
        datePickerRepTo.setValue(weekMonday.plusWeeks(2).minusDays(1));
        datePickerRepFrom.valueProperty().addListener((obs, o, n) -> refreshRepetitionReport());
        datePickerRepTo.valueProperty().addListener((obs, o, n) -> refreshRepetitionReport());
    }

    private void refreshRepetitionReport() {
        if (vboxRepetition == null) {
            return;
        }
        vboxRepetition.getChildren().clear();
        LocalDate from = datePickerRepFrom.getValue();
        LocalDate to = datePickerRepTo.getValue();
        if (from == null || to == null || to.isBefore(from)) {
            vboxRepetition.getChildren().add(issueRow("?", "Invalid date range."));
            return;
        }
        List<AnalyticsService.RepetitionRow> rows = analytics.repetitionReport(from, to);
        if (rows.isEmpty()) {
            vboxRepetition.getChildren().add(issueRow("?", "No planned menus in range."));
            return;
        }
        for (AnalyticsService.RepetitionRow row : rows) {
            Label name = new Label(row.menu().getName());
            name.getStyleClass().add("perf-detail");
            name.setWrapText(true);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            Label count = new Label("× " + row.count() + "  ·  " + Math.round(row.share() * 100) + "%");
            count.getStyleClass().add("count-badge");
            count.setMinWidth(Region.USE_PREF_SIZE);
            count.setTooltip(new Tooltip(row.count() + " occurrences / "
                    + row.deliveryDays() + " delivery days in range"));
            HBox line = new HBox(6, name, count);
            line.setAlignment(Pos.CENTER_LEFT);
            vboxRepetition.getChildren().add(line);
        }
    }

    // ------------------------------------------------------------------
    // Excel export
    // ------------------------------------------------------------------

    private void exportExcel() {
        DatePicker from = new DatePicker(weekMonday);
        DatePicker to = new DatePicker(weekMonday.plusWeeks(3));
        applyIsoDateFormat(from);
        applyIsoDateFormat(to);

        GridPane content = new GridPane();
        content.setHgap(8);
        content.setVgap(8);
        content.setPadding(new Insets(8));
        content.add(new Label("From (any day of first week):"), 0, 0);
        content.add(from, 1, 0);
        content.add(new Label("To (any day of last week):"), 0, 1);
        content.add(to, 1, 1);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Export meal plan");
        dialog.setHeaderText("Choose a range — one sheet per week.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK
                || from.getValue() == null || to.getValue() == null) {
            return;
        }

        LocalDate fromMonday = mondayOf(from.getValue());
        LocalDate toMonday = mondayOf(to.getValue());
        if (toMonday.isBefore(fromMonday)) {
            warn("Invalid range: 'To' lies before 'From'.");
            return;
        }

        WeekFields wf = WeekFields.ISO;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export meal plan");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName("meal-plan_CW" + fromMonday.get(wf.weekOfWeekBasedYear())
                + "-CW" + toMonday.get(wf.weekOfWeekBasedYear()) + ".xlsx");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            exporter.export(file, fromMonday, toMonday);
            Alert done = new Alert(Alert.AlertType.INFORMATION,
                    "Meal plan exported:\n" + file.getAbsolutePath(), ButtonType.OK);
            done.setHeaderText(null);
            done.setTitle("Export");
            done.showAndWait();
        } catch (Exception ex) {
            warn("Export failed: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private static Integer parseGrams(String text) {
        try {
            int grams = Integer.parseInt(text.trim());
            return grams > 0 ? grams : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatPrice(double euros) {
        return String.format(Locale.US, "%.2f €", euros);
    }

    private static void applyIsoDateFormat(DatePicker picker) {
        DateTimeFormatter format = DateTimeFormatter.ISO_LOCAL_DATE;
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : format.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                try {
                    return (text == null || text.isBlank()) ? null : LocalDate.parse(text, format);
                } catch (Exception e) {
                    return null;
                }
            }
        });
    }

    private void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Menu Planner");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
