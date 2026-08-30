package com.chordata.menuplanner.controller;

import com.chordata.menuplanner.model.Composition;
import com.chordata.menuplanner.model.Recipe;
import com.chordata.menuplanner.model.Tag;
import com.chordata.menuplanner.service.CatalogService;
import com.chordata.menuplanner.service.PlanRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.geometry.Side;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Library tab: browse the recipe catalog with search and the AND/OR tag
 * filter, assign tags to recipes, and combine recipes into compositions with
 * duplicate detection via the derived composition id.
 */
public class LibraryController {

    // Recipe list + tag filter
    @FXML private TextField textFieldRecipeSearch;
    @FXML private TextField textFieldTagFilter;
    @FXML private Button buttonTagFilterMode;
    @FXML private Button buttonClearTagFilter;
    @FXML private FlowPane flowPaneSelectedTags;
    @FXML private ListView<Recipe> listViewRecipes;

    // Tag assignment panel
    @FXML private Label labelSelectedRecipe;
    @FXML private VBox vboxTagChecks;

    // Composition builder
    @FXML private Button buttonAddToComposition;
    @FXML private Button buttonRemoveFromComposition;
    @FXML private Button buttonClearComposition;
    @FXML private ListView<Recipe> listViewSelectedRecipes;
    @FXML private Label labelCompositionId;
    @FXML private Label labelCompositionStatus;
    @FXML private TextField textFieldCompositionName;
    @FXML private Button buttonSaveComposition;

    // Existing compositions
    @FXML private TextField textFieldCompositionSearch;
    @FXML private ListView<Composition> listViewCompositions;
    @FXML private Button buttonDeleteComposition;

    private CatalogService catalog;
    private PlanRepository repository;
    private Runnable onLibraryChanged;

    private final ObservableList<Recipe> allRecipes = FXCollections.observableArrayList();
    private FilteredList<Recipe> filteredRecipes;
    private final ObservableList<Recipe> selectedRecipes = FXCollections.observableArrayList();
    private final ObservableList<Composition> compositionItems = FXCollections.observableArrayList();
    private FilteredList<Composition> filteredCompositions;

    private final Set<String> activeFilterTags = new LinkedHashSet<>();
    private boolean tagFilterModeAnd = false;

    public void postInitialize(CatalogService catalog, PlanRepository repository, Runnable onLibraryChanged) {
        this.catalog = catalog;
        this.repository = repository;
        this.onLibraryChanged = onLibraryChanged;

        initializeRecipeList();
        initializeTagFilter();
        initializeTagAssignmentPanel();
        initializeCompositionBuilder();
        initializeCompositionList();
    }

    // ------------------------------------------------------------------
    // Recipe list
    // ------------------------------------------------------------------

    private void initializeRecipeList() {
        allRecipes.setAll(catalog.getRecipes());
        filteredRecipes = new FilteredList<>(allRecipes, r -> true);
        listViewRecipes.setItems(filteredRecipes);
        listViewRecipes.setCellFactory(lv -> new RecipeCell());

        textFieldRecipeSearch.textProperty().addListener((obs, o, n) -> updateRecipeFilter());

        listViewRecipes.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> updateTagAssignmentPanel(n));

