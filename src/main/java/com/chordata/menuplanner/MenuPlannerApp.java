package com.chordata.menuplanner;

import com.chordata.menuplanner.controller.MainController;
import com.chordata.menuplanner.service.AnalyticsService;
import com.chordata.menuplanner.service.CatalogService;
import com.chordata.menuplanner.service.ExcelExportService;
import com.chordata.menuplanner.service.PlanRepository;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * JavaFX entry point: builds the service graph, loads the main view and
 * applies the stylesheet.
 */
public class MenuPlannerApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        CatalogService catalog = new CatalogService();
        PlanRepository repository = new PlanRepository(catalog, PlanRepository.defaultStoreFile());
        repository.loadOrSeed();
        AnalyticsService analytics = new AnalyticsService(repository);
        ExcelExportService exporter = new ExcelExportService(catalog, repository);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chordata/menuplanner/main.fxml"));
        Scene scene = new Scene(loader.load(), 1440, 900);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/com/chordata/menuplanner/styles.css")).toExternalForm());

        MainController controller = loader.getController();
        controller.postInitialize(stage, catalog, repository, analytics, exporter);

        stage.setTitle("Menu Planner");
        stage.setScene(scene);
        stage.setMinWidth(1180);
        stage.setMinHeight(720);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
