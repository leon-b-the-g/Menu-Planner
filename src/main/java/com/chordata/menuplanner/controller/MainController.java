package com.chordata.menuplanner.controller;

import com.chordata.menuplanner.service.AnalyticsService;
import com.chordata.menuplanner.service.CatalogService;
import com.chordata.menuplanner.service.ExcelExportService;
import com.chordata.menuplanner.service.PlanRepository;
import javafx.fxml.FXML;
import javafx.stage.Stage;

/**
 * Shell controller: hosts the Library and Planner tabs (loaded via
 * {@code fx:include}) and wires them together — a library change (new
 * composition, tag assignment) refreshes the planner's building blocks.
 */
public class MainController {

    @FXML private LibraryController libraryContentController;
    @FXML private PlannerController plannerContentController;

    public void postInitialize(Stage stage, CatalogService catalog, PlanRepository repository,
                               AnalyticsService analytics, ExcelExportService exporter) {
        plannerContentController.postInitialize(stage, catalog, repository, analytics, exporter);
        libraryContentController.postInitialize(catalog, repository,
                () -> plannerContentController.refreshBuildingBlocks());
    }
}