        listViewRecipes.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                addSelectedRecipeToComposition();
            }
        });
    }

    private void updateRecipeFilter() {
        String search = textFieldRecipeSearch.getText() == null
                ? "" : textFieldRecipeSearch.getText().toLowerCase(Locale.ROOT).trim();
        filteredRecipes.setPredicate(recipe -> {
            boolean matchesSearch = search.isEmpty()
                    || recipe.name().toLowerCase(Locale.ROOT).contains(search);
            if (!matchesSearch) {
                return false;
            }
            if (activeFilterTags.isEmpty()) {
                return true;
            }
            Set<String> recipeTags = repository.getTagsForRecipe(recipe.number());
            return tagFilterModeAnd
                    ? recipeTags.containsAll(activeFilterTags)
                    : activeFilterTags.stream().anyMatch(recipeTags::contains);
        });
    }

    /** Recipe row: name, colored tag chips, portion + cost + allergens on the right. */
    private class RecipeCell extends ListCell<Recipe> {
        RecipeCell() {
            // Adopt the ListView's width (the detail label ellipsizes) instead
            // of forcing a horizontal scrollbar.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(Recipe recipe, boolean empty) {
            super.updateItem(recipe, empty);
            if (empty || recipe == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(recipe.name());
            name.getStyleClass().add("perf-name");
            row.getChildren().add(name);

            for (String tagName : repository.getTagsForRecipe(recipe.number()).stream().sorted().toList()) {
                row.getChildren().add(buildTagChip(tagName, null));
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().add(spacer);

            String allergens = recipe.allergens().isEmpty()
                    ? "" : "  ·  allergens " + String.join(",", recipe.allergens());
            Label detail = new Label(String.format(Locale.US, "%d g  ·  %.2f €/100g%s",
                    recipe.defaultGrams(), recipe.costPer100g(), allergens));
            detail.getStyleClass().add("perf-detail");
            row.getChildren().add(detail);

            setGraphic(row);
            setText(null);
        }
    }

    /** A colored tag chip; when {@code onRemove} is given it renders a ✕ handle. */
    private HBox buildTagChip(String tagName, Runnable onRemove) {
        String color = catalog.findTag(tagName).map(Tag::color).orElse("#808080");
        HBox chip = new HBox(4);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("tag-chip");
        chip.setStyle("-fx-background-color: " + color + "26; -fx-border-color: " + color + ";");
        chip.getChildren().add(new Label(tagName));
        if (onRemove != null) {
            Label remove = new Label("✕");
            remove.getStyleClass().add("tag-chip-remove");
            remove.setOnMouseClicked(e -> onRemove.run());
            chip.getChildren().add(remove);
        }
        return chip;
    }

    // ------------------------------------------------------------------
    // Tag filter (autocomplete + AND/OR + chips)
    // ------------------------------------------------------------------

    private void initializeTagFilter() {
        ContextMenu suggestions = new ContextMenu();
        textFieldTagFilter.textProperty().addListener((obs, o, n) -> {
            suggestions.getItems().clear();
            String query = n == null ? "" : n.toLowerCase(Locale.ROOT).trim();
            if (query.isEmpty()) {
                suggestions.hide();
                return;
            }
            List<String> matches = catalog.getTags().stream()
                    .map(Tag::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains(query))
                    .filter(name -> !activeFilterTags.contains(name))
                    .sorted()
                    .limit(8)
                    .toList();
            if (matches.isEmpty()) {
                suggestions.hide();
                return;
            }
            for (String match : matches) {
                MenuItem item = new MenuItem(match);
                item.setOnAction(e -> {
                    addFilterTag(match);
                    textFieldTagFilter.clear();
                    suggestions.hide();
                });
                suggestions.getItems().add(item);
            }
            if (!suggestions.isShowing() && textFieldTagFilter.isFocused()) {
                suggestions.show(textFieldTagFilter, Side.BOTTOM, 0, 0);
            }
        });
        textFieldTagFilter.setOnAction(e -> {
            if (!suggestions.getItems().isEmpty()) {
                suggestions.getItems().get(0).fire();
            }
        });

        buttonTagFilterMode.setOnAction(e -> {
            tagFilterModeAnd = !tagFilterModeAnd;
            buttonTagFilterMode.setText(tagFilterModeAnd ? "AND" : "OR");
            updateRecipeFilter();
        });
        buttonClearTagFilter.setOnAction(e -> {
            activeFilterTags.clear();
            renderFilterChips();
            updateRecipeFilter();
        });
    }

    private void addFilterTag(String tagName) {
        if (activeFilterTags.add(tagName)) {
            renderFilterChips();
            updateRecipeFilter();
        }
    }

    private void renderFilterChips() {
        flowPaneSelectedTags.getChildren().clear();
        for (String tagName : activeFilterTags) {
            flowPaneSelectedTags.getChildren().add(buildTagChip(tagName, () -> {
                activeFilterTags.remove(tagName);
                renderFilterChips();
                updateRecipeFilter();
            }));
        }
    }

    // ------------------------------------------------------------------
    // Tag assignment panel
    // ------------------------------------------------------------------

    private void initializeTagAssignmentPanel() {
        updateTagAssignmentPanel(null);
    }

    private void updateTagAssignmentPanel(Recipe recipe) {
        vboxTagChecks.getChildren().clear();
        if (recipe == null) {
            labelSelectedRecipe.setText("Select a recipe to edit its tags");
            return;
        }
        labelSelectedRecipe.setText(recipe.name());
        Set<String> assigned = repository.getTagsForRecipe(recipe.number());
        for (Tag tag : catalog.getTags()) {
            CheckBox check = new CheckBox(tag.name());
            check.setSelected(assigned.contains(tag.name()));
            check.setStyle("-fx-border-color: " + tag.color() + "; -fx-border-width: 0 0 0 3; -fx-padding: 2 0 2 6;");
            check.setOnAction(e -> {
                repository.setTagAssigned(recipe.number(), tag.name(), check.isSelected());
                listViewRecipes.refresh();
                updateRecipeFilter();
                onLibraryChanged.run();
            });
            vboxTagChecks.getChildren().add(check);
        }
    }

    // ------------------------------------------------------------------
    // Composition builder
    // ------------------------------------------------------------------

    private void initializeCompositionBuilder() {
        listViewSelectedRecipes.setItems(selectedRecipes);
        listViewSelectedRecipes.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Recipe recipe, boolean empty) {
                super.updateItem(recipe, empty);
                setText(empty || recipe == null ? null : recipe.name());
            }
        });
        listViewSelectedRecipes.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Recipe selected = listViewSelectedRecipes.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectedRecipes.remove(selected);
                    updateCompositionStatus();
                }
            }
        });

        buttonAddToComposition.setOnAction(e -> addSelectedRecipeToComposition());
        buttonRemoveFromComposition.setOnAction(e -> {
            Recipe selected = listViewSelectedRecipes.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectedRecipes.remove(selected);
                updateCompositionStatus();
            }
        });
        buttonClearComposition.setOnAction(e -> {
            selectedRecipes.clear();
            textFieldCompositionName.clear();
            updateCompositionStatus();
        });
        buttonSaveComposition.setOnAction(e -> saveComposition());
        updateCompositionStatus();
    }

    private void addSelectedRecipeToComposition() {
        Recipe selected = listViewRecipes.getSelectionModel().getSelectedItem();
        if (selected != null && !selectedRecipes.contains(selected)) {
            selectedRecipes.add(selected);
            updateCompositionStatus();
        }
    }

    private void updateCompositionStatus() {
        if (selectedRecipes.isEmpty()) {
            labelCompositionId.setText("–");
            labelCompositionStatus.setText("–");
            labelCompositionStatus.getStyleClass().removeAll("status-label-new", "status-label-exists");
            buttonSaveComposition.setDisable(true);
            return;
        }
        String id = Composition.idFor(selectedRecipes.stream().map(Recipe::number).toList());
        labelCompositionId.setText(id);

        boolean exists = repository.compositionExists(id);
        labelCompositionStatus.setText(exists ? "Already exists" : "New");
        labelCompositionStatus.getStyleClass().removeAll("status-label-new", "status-label-exists");
        labelCompositionStatus.getStyleClass().add(exists ? "status-label-exists" : "status-label-new");
        buttonSaveComposition.setDisable(exists);

        if (exists) {
            repository.findComposition(id).ifPresent(c -> {
                listViewCompositions.getSelectionModel().select(c);
                listViewCompositions.scrollTo(c);
            });
        }
    }

    private void saveComposition() {
        String name = textFieldCompositionName.getText();
        if (selectedRecipes.isEmpty()) {
            warn("Please select at least one recipe.");
            return;
        }
        if (name == null || name.isBlank()) {
            warn("Please enter a name for the composition.");
            return;
        }
        List<Long> numbers = new ArrayList<>(selectedRecipes.stream().map(Recipe::number).toList());
        repository.addComposition(new Composition(Composition.idFor(numbers), name.trim(), numbers));

        selectedRecipes.clear();
        textFieldCompositionName.clear();
        updateCompositionStatus();
        refreshCompositionList();
        onLibraryChanged.run();
    }

    // ------------------------------------------------------------------
    // Composition list
    // ------------------------------------------------------------------

    private void initializeCompositionList() {
        filteredCompositions = new FilteredList<>(compositionItems, c -> true);
        listViewCompositions.setItems(filteredCompositions);
        listViewCompositions.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Composition comp, boolean empty) {
                super.updateItem(comp, empty);
                if (empty || comp == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(comp.name());
                name.getStyleClass().add("perf-name");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label detail = new Label(comp.recipeNumbers().size() + " recipes  ·  id " + comp.id());
                detail.getStyleClass().add("perf-detail");
                row.getChildren().addAll(name, spacer, detail);
                setGraphic(row);
                setText(null);
            }
        });
        textFieldCompositionSearch.textProperty().addListener((obs, o, n) -> {
            String query = n == null ? "" : n.toLowerCase(Locale.ROOT).trim();
            filteredCompositions.setPredicate(c ->
                    query.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(query));
        });
        buttonDeleteComposition.setOnAction(e -> {
            Composition selected = listViewCompositions.getSelectionModel().getSelectedItem();
            if (selected != null) {
                repository.removeComposition(selected);
                refreshCompositionList();
                updateCompositionStatus();
                onLibraryChanged.run();
            }
        });
        refreshCompositionList();
    }

    private void refreshCompositionList() {
        compositionItems.setAll(repository.getCompositions());
    }

    private void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Menu Planner");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
